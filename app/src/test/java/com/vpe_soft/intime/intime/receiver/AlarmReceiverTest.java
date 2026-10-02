package com.vpe_soft.intime.intime.receiver;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.testing.TestWorkerBuilder;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.notifications.NotificationHelper;
import com.vpe_soft.intime.intime.workers.TaskNotificationWorker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AlarmReceiverTest {
    private Context context;
    private AppDatabase database;
    private TaskDao dao;
    private NotificationManager manager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        AppDatabase.setTestInstance(database);
        dao = database.taskDao();
        manager = context.getSystemService(NotificationManager.class);
        shadowOf((Application) context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        shadowOf(manager).setNotificationsEnabled(true);
        NotificationHelper.ensureTaskOverdueChannel(context);
    }

    @After
    public void tearDown() {
        manager.cancelAll();
        database.close();
        AppDatabase.setTestInstance(null);
    }

    /**
     * Проверяет: Последовательно обрабатываются будильники двух просроченных задач. Оба уведомления
     * должны иметь по одному действию с разными PendingIntent. В сохранённом Intent каждого действия
     * проверяется id именно соответствующей задачи.
     */
    @Test
    public void consecutiveNotifications_keepAcknowledgementBoundToEachTask() throws Exception {
        long firstId = addDueTask("First");
        long secondId = addDueTask("Second");
        fireAlarm(firstId);
        Notification first = currentNotification();
        fireAlarm(secondId);
        Notification second = currentNotification();

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(1, first.actions.length);
        assertEquals(1, second.actions.length);
        assertNotEquals(first.actions[0].actionIntent, second.actions[0].actionIntent);
        assertEquals(firstId, shadowOf(first.actions[0].actionIntent).getSavedIntent()
                .getLongExtra(Constants.EXTRA_TASK_ID, -1));
        assertEquals(secondId, shadowOf(second.actions[0].actionIntent).getSavedIntent()
                .getLongExtra(Constants.EXTRA_TASK_ID, -1));
    }

    /**
     * Проверяет: На API 35 запрещается POST_NOTIFICATIONS и обрабатывается просроченная задача; в базе
     * также есть срок через час. Ожидаются отсутствие уведомления, wasNotified = false и время первого
     * запланированного будильника, равное сроку будущей задачи.
     */
    @Test
    public void deniedPermission_doesNotMarkTaskAndStillSchedulesNextAlarm() throws Exception {
        long dueId = addDueTask("Due");
        long future = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
        dao.insert(new TaskEntity("Future", 1, 1, future, future, 0, 1));
        shadowOf((Application) context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);

        fireAlarm(dueId);

        assertNull(currentNotification());
        assertFalse(dao.getRawTaskById(dueId).isWasNotified());
        android.app.AlarmManager alarmManager = context.getSystemService(android.app.AlarmManager.class);
        assertEquals(future, shadowOf(alarmManager).getScheduledAlarms().get(0).triggerAtTime);
    }

    /**
     * Проверяет: Уведомления приложения целиком отключаются в Robolectric, затем обрабатывается
     * просроченная задача. Ожидаются отсутствие уведомления в NotificationManager и сохранение
     * wasNotified = false.
     */
    @Test
    public void disabledNotifications_doNotMarkTask() throws Exception {
        long id = addDueTask("Due");
        shadowOf(manager).setNotificationsEnabled(false);
        fireAlarm(id);
        assertNull(currentNotification());
        assertFalse(dao.getRawTaskById(id).isWasNotified());
    }

    /**
     * Проверяет: Для канала просроченных задач устанавливается IMPORTANCE_NONE, затем вызывается
     * обработчик будильника. Уведомление должно отсутствовать, а wasNotified оставаться false для
     * последующей попытки.
     */
    @Test
    public void disabledChannel_doesNotMarkTask() throws Exception {
        long id = addDueTask("Due");
        disableChannel();
        fireAlarm(id);
        assertNull(currentNotification());
        assertFalse(dao.getRawTaskById(id).isWasNotified());
    }

    /**
     * Проверяет: При разрешённых уведомлениях и доступном канале обрабатывается просроченная задача.
     * Ожидаются зарегистрированное Robolectric уведомление и wasNotified = true в базе. Проверяется
     * публикация, а не прочтение пользователем.
     */
    @Test
    public void postedNotification_marksTask() throws Exception {
        long id = addDueTask("Due");
        fireAlarm(id);
        assertNotNull(currentNotification());
        assertTrue(dao.getRawTaskById(id).isWasNotified());
    }

    /**
     * Проверяет: Сначала будильник обрабатывается без POST_NOTIFICATIONS, оставляя wasNotified =
     * false. После выдачи разрешения явно запускается doWork. Ожидаются уведомление без действий
     * подтверждения и wasNotified = true у задачи.
     */
    @Test
    public void restoredPermission_workerRecoversTaskWithoutAcknowledgementAction() throws Exception {
        long id = addDueTask("Due");
        shadowOf((Application) context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        fireAlarm(id);
        assertFalse(dao.getRawTaskById(id).isWasNotified());
        shadowOf((Application) context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);

        runWorker();

        Notification notification = currentNotification();
        assertNotNull(notification);
        assertTrue(notification.actions == null || notification.actions.length == 0);
        assertTrue(dao.getRawTaskById(id).isWasNotified());
    }

    /**
     * Проверяет: Канал отключается через IMPORTANCE_NONE, затем для просроченной задачи явно
     * запускается doWork через TestWorkerBuilder. Ожидаются отсутствие уведомления и wasNotified =
     * false.
     */
    @Test
    public void workerWithDisabledChannel_doesNotMarkTask() {
        long id = addDueTask("Due");
        disableChannel();
        runWorker();
        assertNull(currentNotification());
        assertFalse(dao.getRawTaskById(id).isWasNotified());
    }

    /**
     * Проверяет: На API 35 отзывается POST_NOTIFICATIONS и явно запускается doWork для просроченной
     * задачи. Ожидаются отсутствие уведомления и wasNotified = false; расписание WorkManager здесь не
     * проверяется.
     */
    @Test
    public void workerWithDeniedPermission_doesNotMarkTask() {
        long id = addDueTask("Due");
        shadowOf((Application) context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        runWorker();
        assertNull(currentNotification());
        assertFalse(dao.getRawTaskById(id).isWasNotified());
    }

    /**
     * Проверяет: Уведомления всего приложения выключаются в Robolectric, затем явно запускается worker
     * для просроченной задачи. Ожидаются отсутствие уведомления в NotificationManager и wasNotified =
     * false в базе.
     */
    @Test
    public void workerWithDisabledNotifications_doesNotMarkTask() {
        long id = addDueTask("Due");
        shadowOf(manager).setNotificationsEnabled(false);
        runWorker();
        assertNull(currentNotification());
        assertFalse(dao.getRawTaskById(id).isWasNotified());
    }

    /**
     * Проверяет устаревший будильник удалённой задачи: запись удаляется до обработки
     * сохранённого Intent. Уведомление не должно появиться, база остаётся пустой.
     */
    @Test
    public void deletedTask_staleAlarmDoesNotNotify() throws Exception {
        long id = addDueTask("Deleted");
        Intent intent = alarmIntent(id, "Deleted");
        dao.delete(dao.getRawTaskById(id));
        fireIntent(intent);
        assertNull(currentNotification());
        assertEquals(0, dao.getTaskCount());
    }

    /**
     * Проверяет старый будильник после подтверждения и переноса срока в будущее.
     * Обрабатывается сохранённый Intent: уведомления нет, wasNotified остаётся false,
     * а первый запланированный будильник получает новый срок задачи.
     */
    @Test
    public void acknowledgedTask_staleAlarmSchedulesNewDeadline() throws Exception {
        long id = addDueTask("Due");
        Intent intent = alarmIntent(id, "Due");
        long now = System.currentTimeMillis();
        long future = now + TimeUnit.HOURS.toMillis(1);
        dao.acknowledgeTask(id, now, future, future);
        fireIntent(intent);
        assertNull(currentNotification());
        assertFalse(dao.getRawTaskById(id).isWasNotified());
        android.app.AlarmManager alarms = context.getSystemService(android.app.AlarmManager.class);
        assertEquals(future, shadowOf(alarms).getScheduledAlarms().get(0).triggerAtTime);
    }

    /**
     * Проверяет повторную доставку будильника уже уведомлённой задачи.
     * После первой публикации уведомление удаляется из менеджера и событие повторяется.
     * Новое уведомление не должно появиться, wasNotified должен остаться true.
     */
    @Test
    public void repeatedAlarm_doesNotPostAgain() throws Exception {
        long id = addDueTask("Due");
        fireAlarm(id);
        assertNotNull(currentNotification());
        manager.cancelAll();
        fireAlarm(id);
        assertNull(currentNotification());
        assertTrue(dao.getRawTaskById(id).isWasNotified());
    }

    /**
     * Проверяет чтение актуального описания из Room вместо текста старого Intent.
     * Задача переименовывается после создания Intent; уведомление должно содержать
     * новое описание Updated и задача должна получить wasNotified = true.
     */
    @Test
    public void renamedTask_alarmUsesCurrentDescription() throws Exception {
        long id = addDueTask("Old");
        Intent intent = alarmIntent(id, "Old");
        TaskEntity task = dao.getRawTaskById(id);
        task.description = "Updated";
        dao.update(task);
        fireIntent(intent);
        assertEquals("Updated", currentNotification().extras.getCharSequence(Notification.EXTRA_TEXT));
        assertTrue(dao.getRawTaskById(id).isWasNotified());
    }

    private void disableChannel() {
        manager.createNotificationChannel(new NotificationChannel(
                Constants.TASK_OVERDUE_CHANNEL_ID, "Disabled", NotificationManager.IMPORTANCE_NONE));
    }

    private long addDueTask(String description) {
        long past = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1);
        return dao.insert(new TaskEntity(description, 1, 1, past, past, past, 1));
    }

    private Notification currentNotification() {
        return shadowOf(manager).getNotification(AlarmUtil.NOTIFICATION_TAG, 1);
    }

    private void fireAlarm(long id) throws Exception {
        fireIntent(alarmIntent(id, dao.getRawTaskById(id).description));
    }

    private Intent alarmIntent(long id, String description) {
        return new Intent(context, AlarmReceiver.class)
                .putExtra(Constants.EXTRA_TASK_ID, id)
                .putExtra(Constants.EXTRA_TASK_DESCRIPTION, description);
    }

    private void fireIntent(Intent intent) throws Exception {
        // Выполняем обработчик синхронно на фоновом потоке, включая перепланирование.
        FutureTask<Void> work = new FutureTask<>(() -> {
            AlarmReceiver.handleAlarm(context, intent);
            return null;
        });
        new Thread(work).start();
        work.get(10, TimeUnit.SECONDS);
    }

    private void runWorker() {
        TaskNotificationWorker worker = TestWorkerBuilder.from(
                context, TaskNotificationWorker.class, Runnable::run).build();
        worker.doWork();
    }
}
