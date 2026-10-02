package com.vpe_soft.intime.intime.receiver;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.scheduling.SchedulingCoordinator;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Two host-controlled phases with a real adb reboot between them. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 33)
public class RebootRecoveryTest {
    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private void requirePhase(String method) {
        String filter = InstrumentationRegistry.getArguments().getString("class", "");
        Assume.assumeTrue("Host must run this phase separately around adb reboot",
                filter.endsWith("RebootRecoveryTest#" + method));
    }

    /**
     * Подготавливает проверку настоящего reboot на выделенном AVD: в файловую Room-базу
     * тестового .dev-приложения добавляются синтетическая просроченная и будущая задачи.
     * Их ID, текущий BOOT_COUNT и прежняя отметка использования сохраняются для второй
     * фазы; проверяется успешное сохранение. Данные намеренно переживают завершение теста.
     * В обычной suite фаза пропускается: хост должен вызвать её отдельно перед adb reboot.
     */
    @Test
    public void prepareForReboot() {
        requirePhase("prepareForReboot");
        Context context = context();
        SharedPreferences state = context.getSharedPreferences("reboot_test_fixture", Context.MODE_PRIVATE);
        assertFalse("Previous fixture requires cleanup", state.contains("pastId"));
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        TaskDao dao = AppDatabase.getInstance(context).taskDao();
        long now = System.currentTimeMillis();
        long past = now - 1000;
        long future = now + 600000;
        long pastId = dao.insert(new TaskEntity("REBOOT_TEST_PAST", 1, 1, past, past, 0, 1));
        long futureId = dao.insert(new TaskEntity("REBOOT_TEST_FUTURE", 1, 1, future, future, 0, 1));
        SharedPreferences session = context.getSharedPreferences(Constants.SESSION_INFO_SP_NAME, Context.MODE_PRIVATE);
        assertTrue(state.edit().putLong("pastId", pastId).putLong("futureId", futureId)
                .putInt("bootCount", Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1))
                .putBoolean("hadUsage", session.contains(Constants.LAST_USAGE_TIMESTAMP_KEY))
                .putLong("oldUsage", session.getLong(Constants.LAST_USAGE_TIMESTAMP_KEY, 0)).commit());
        assertTrue(session.edit().putLong(Constants.LAST_USAGE_TIMESTAMP_KEY, now - 60000).commit());
        SchedulingCoordinator.reschedule(context);
        assertNotNull(dao.getRawTaskById(pastId));
        assertNotNull(dao.getRawTaskById(futureId));
    }

    /**
     * Проверяет состояние после настоящего adb reboot: BOOT_COUNT должен увеличиться,
     * обе синтетические задачи сохраниться, а BootReceiver опубликовать системную сводку
     * о пропущенных задачах без ACK. Проверка расписания будущего будильника выполняется
     * хостом через dumpsys alarm до запуска этой фазы. В finally удаляются только задачи
     * фикстуры и восстанавливается отметка использования, без очистки остальных данных.
     */
    @Test
    public void verifyAfterReboot() {
        requirePhase("verifyAfterReboot");
        Context context = context();
        SharedPreferences state = context.getSharedPreferences("reboot_test_fixture", Context.MODE_PRIVATE);
        assertTrue("Run prepareForReboot first", state.contains("pastId"));
        TaskDao dao = AppDatabase.getInstance(context).taskDao();
        long pastId = state.getLong("pastId", -1);
        long futureId = state.getLong("futureId", -1);
        try {
            assertTrue(Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1)
                    > state.getInt("bootCount", -1));
            assertEquals("REBOOT_TEST_PAST", dao.getRawTaskById(pastId).description);
            assertEquals("REBOOT_TEST_FUTURE", dao.getRawTaskById(futureId).description);
            android.app.Notification boot = null;
            long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
            String expected = context.getString(R.string.boot_completed_overdue_tasks_notification);
            while (boot == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                for (StatusBarNotification notification : context.getSystemService(NotificationManager.class)
                        .getActiveNotifications()) {
                    if (AlarmUtil.NOTIFICATION_TAG.equals(notification.getTag())
                            && expected.contentEquals(notification.getNotification().extras
                                    .getCharSequence(android.app.Notification.EXTRA_TEXT, ""))) {
                        boot = notification.getNotification();
                    }
                }
                if (boot == null) android.os.SystemClock.sleep(100);
            }
            assertNotNull("BootReceiver did not post notification", boot);
            assertEquals(context.getString(R.string.boot_completed_overdue_tasks_notification),
                    boot.extras.getCharSequence(android.app.Notification.EXTRA_TEXT));
            assertTrue(boot.actions == null || boot.actions.length == 0);
        } finally {
            TaskEntity pastTask = dao.getRawTaskById(pastId);
            TaskEntity futureTask = dao.getRawTaskById(futureId);
            if (pastTask != null) dao.delete(pastTask);
            if (futureTask != null) dao.delete(futureTask);
            SharedPreferences.Editor session = context.getSharedPreferences(Constants.SESSION_INFO_SP_NAME,
                    Context.MODE_PRIVATE).edit();
            if (state.getBoolean("hadUsage", false)) {
                session.putLong(Constants.LAST_USAGE_TIMESTAMP_KEY, state.getLong("oldUsage", 0));
            } else {
                session.remove(Constants.LAST_USAGE_TIMESTAMP_KEY);
            }
            assertTrue(session.commit());
            assertTrue(state.edit().clear().commit());
            context.getSystemService(NotificationManager.class).cancelAll();
            SchedulingCoordinator.reschedule(context);
        }
    }
}
