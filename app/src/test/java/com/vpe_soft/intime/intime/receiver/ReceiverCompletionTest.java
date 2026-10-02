package com.vpe_soft.intime.intime.receiver;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.concurrent.AppExecutors;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBroadcastPendingResult;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ReceiverCompletionTest {
    private Context context;
    private AppDatabase database;
    private long dueId;
    private long future;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        AppDatabase.setTestInstance(database);
        long now = System.currentTimeMillis();
        future = now + TimeUnit.HOURS.toMillis(2);
        dueId = database.taskDao().insert(new TaskEntity("Due", 1, 1, now - 1000, now - 1000, 0, 1));
        database.taskDao().insert(new TaskEntity("Future", 1, 1, future, future - 60000, 0, 1));
        context.getSharedPreferences(Constants.SESSION_INFO_SP_NAME, Context.MODE_PRIVATE).edit()
                .putLong(Constants.LAST_USAGE_TIMESTAMP_KEY, now - 60000).commit();
    }

    @After public void tearDown() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AppExecutors.executeReceiver("completion test barrier", () -> { }, done::countDown);
        assertTrue(done.await(10, TimeUnit.SECONDS));
        database.close();
        AppDatabase.setTestInstance(null);
    }

    /**
     * Просроченная задача подтверждается через настоящий onReceive AckReceiver с установленным
     * PendingResult Robolectric. Ожидает finish до 10 секунд и лишь затем проверяет lastAck,
     * новый срок и наличие alarm. Доказывает завершение после работы, включая перепланирование;
     * доставку broadcast Android и нажатие действия в шторке этот локальный тест не проверяет.
     */
    @Test public void ackFinishesAfterUpdatingTaskAndScheduling() throws Exception {
        receive(new AckReceiver(), context);
        assertTrue(database.taskDao().getRawTaskById(dueId).lastAck > 0);
        assertTrue(database.taskDao().getRawTaskById(dueId).nextAlarm > System.currentTimeMillis());
        assertEquals(1, shadowOf(manager()).getScheduledAlarms().size());
    }

    /**
     * Передаёт просроченную задачу в onReceive AlarmReceiver. После завершения PendingResult
     * ожидает wasNotified = true, опубликованное уведомление и один alarm на вторую, будущую задачу.
     * Проверяется весь локальный асинхронный путь receiver; системный таймаут и реальная доставка
     * AlarmManager не моделируются. База и служба уведомлений принадлежат Robolectric.
     */
    @Test public void alarmFinishesAfterNotificationAndScheduling() throws Exception {
        receive(new AlarmReceiver(), context);
        assertTrue(database.taskDao().getRawTaskById(dueId).isWasNotified());
        assertNotNull(shadowOf(context.getSystemService(NotificationManager.class)).getNotification(AlarmUtil.NOTIFICATION_TAG, 1));
        assertFutureAlarm();
    }

    /**
     * Запускает onReceive BootReceiver с BOOT_COMPLETED, отметкой использования до просроченной
     * задачи и отдельной будущей задачей. Дожидается finish и проверяет boot-уведомление и будущий
     * alarm. Доказывает завершение асинхронного обработчика; настоящая перезагрузка проверяется
     * отдельным инструментальным smoke, а не этим синтетическим вызовом.
     */
    @Test public void bootFinishesAfterSummaryAndScheduling() throws Exception {
        receive(new BootReceiver(), context);
        assertNotNull(shadowOf(context.getSystemService(NotificationManager.class)).getNotification(AlarmUtil.NOTIFICATION_TAG, 1));
        assertFutureAlarm();
    }

    /**
     * Контекст ACK выбрасывает исключение при запросе ресурсов после чтения задачи. Проверяет
     * завершение PendingResult, неизменный lastAck после отката транзакции и журнал с исключением.
     * Затем нормальный ACK в той же общей очереди должен завершиться и обновить задачу.
     * Ошибка искусственная; отказ доступа к реальной базе Android здесь не воспроизводится.
     */
    @Test public void ackFailureFinishesRollsBackAndAllowsNextBroadcast() throws Exception {
        verifyFailure(AckReceiver.class);
        assertEquals(0L, database.taskDao().getRawTaskById(dueId).lastAck.longValue());
        receive(new AckReceiver(), context);
        assertTrue(database.taskDao().getRawTaskById(dueId).lastAck > 0);
    }

    /**
     * Контекст AlarmReceiver выбрасывает исключение при отправке внутреннего ordered broadcast.
     * После finish проверяет отсутствие wasNotified и запись ошибки. Следующий нормальный alarm
     * в той же очереди должен завершиться и отметить задачу. Проверяются откат и живучесть очереди,
     * без системной доставки уведомлений и без моделирования убийства процесса ОС.
     */
    @Test public void alarmFailureFinishesRollsBackAndAllowsNextBroadcast() throws Exception {
        verifyFailure(AlarmReceiver.class);
        assertFalse(database.taskDao().getRawTaskById(dueId).isWasNotified());
        receive(new AlarmReceiver(), context);
        assertTrue(database.taskDao().getRawTaskById(dueId).isWasNotified());
    }

    /**
     * Контекст BootReceiver выбрасывает исключение при чтении session preferences. Проверяет
     * finish и диагностический журнал, затем повторяет boot с исправным контекстом и ожидает
     * завершение с будущим alarm. Этот тест проверяет исключение внутри onReceive/goAsync;
     * сбой system_server, нехватка памяти AVD и системный broadcast-таймаут сюда не входят.
     */
    @Test public void bootFailureFinishesAndAllowsNextBroadcast() throws Exception {
        verifyFailure(BootReceiver.class);
        receive(new BootReceiver(), context);
        assertFutureAlarm();
    }

    private void verifyFailure(Class<? extends BroadcastReceiver> type) throws Exception {
        RuntimeException failure = new IllegalStateException("Injected receiver failure");
        Context broken = new ContextWrapper(context) {
            @Override public Context getApplicationContext() { return this; }
            @Override public Resources getResources() { throw failure; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) { throw failure; }
            @Override public void sendOrderedBroadcast(Intent intent, String permission) { throw failure; }
        };
        receive(type.getDeclaredConstructor().newInstance(), broken);
        assertTrue(ShadowLog.getLogsForTag("AppExecutors").stream().anyMatch(
                log -> log.msg.contains(type.getSimpleName()) && log.throwable == failure));
    }

    private void receive(BroadcastReceiver receiver, Context receiverContext) throws Exception {
        // Hidden-конструктор SDK 28: только в тесте, без изменения production receiver.
        BroadcastReceiver.PendingResult result = ReflectionHelpers.callConstructor(BroadcastReceiver.PendingResult.class,
                ClassParameter.from(int.class, 0), ClassParameter.from(String.class, null),
                ClassParameter.from(Bundle.class, null), ClassParameter.from(int.class, 0),
                ClassParameter.from(boolean.class, true), ClassParameter.from(boolean.class, false),
                ClassParameter.from(IBinder.class, null), ClassParameter.from(int.class, 0), ClassParameter.from(int.class, 0));
        ReflectionHelpers.callInstanceMethod(receiver, "setPendingResult", ClassParameter.from(BroadcastReceiver.PendingResult.class, result));
        receiver.onReceive(receiverContext, new Intent(Intent.ACTION_BOOT_COMPLETED).putExtra(Constants.EXTRA_TASK_ID, dueId));
        assertTrue(shadowOf(receiver).wentAsync());
        ShadowBroadcastPendingResult pending = Shadow.extract(result);
        assertSame(result, pending.getFuture().get(10, TimeUnit.SECONDS));
    }
    private AlarmManager manager() { return context.getSystemService(AlarmManager.class); }
    private void assertFutureAlarm() {
        assertEquals(1, shadowOf(manager()).getScheduledAlarms().size());
        assertEquals(future, shadowOf(manager()).getScheduledAlarms().get(0).triggerAtTime);
    }
}
