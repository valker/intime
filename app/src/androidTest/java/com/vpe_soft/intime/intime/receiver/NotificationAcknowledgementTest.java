package com.vpe_soft.intime.intime.receiver;

import static org.junit.Assert.*;

import android.app.Notification;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
public class NotificationAcknowledgementTest {
    private Context context;
    private AppDatabase database;
    private TaskDao dao;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        AppDatabase.setTestInstance(database);
        dao = database.taskDao();
    }

    @After
    public void tearDown() {
        database.close();
        AppDatabase.setTestInstance(null);
    }

    /**
     * Проверяет: На Android создаются две просроченные задачи с lastAck = 0 и уведомления с разными
     * PendingIntent. Отправляется действие второго уведомления с ожиданием завершения broadcast до 10
     * секунд. У первой lastAck должен остаться 0, у второй стать положительным, а nextAlarm перейти в
     * будущее. Проверяются настоящий PendingIntent и обработчик ACK, без нажатия в шторке уведомлений.
     */
    @Test
    public void secondNotificationAction_acknowledgesOnlySecondTask() throws Exception {
        long past = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1);
        long firstId = dao.insert(new TaskEntity("First", 1, 1, past, past, 0, 1));
        long secondId = dao.insert(new TaskEntity("Second", 1, 1, past, past, 0, 1));
        Notification first = notification(firstId);
        Notification second = notification(secondId);
        assertNotEquals(first.actions[0].actionIntent, second.actions[0].actionIntent);

        CountDownLatch completed = new CountDownLatch(1);
        second.actions[0].actionIntent.send(context, 0, null,
                (pendingIntent, intent, resultCode, resultData, resultExtras) -> completed.countDown(),
                new Handler(Looper.getMainLooper()));
        // Callback приходит после завершения broadcast/goAsync, включая перепланирование.
        assertTrue("ACK broadcast did not finish", completed.await(10, TimeUnit.SECONDS));
        assertEquals(0L, dao.getRawTaskById(firstId).lastAck.longValue());
        assertTrue(dao.getRawTaskById(secondId).lastAck > 0);
        assertTrue(dao.getRawTaskById(secondId).nextAlarm > System.currentTimeMillis());
    }

    private Notification notification(long id) {
        return AlarmReceiver.createNotification(context, "Task",
                new NotificationCompat.Builder(context, Constants.TASK_OVERDUE_CHANNEL_ID), id);
    }
}
