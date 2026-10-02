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

    /**
     * Проверяет: В тестовую Room-базу добавляется неуведомлённая задача со сроком на пять секунд
     * раньше now. Выборка для уведомлений должна вернуть ровно одну задачу с описанием Overdue.
     */
    @Test
    public void getTasksForNotification_returnsOverdueUnnotifiedTask() {
        long now = System.currentTimeMillis();
        taskDao.insert(sampleTask("Overdue", now - 5000));

        List<TaskEntity> result = taskDao.getTasksForNotification(now);

        assertEquals(1, result.size());
        assertEquals("Overdue", result.get(0).description);
    }

    /**
     * Проверяет: Просроченная на пять секунд задача предварительно помечается через markTaskNotified.
     * Запрос на момент now должен вернуть пустой список, исключая уже уведомлённую задачу.
     */
    @Test
    public void getTasksForNotification_excludesNotifiedTask() {
        long now = System.currentTimeMillis();
        long taskId = taskDao.insert(sampleTask("Notified overdue", now - 5000));
        taskDao.markTaskNotified(taskId);

        List<TaskEntity> result = taskDao.getTasksForNotification(now);

        assertEquals(0, result.size());
    }

    /**
     * Проверяет: Добавляется задача с nextAlarm на 100 секунд позже now. Запрос задач для уведомлений
     * на момент now должен быть пустым, поскольку срок ещё не наступил.
     */
    @Test
    public void getTasksForNotification_excludesFutureTask() {
        long now = System.currentTimeMillis();
        taskDao.insert(sampleTask("Future task", now + 100000));

        List<TaskEntity> result = taskDao.getTasksForNotification(now);

        assertEquals(0, result.size());
    }

    /**
     * Проверяет: До отметки просроченная задача присутствует в выборке в единственном экземпляре.
     * После markTaskNotified повторный запрос с тем же now должен вернуть ноль задач.
     */
    @Test
    public void markTaskNotified_excludesTaskFromSubsequentQueries() {
        long now = System.currentTimeMillis();
        long taskId = taskDao.insert(sampleTask("Overdue", now - 5000));

        assertEquals(1, taskDao.getTasksForNotification(now).size());

        taskDao.markTaskNotified(taskId);

        assertEquals(0, taskDao.getTasksForNotification(now).size());
    }

    /**
     * Проверяет: Уведомлённая просроченная задача подтверждается с новым сроком через 100 секунд.
     * Выборка остаётся пустой до и после подтверждения из-за будущего срока. Сам сброс wasNotified
     * напрямую не утверждается этим тестом.
     */
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

    /**
     * Проверяет: Уведомлённая просроченная задача сначала отсутствует в выборке. После подтверждения
     * со сроком на секунду раньше now она должна снова появиться единственной записью, подтверждая
     * сброс признака уведомления.
     */
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
