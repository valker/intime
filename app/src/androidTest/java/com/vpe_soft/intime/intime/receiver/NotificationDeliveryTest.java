package com.vpe_soft.intime.intime.receiver;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;

import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.notifications.NotificationHelper;
import com.vpe_soft.intime.intime.scheduling.SchedulingCoordinator;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Runs only on the dedicated project emulator: changes its test app permissions. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 33)
@FixMethodOrder(MethodSorters.NAME_ASCENDING) // Отключение канала выполняется последним.
public class NotificationDeliveryTest {
    @Rule public final TestName name = new TestName();
    private Context context;
    private AppDatabase database;
    private TaskDao dao;
    private NotificationManager manager;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        AppDatabase.setTestInstance(database);
        dao = database.taskDao();
        manager = context.getSystemService(NotificationManager.class);
        manager.cancelAll();
        if (!name.getMethodName().equals("deniedRuntimePermission_doesNotMarkTask")) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
            // Только тестовое приложение на выделенном AVD: разрешаем точную доставку.
            try (android.os.ParcelFileDescriptor command = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand("appops set " + context.getPackageName()
                            + " SCHEDULE_EXACT_ALARM allow");
                 java.io.FileInputStream output = new java.io.FileInputStream(command.getFileDescriptor())) {
                while (output.read() != -1) { /* Дожидаемся завершения команды shell. */ }
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
        }
        NotificationHelper.ensureTaskOverdueChannel(context);
    }

    @After
    public void tearDown() {
        manager.cancelAll();
        dao.deleteAll();
        SchedulingCoordinator.reschedule(context);
        database.close();
        AppDatabase.setTestInstance(null);
    }

    /**
     * Проверяет реальную доставку будильника Android и ACK опубликованного уведомления.
     * Задача назначается через три секунды и планируется через SchedulingCoordinator.
     * В течение 30 секунд ожидается уведомление NotificationManager с одним действием;
     * после отправки его PendingIntent проверяются lastAck и новый будущий срок задачи.
     * Требуется разрешение точных будильников; наличие уведомления не доказывает его прочтение.
     */
    @Test
    public void platformAlarm_postsNotificationAndAcknowledgesTask() throws Exception {
        assertTrue("Grant exact alarms to the dedicated test app before this test",
                context.getSystemService(AlarmManager.class).canScheduleExactAlarms());
        long future = System.currentTimeMillis() + 3000;
        long id = dao.insert(new TaskEntity("Device delivery", 1, 1, future, future, 0, 1));
        SchedulingCoordinator.reschedule(context);
        Notification notification = awaitNotification(30000);
        assertEquals("Device delivery", notification.extras.getCharSequence(Notification.EXTRA_TEXT));
        assertEquals(1, notification.actions.length);
        CountDownLatch completed = new CountDownLatch(1);
        notification.actions[0].actionIntent.send(context, 0, null,
                (pendingIntent, intent, resultCode, resultData, extras) -> completed.countDown(),
                new Handler(Looper.getMainLooper()));
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        assertTrue(dao.getRawTaskById(id).lastAck > 0);
        assertTrue(dao.getRawTaskById(id).nextAlarm > System.currentTimeMillis());
    }

    /**
     * Проверяет настоящий запрет POST_NOTIFICATIONS у тестового приложения.
     * Перед отдельным запуском хост отзывает разрешение через adb; при выданном
     * разрешении тест пропускается, чтобы не завершать instrumentation его отзывом.
     * Для просроченной задачи вызывается обработчик: ожидаются пустой системный список
     * уведомлений и wasNotified = false. Доставка AlarmManager здесь не проверяется.
     */
    @Test
    public void deniedRuntimePermission_doesNotMarkTask() {
        Assume.assumeTrue("Run separately after adb pm revoke POST_NOTIFICATIONS",
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_DENIED);
        long past = System.currentTimeMillis() - 1000;
        long id = dao.insert(new TaskEntity("Blocked device", 1, 1, past, past, 0, 1));
        AlarmReceiver.handleAlarm(context, new android.content.Intent()
                .putExtra(com.vpe_soft.intime.intime.Constants.EXTRA_TASK_ID, id));
        assertEquals(0, manager.getActiveNotifications().length);
        assertFalse(dao.getRawTaskById(id).isWasNotified());
    }

    /**
     * Проверяет опубликованный Android спокойный повтор: уже уведомлённая просроченная
     * задача и отметка отправки 16 минут назад передаются настоящему worker через
     * TestWorkerBuilder. В системном уведомлении ожидаются исходное описание, отсутствие
     * действий ACK, звука и вибрации. Периодический запуск WorkManager и слышимость
     * на физическом устройстве не проверяются; doWork вызывается явно.
     */
    @Test
    public void quietWorkerRepeat_isSilentWithoutAck() {
        long past = System.currentTimeMillis() - 1000;
        long id = dao.insert(new TaskEntity("Quiet device", 1, 1, past, past, 0, 1));
        dao.markTaskNotified(id);
        assertTrue(context.getSharedPreferences("notification_reminder_state", Context.MODE_PRIVATE)
                .edit().putLong("last_successful_post", System.currentTimeMillis() - 960000).commit());
        androidx.work.testing.TestWorkerBuilder.from(context,
                com.vpe_soft.intime.intime.workers.TaskNotificationWorker.class, Runnable::run)
                .build().doWork();
        Notification notification = awaitNotification(10000);
        assertEquals("Quiet device", notification.extras.getCharSequence(Notification.EXTRA_TEXT));
        assertTrue(notification.actions == null || notification.actions.length == 0);
        assertNull(notification.sound);
        assertNull(notification.vibrate);
        assertEquals(0, notification.defaults & (Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE));
    }

    /**
     * Проверяет отключённый настоящий Android-канал уведомлений тестового приложения.
     * После создания канала его importance понижается до NONE и проверяется в системе.
     * Для просроченной задачи вызывается receiver: уведомления быть не должно,
     * wasNotified должен остаться false. Возврат importance пользователем не моделируется.
     */
    @Test
    public void z_disabledChannel_doesNotMarkTask() {
        String channelId = com.vpe_soft.intime.intime.Constants.TASK_OVERDUE_CHANNEL_ID;
        manager.createNotificationChannel(new android.app.NotificationChannel(
                channelId, "Disabled device test", NotificationManager.IMPORTANCE_NONE));
        assertEquals(NotificationManager.IMPORTANCE_NONE,
                manager.getNotificationChannel(channelId).getImportance());
        long past = System.currentTimeMillis() - 1000;
        long id = dao.insert(new TaskEntity("Disabled channel", 1, 1, past, past, 0, 1));
        AlarmReceiver.handleAlarm(context, new android.content.Intent()
                .putExtra(com.vpe_soft.intime.intime.Constants.EXTRA_TASK_ID, id));
        assertEquals(0, manager.getActiveNotifications().length);
        assertFalse(dao.getRawTaskById(id).isWasNotified());
    }

    private Notification awaitNotification(long timeoutMillis) {
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        while (SystemClock.elapsedRealtime() < deadline) {
            for (StatusBarNotification posted : manager.getActiveNotifications()) {
                if (AlarmUtil.NOTIFICATION_TAG.equals(posted.getTag())) {
                    return posted.getNotification();
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("No platform notification within " + timeoutMillis + " ms");
    }
}
