package com.vpe_soft.intime.intime.receiver;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Constraints;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.workers.TaskNotificationWorker;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Две фазы для scripts/test-periodic-smoke.ps1; база только новой .dev установки. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 36)
public class PeriodicRecoveryTest {
    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private void requirePhase(String method) {
        Assume.assumeTrue("Run only through test-periodic-smoke.ps1",
                "true".equals(InstrumentationRegistry.getArguments().getString("periodicPhase"))
                && InstrumentationRegistry.getArguments().getString("class", "")
                .endsWith("PeriodicRecoveryTest#" + method));
        assertEquals("com.vpe_soft.intime.intime.dev", context().getPackageName());
    }

    private StatusBarNotification notification(String description) {
        for (StatusBarNotification posted : context().getSystemService(NotificationManager.class).getActiveNotifications()) {
            if (AlarmUtil.NOTIFICATION_TAG.equals(posted.getTag()) && description.contentEquals(
                    posted.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT, ""))) return posted;
        }
        return null;
    }

    private void assertQuiet(Notification notification) {
        assertTrue(notification.actions == null || notification.actions.length == 0);
        assertNull(notification.sound);
        assertNull(notification.vibrate);
        assertEquals(0, notification.defaults & (Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE));
    }

    /**
     * Только отдельная host-фаза на новой .dev установке: пустая файловая Room и
     * отсутствие прежней фикстуры обязательны. После ухода с MainActivity отменяется
     * её штатная periodic work, выдаётся POST_NOTIFICATIONS и создаётся одна уже
     * уведомлённая просроченная задача. LAST_POST датируется 16 минутами ранее.
     * Настоящий WorkManager получает PeriodicWorkRequest с интервалом 15 минут,
     * BatteryNotLow и initial delay 10 секунд. Ожидается первое тихое системное
     * уведомление без ACK и возврат WorkInfo в ENQUEUED с будущим сроком.
     * ID задачи/работы, исходные поля и время первого уведомления сохраняются в
     * preferences для второй фазы; host получает контрольное время через sendStatus.
     * Данные намеренно остаются после instrumentation для смерти процесса.
     * doWork/JobScheduler вручную не запускаются, часы/период не ускоряются.
     * Обычная suite пропускает фазу; личные данные и host main*.db не используются.
     */
    @Test
    public void prepareFirstRun() throws Exception {
        requirePhase("prepareFirstRun");
        Context context = context();
        SharedPreferences fixture = context.getSharedPreferences("periodic_recovery_fixture", Context.MODE_PRIVATE);
        assertFalse(fixture.contains("workId"));
        TaskDao dao = AppDatabase.getInstance(context).taskDao();
        assertEquals("Refusing existing data", 0, dao.getTaskCount());
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(TaskNotificationWorker.class.getName()).getResult().get(10, TimeUnit.SECONDS);
        long started = System.currentTimeMillis();
        String description = "PERIODIC_RECOVERY_" + started;
        long past = started - 1000;
        long taskId = dao.insert(new TaskEntity(description, 1, 1, past, past, 0, 1));
        dao.markTaskNotified(taskId);
        SharedPreferences reminder = context.getSharedPreferences("notification_reminder_state", Context.MODE_PRIVATE);
        boolean hadLastPost = reminder.contains("last_successful_post");
        long oldLastPost = reminder.getLong("last_successful_post", 0);
        assertTrue(reminder.edit().putLong("last_successful_post", started - 960000).commit());
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(TaskNotificationWorker.class, 15, TimeUnit.MINUTES)
                .setInitialDelay(10, TimeUnit.SECONDS)
                .setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true).build()).build();
        assertTrue(fixture.edit().putString("workId", request.getId().toString()).putLong("taskId", taskId)
                .putString("description", description).putLong("lastAck", dao.getRawTaskById(taskId).lastAck)
                .putLong("nextAlarm", past).putBoolean("hadLastPost", hadLastPost)
                .putLong("oldLastPost", oldLastPost).commit());
        workManager.enqueue(request).getResult().get(10, TimeUnit.SECONDS);
        long limit = SystemClock.elapsedRealtime() + 40000;
        StatusBarNotification posted;
        do {
            posted = notification(description);
            if (posted != null) break;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < limit);
        assertNotNull("First periodic notification missing", posted);
        assertQuiet(posted.getNotification());
        WorkInfo info;
        do {
            info = workManager.getWorkInfoById(request.getId()).get(5, TimeUnit.SECONDS);
            if (info != null && info.getState() == WorkInfo.State.ENQUEUED
                    && info.getNextScheduleTimeMillis() >= started + TimeUnit.MINUTES.toMillis(14)) break;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < limit);
        assertNotNull(info);
        assertEquals(WorkInfo.State.ENQUEUED, info.getState());
        assertTrue(info.getNextScheduleTimeMillis() >= started + TimeUnit.MINUTES.toMillis(14));
        assertTrue(fixture.edit().putLong("firstPost", posted.getPostTime()).commit());
        Bundle status = new Bundle();
        status.putString("periodic_description", description);
        status.putString("periodic_first_post", Long.toString(posted.getPostTime()));
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
    }

    /**
     * После первой instrumentation host выполнил am kill (не shell force-stop),
     * подтвердил отсутствие PID/stopped=false и без открытия Activity/instrumentation
     * дождался нового уведомления не ранее чем через реальные 15 минут. Сам Android
     * может завершить процесс ещё при окончании первой instrumentation; тест не
     * различает эти причины смерти процесса. Фаза открывает ту же файловую Room и проверяет неизменные
     * описание/lastAck/nextAlarm/wasNotified. Уведомление должно быть тихим, без ACK,
     * с postTime не раньше первого +15 минут; LAST_POST также должен обновиться.
     * Сохранённый UUID WorkManager должен оставаться ENQUEUED с новым будущим сроком.
     * В finally отменяется только UUID фикстуры, удаляется её задача, возвращается
     * прежний LAST_POST и удаляются preferences/уведомления фикстуры. Это debug
     * проверка одного второго периода после обычной смерти процесса, не force-stop,
     * reboot, Doze, release/R8, OEM-квоты или дальнейшие многократные повторы.
     * Host может временно уменьшить пакетный порог JobScheduler на проектном AVD:
     * при таком условии успех не доказывает срок доставки с настройками ОС по умолчанию.
     */
    @Test
    public void verifySecondRun() throws Exception {
        requirePhase("verifySecondRun");
        Context context = context();
        SharedPreferences fixture = context.getSharedPreferences("periodic_recovery_fixture", Context.MODE_PRIVATE);
        assertTrue("Prepare phase missing", fixture.contains("firstPost"));
        WorkManager workManager = WorkManager.getInstance(context);
        UUID workId = UUID.fromString(fixture.getString("workId", ""));
        TaskDao dao = AppDatabase.getInstance(context).taskDao();
        long taskId = fixture.getLong("taskId", -1);
        try {
            TaskEntity task = dao.getRawTaskById(taskId);
            assertNotNull(task);
            assertEquals(fixture.getString("description", ""), task.description);
            assertEquals(fixture.getLong("lastAck", -1), task.lastAck.longValue());
            assertEquals(fixture.getLong("nextAlarm", -1), task.nextAlarm.longValue());
            assertTrue(task.isWasNotified());
            long earliest = fixture.getLong("firstPost", 0) + TimeUnit.MINUTES.toMillis(15);
            StatusBarNotification posted = notification(task.description);
            assertNotNull(posted);
            assertTrue("Notification was not repeated", posted.getPostTime() >= earliest);
            assertQuiet(posted.getNotification());
            assertTrue(context.getSharedPreferences("notification_reminder_state", Context.MODE_PRIVATE)
                    .getLong("last_successful_post", 0) >= earliest);
            long limit = SystemClock.elapsedRealtime() + 10000;
            WorkInfo info;
            do {
                info = workManager.getWorkInfoById(workId).get(5, TimeUnit.SECONDS);
                if (info != null && info.getState() == WorkInfo.State.ENQUEUED
                        && info.getNextScheduleTimeMillis() > System.currentTimeMillis()) break;
                SystemClock.sleep(100);
            } while (SystemClock.elapsedRealtime() < limit);
            assertNotNull(info);
            assertEquals(WorkInfo.State.ENQUEUED, info.getState());
            assertTrue(info.getNextScheduleTimeMillis() > System.currentTimeMillis());
        } finally {
            workManager.cancelWorkById(workId).getResult().get(10, TimeUnit.SECONDS);
            TaskEntity task = dao.getRawTaskById(taskId);
            if (task != null) dao.delete(task);
            SharedPreferences.Editor restore = context.getSharedPreferences("notification_reminder_state", Context.MODE_PRIVATE).edit();
            if (fixture.getBoolean("hadLastPost", false)) restore.putLong("last_successful_post", fixture.getLong("oldLastPost", 0));
            else restore.remove("last_successful_post");
            assertTrue(restore.commit());
            assertTrue(fixture.edit().clear().commit());
            context.getSystemService(NotificationManager.class).cancel(AlarmUtil.NOTIFICATION_TAG, 1);
        }
    }
}
