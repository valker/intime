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
     * В in-memory Room создаётся одна уже уведомлённая просроченная задача; lastAck
     * и nextAlarm запоминаются, последняя успешная отправка в preferences датируется
     * 16 минутами ранее. Разрешение уведомлений выдано, канал включён, Activity не
     * открывается. Настоящему WorkManager передаётся новая периодическая работа
     * после отмены оставшейся штатной periodic work этого тестового приложения,
     * чтобы другое расписание не отправило уведомление вместо проверяемого запроса.
     * TaskNotificationWorker с интервалом 15 минут, BatteryNotLow и начальной
     * задержкой 10 секунд; TestWorkerBuilder и явный doWork не используются.
     * До запуска проверяется регистрация её ID в Android JobScheduler (на API 34+
     * в namespace androidx.work.systemjobscheduler). Затем
     * ожидается системное уведомление с исходным описанием, без ACK, звука,
     * вибрации и звуковых defaults. Проверяются неизменные lastAck/nextAlarm,
     * сохранённый wasNotified и обновление времени успешной отправки. WorkInfo
     * должен вернуться в ENQUEUED с будущим nextScheduleTimeMillis не менее чем
     * через 14 минут от начала проверки: это подтверждает перепланирование
     * периодической работы после первого исполнения. В finally отменяется только
     * работа с созданным здесь UUID и возвращается прежнее значение LAST_POST.
     * Проверка не ждёт второй 15-минутный запуск,
     * не доказывает выбор SystemJobScheduler вместо GreedyScheduler для исполнения,
     * регистрацию работы MainActivity, устойчивость после убийства процесса или Doze.
     */
    @Test
    public void realPeriodicWorker_postsQuietReminderAndSchedulesNextRun() throws Exception {
        androidx.work.WorkManager workManager = androidx.work.WorkManager.getInstance(context);
        workManager.cancelUniqueWork(com.vpe_soft.intime.intime.workers.TaskNotificationWorker.class.getName())
                .getResult().get(10, TimeUnit.SECONDS);
        android.content.SharedPreferences reminderState = context.getSharedPreferences(
                "notification_reminder_state", Context.MODE_PRIVATE);
        boolean hadLastPost = reminderState.contains("last_successful_post");
        long previousLastPost = reminderState.getLong("last_successful_post", 0);
        long started = System.currentTimeMillis();
        long past = started - 1000;
        long id = dao.insert(new TaskEntity("Periodic device", 1, 1, past, past, 0, 1));
        dao.markTaskNotified(id);
        TaskEntity before = dao.getRawTaskById(id);
        assertTrue(context.getSharedPreferences("notification_reminder_state", Context.MODE_PRIVATE)
                .edit().putLong("last_successful_post", started - 960000).commit());
        androidx.work.PeriodicWorkRequest request = new androidx.work.PeriodicWorkRequest.Builder(
                com.vpe_soft.intime.intime.workers.TaskNotificationWorker.class, 15, TimeUnit.MINUTES)
                .setInitialDelay(10, TimeUnit.SECONDS)
                .setConstraints(new androidx.work.Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build();
        try {
            workManager.enqueue(request).getResult().get(10, TimeUnit.SECONDS);
            android.app.job.JobScheduler scheduler = context.getSystemService(android.app.job.JobScheduler.class);
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                scheduler = scheduler.forNamespace("androidx.work.systemjobscheduler");
            }
            long registrationLimit = SystemClock.elapsedRealtime() + 5000;
            boolean registered = false;
            do {
                for (android.app.job.JobInfo job : scheduler.getAllPendingJobs()) {
                    if (request.getId().toString().equals(job.getExtras().getString("EXTRA_WORK_SPEC_ID"))) {
                        registered = true;
                        break;
                    }
                }
                if (!registered) SystemClock.sleep(100);
            } while (!registered && SystemClock.elapsedRealtime() < registrationLimit);
            assertTrue("Periodic work was not registered with Android JobScheduler", registered);
            Notification notification = awaitNotification(30000);
            assertEquals("Periodic device", notification.extras.getCharSequence(Notification.EXTRA_TEXT));
            assertTrue(notification.actions == null || notification.actions.length == 0);
            assertNull(notification.sound);
            assertNull(notification.vibrate);
            assertEquals(0, notification.defaults & (Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE));
            TaskEntity after = dao.getRawTaskById(id);
            assertEquals(before.lastAck, after.lastAck);
            assertEquals(before.nextAlarm, after.nextAlarm);
            assertTrue(after.isWasNotified());
            assertTrue(context.getSharedPreferences("notification_reminder_state", Context.MODE_PRIVATE)
                    .getLong("last_successful_post", 0) >= started);
            long rescheduleLimit = SystemClock.elapsedRealtime() + 10000;
            androidx.work.WorkInfo info;
            do {
                info = workManager.getWorkInfoById(request.getId()).get(5, TimeUnit.SECONDS);
                if (info != null && info.getState() == androidx.work.WorkInfo.State.ENQUEUED
                        && info.getNextScheduleTimeMillis() >= started + TimeUnit.MINUTES.toMillis(14)) break;
                SystemClock.sleep(100);
            } while (SystemClock.elapsedRealtime() < rescheduleLimit);
            assertNotNull(info);
            assertEquals(androidx.work.WorkInfo.State.ENQUEUED, info.getState());
            assertTrue("Next periodic run was not scheduled in the future",
                    info.getNextScheduleTimeMillis() >= started + TimeUnit.MINUTES.toMillis(14));
        } finally {
            try {
                workManager.cancelWorkById(request.getId()).getResult().get(10, TimeUnit.SECONDS);
            } finally {
                android.content.SharedPreferences.Editor restore = reminderState.edit();
                if (hadLastPost) restore.putLong("last_successful_post", previousLastPost);
                else restore.remove("last_successful_post");
                assertTrue(restore.commit());
            }
        }
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
