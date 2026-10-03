package com.vpe_soft.intime.intime.database;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.Assume;
import org.junit.Test;

public class UpgradePrepareTest {
    /**
     * Отдельная smoke-фаза после запуска старого APK 1.1.11/24 на проектном AVD. Проверяет
     * production-пакет, существование созданной старым приложением базы main версии 5,
     * пустой список и отсутствие метаданных Room. Добавляет две синтетические задачи
     * со всеми восемью полями и удалённый ID 500 для AUTOINCREMENT; сохраняет контрольные
     * времена, UID и firstInstallTime в preferences. База намеренно переживает тест для
     * установки обновления. В обычной suite фаза пропускается; пользовательские данные
     * не читаются. Класс не использует классы нового приложения: выполняется внутри старого APK.
     */
    @Test public void prepareOldApplication() throws Exception {
        Assume.assumeTrue(UpgradeSmokeInstrumentation.isPrepare());
        Context context = UpgradeSmokeInstrumentation.targetContext();
        assertEquals("com.vpe_soft.intime.intime", context.getPackageName());
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        assertEquals(24, info.getLongVersionCode());
        assertTrue(context.getDatabasePath("main").exists());
        long future = System.currentTimeMillis() + 7200000;
        try (SQLiteDatabase raw = SQLiteDatabase.openDatabase(context.getDatabasePath("main").getPath(), null, 0)) {
            assertEquals(5, raw.getVersion());
            try (Cursor cursor = raw.rawQuery("SELECT COUNT(*) FROM tasks", null)) {
                assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0));
            }
            try (Cursor cursor = raw.rawQuery("SELECT name FROM sqlite_master WHERE name = 'room_master_table'", null)) {
                assertFalse(cursor.moveToFirst());
            }
            raw.execSQL("INSERT INTO tasks VALUES (7, 'UPGRADE_FIRST', 1, 2, ?, ?, 1000, 2)",
                    new Object[]{future, future - 60000});
            raw.execSQL("INSERT INTO tasks VALUES (22, 'UPGRADE_SECOND', 2, 3, ?, ?, 2000, 3)",
                    new Object[]{future + 7200000, future});
            raw.execSQL("INSERT INTO tasks VALUES (500, 'DELETED', 1, 1, 0, 0, 0, 1)");
            raw.execSQL("DELETE FROM tasks WHERE id = 500");
        }
        assertTrue(context.getSharedPreferences("upgrade_fixture", Context.MODE_PRIVATE).edit()
                .putLong("future", future).putLong("firstInstallTime", info.firstInstallTime)
                .putInt("uid", info.applicationInfo.uid).commit());
    }
}
