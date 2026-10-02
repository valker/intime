package com.vpe_soft.intime.intime.receiver;

import static org.junit.Assert.*;

import android.app.Notification;
import android.content.Context;
import android.database.SQLException;
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

    /**
     * Отправляет настоящий ACK PendingIntent для несуществующего положительного ID и дожидается
     * callback завершения broadcast до 10 секунд. База должна остаться пустой. Затем в той же
     * очереди receiver отправляет ACK для существующей задачи и проверяет lastAck и будущий срок.
     * Проверяется системная доставка и завершение goAsync без записи для первого ID; шторка
     * уведомлений, reboot и убийство процесса здесь не проверяются.
     */
    @Test
    public void missingTaskAck_finishesAndAllowsFollowingAck() throws Exception {
        sendAndWait(404);
        assertEquals(0, dao.getTaskCount());
        long past = System.currentTimeMillis() - 60000;
        long id = dao.insert(new TaskEntity("Following", 1, 1, past, past, 0, 1));
        sendAndWait(id);
        assertTrue(dao.getRawTaskById(id).lastAck > 0);
        assertTrue(dao.getRawTaskById(id).nextAlarm > System.currentTimeMillis());
    }

    /**
     * Закрывает исключительно внедрённую Room-базу в памяти, оставляя её доступной через test
     * singleton. Перед отправкой настоящего ACK PendingIntent подтверждает SQLException
     * при запросе к закрытой базе. Ошибка в receiver не должна задержать завершение broadcast:
     * ожидается callback до 10 секунд. После него
     * внедряется новая база в памяти и проверяется успешный ACK следующей задачи в той же очереди.
     * Production-база и пользовательские данные не открываются; системные сбои не имитируются.
     */
    @Test
    public void databaseFailureAck_finishesAndQueueRecovers() throws Exception {
        assertEquals(0, dao.getTaskCount());
        database.close();
        assertThrows(SQLException.class, () -> dao.getTaskCount());
        sendAndWait(404);
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        AppDatabase.setTestInstance(database);
        dao = database.taskDao();
        long past = System.currentTimeMillis() - 60000;
        long id = dao.insert(new TaskEntity("After failure", 1, 1, past, past, 0, 1));
        sendAndWait(id);
        assertTrue(dao.getRawTaskById(id).lastAck > 0);
        assertTrue(dao.getRawTaskById(id).nextAlarm > System.currentTimeMillis());
    }

    private void sendAndWait(long id) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        notification(id).actions[0].actionIntent.send(context, 0, null,
                (pendingIntent, intent, resultCode, resultData, resultExtras) -> completed.countDown(),
                new Handler(Looper.getMainLooper()));
        assertTrue("ACK broadcast did not finish", completed.await(10, TimeUnit.SECONDS));
    }
}
