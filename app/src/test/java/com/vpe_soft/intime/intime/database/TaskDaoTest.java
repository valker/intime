package com.vpe_soft.intime.intime.database;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TaskDaoTest {

    private AppDatabase database;
    private TaskDao taskDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        taskDao = database.taskDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void getTasksForNotification_returnsOverdueUnnotifiedTask() {
        long now = System.currentTimeMillis();
        taskDao.insert(sampleTask("Overdue", now - 5000));

        List<TaskEntity> result = taskDao.getTasksForNotification(now);

        assertEquals(1, result.size());
        assertEquals("Overdue", result.get(0).description);
    }

    @Test
    public void getTasksForNotification_excludesNotifiedTask() {
        long now = System.currentTimeMillis();
        long taskId = taskDao.insert(sampleTask("Notified overdue", now - 5000));
        taskDao.markTaskNotified(taskId);

        List<TaskEntity> result = taskDao.getTasksForNotification(now);

        assertEquals(0, result.size());
    }

    @Test
    public void getTasksForNotification_excludesFutureTask() {
        long now = System.currentTimeMillis();
        taskDao.insert(sampleTask("Future task", now + 100000));

        List<TaskEntity> result = taskDao.getTasksForNotification(now);

        assertEquals(0, result.size());
    }

    @Test
    public void markTaskNotified_excludesTaskFromSubsequentQueries() {
        long now = System.currentTimeMillis();
        long taskId = taskDao.insert(sampleTask("Overdue", now - 5000));

        assertEquals(1, taskDao.getTasksForNotification(now).size());

        taskDao.markTaskNotified(taskId);

        assertEquals(0, taskDao.getTasksForNotification(now).size());
    }

    @Test
    public void acknowledgeTask_resetsWasNotified() {
        long now = System.currentTimeMillis();
        long taskId = taskDao.insert(sampleTask("Overdue", now - 5000));
        taskDao.markTaskNotified(taskId);

        assertEquals(0, taskDao.getTasksForNotification(now).size());

        // acknowledgeTask resets wasNotified=0 and sets next_alarm to a future value
        taskDao.acknowledgeTask(taskId, now, now + 100000, now + 99900);

        // Task is unnotified again, but next_alarm is in the future → still excluded
        assertEquals(0, taskDao.getTasksForNotification(now).size());
    }

    @Test
    public void acknowledgeTask_makesOverdueTaskReappearIfStillOverdue() {
        long now = System.currentTimeMillis();
        long taskId = taskDao.insert(sampleTask("Overdue", now - 5000));
        taskDao.markTaskNotified(taskId);

        assertEquals(0, taskDao.getTasksForNotification(now).size());

        // acknowledgeTask resets wasNotified=0 but next_alarm stays in the past
        taskDao.acknowledgeTask(taskId, now, now - 1000, now - 1100);

        // Task reappears — it is overdue and wasNotified was reset
        List<TaskEntity> result = taskDao.getTasksForNotification(now);
        assertEquals(1, result.size());
    }

    private static TaskEntity sampleTask(String description, long nextAlarm) {
        long now = System.currentTimeMillis();
        return new TaskEntity(description, 1, 1, nextAlarm, nextAlarm - 100, now, 1);
    }
}
