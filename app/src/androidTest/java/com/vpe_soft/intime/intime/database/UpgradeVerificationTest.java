package com.vpe_soft.intime.intime.database;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
import org.junit.Assume;
import org.junit.Test;

public class UpgradeVerificationTest {
    /**
     * После install -r debug-сборки 25 поверх переподписанной копии APK 24 проверяет неизменные
     * UID/firstInstallTime и preferences фикстуры, все восемь полей двух задач и wasNotified = false
     * после миграции 5 → 6. Через репозиторий подтверждает первую задачу (сохранив параметры второй),
     * затем выполняет edit/insert/delete через DAO и проверяет новый ID > 500. Сохраняет новый срок
     * первой задачи для проверки host через dumpsys alarm и после перезапуска процесса.
     * Обычная suite пропускает фазу; ключ тестовый, совместимость production-подписи не утверждается.
     */
    @Test public void verifyAfterUpdate() throws Exception {
        Context context = phase("verify");
        SharedPreferences state = identity(context);
        long future = state.getLong("future", -1);
        AppDatabase database = AppDatabase.getInstance(context);
        TaskEntity first = task(7, "UPGRADE_FIRST", 1, 2, future, future - 60000, 1000, 2);
        TaskEntity second = task(22, "UPGRADE_SECOND", 2, 3, future + 7200000, future, 2000, 3);
        assertEquals(2, database.taskDao().getTaskCount());
        assertEquals(first, database.taskDao().getRawTaskById(7));
        assertEquals(second, database.taskDao().getRawTaskById(22));
        assertFalse(database.taskDao().getRawTaskById(7).isWasNotified());
        assertFalse(database.taskDao().getRawTaskById(22).isWasNotified());
        assertEquals(6, database.getOpenHelper().getWritableDatabase().getVersion());
        new TaskRepository(context).acknowledgeTaskById(7);
        first = database.taskDao().getRawTaskById(7);
        assertTrue(first.lastAck > 1000);
        assertTrue(first.nextAlarm > System.currentTimeMillis());
        assertEquals(second, database.taskDao().getRawTaskById(22));
        second.description = "UPGRADE_EDITED";
        database.taskDao().update(second);
        TaskEntity added = task(0, "UPGRADE_ADDED", 1, 1, future + 9000000, future, 0, 1);
        added.setId(database.taskDao().insert(added));
        assertTrue(added.id > 500);
        database.taskDao().delete(added);
        assertEquals(2, database.taskDao().getTaskCount());
        assertEquals(second, database.taskDao().getRawTaskById(22));
        assertTrue(state.edit().putLong("ack", first.lastAck).putLong("alarm", first.nextAlarm)
                .putLong("caution", first.nextCaution).putBoolean("verified", true).commit());
    }

    /**
     * После запуска нового launcher и принудительного завершения процесса хостом повторно
     * открывает штатный singleton Room. Проверяет сохранение UID/firstInstallTime и всех полей
     * обеих задач, включая новый ACK/срок первой и edit второй из прошлой фазы; записей ровно две,
     * wasNotified не установлен. Host отдельно проверяет настоящий alarm по сохранённому сроку.
     * Фаза пропускается в обычной suite; пользовательские файлы и production-сертификат не проверяются.
     */
    @Test public void verifyAfterRestart() throws Exception {
        Context context = phase("restart");
        SharedPreferences state = identity(context);
        assertTrue(state.getBoolean("verified", false));
        long future = state.getLong("future", -1);
        AppDatabase database = AppDatabase.getInstance(context);
        assertEquals(2, database.taskDao().getTaskCount());
        TaskEntity first = task(7, "UPGRADE_FIRST", 1, 2, state.getLong("alarm", -1),
                state.getLong("caution", -1), state.getLong("ack", -1), 2);
        assertEquals(first, database.taskDao().getRawTaskById(7));
        assertEquals(task(22, "UPGRADE_EDITED", 2, 3, future + 7200000, future, 2000, 3),
                database.taskDao().getRawTaskById(22));
        assertFalse(database.taskDao().getRawTaskById(7).isWasNotified());
        assertFalse(database.taskDao().getRawTaskById(22).isWasNotified());
        android.util.Log.i("UpgradeSmoke", "EXPECTED_ALARM=" + first.nextAlarm);
    }

    private static Context phase(String phase) {
        Assume.assumeTrue(phase.equals(InstrumentationRegistry.getArguments().getString("upgradePhase")));
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static SharedPreferences identity(Context context) throws Exception {
        assertEquals("com.vpe_soft.intime.intime", context.getPackageName());
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        assertEquals(25, info.getLongVersionCode());
        SharedPreferences state = context.getSharedPreferences("upgrade_fixture", Context.MODE_PRIVATE);
        assertEquals(state.getLong("firstInstallTime", -1), info.firstInstallTime);
        assertEquals(state.getInt("uid", -1), info.applicationInfo.uid);
        return state;
    }

    private static TaskEntity task(long id, String text, int interval, int amount,
                                   long alarm, long caution, long ack, int quant) {
        TaskEntity task = new TaskEntity(text, interval, amount, alarm, caution, ack, quant);
        task.setId(id); return task;
    }
}
