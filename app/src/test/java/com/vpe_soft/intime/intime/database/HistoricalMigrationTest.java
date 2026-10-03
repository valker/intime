package com.vpe_soft.intime.intime.database;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HistoricalMigrationTest {
    private static final String TEST_DB = "historical_migration_fixture";
    private Context context;
    private AppDatabase database;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TEST_DB);
    }

    @After public void tearDown() {
        if (database != null) database.close();
        context.deleteDatabase(TEST_DB);
    }

    /**
     * Создаёт отдельную файловую SQLite-базу версии 4 по DDL из тега v1.1.2: семь полей,
     * UNIQUE у ID, AUTOINCREMENT и отсутствие room_master_table. Три синтетические задачи
     * содержат разные ID, русские/пустые описания, интервалы и времена, включая ноль.
     * Открывает базу штатным builder AppDatabase с цепочкой 4 → 5 → 6 и сравнивает каждое
     * старое поле: добавлены только quant = 1 и wasNotified = false, версия стала 6,
     * появилась identity Room. Повторное открытие должно сохранить записи; DAO insert,
     * edit, markNotified, ACK и delete должны работать, AUTOINCREMENT учитывать удалённый
     * ID 500. Проверяет SQLite/Room Robolectric, без APK-обновления, alarm и реальных данных.
     */
    @Test public void rawVersionFourMigratesPreservesFieldsAndSupportsReopeningAndWrites() {
        List<TaskEntity> expected = tasks(1);
        seed(4, expected, false);
        verifyMigrationAndWrites(expected);
    }

    /**
     * Создаёт SQLite версии 5 по историческому DDL коммита 0add531, без метаданных Room.
     * У трёх задач quant равен 1, 7 и 3. После открытия через штатный builder и миграцию
     * 5 → 6 проверяет все восемь старых полей, ID/число строк, wasNotified = false и identity
     * Room. Затем повторяет открытие, проверяет сохранность полей и операции DAO с сохранением
     * последовательности AUTOINCREMENT. Это синтетическая историческая база, не файл пользователя,
     * не exported Room v5 fixture и не установка новой версии APK поверх старой.
     */
    @Test public void rawVersionFivePreservesQuantAndAllFieldsAcrossMigrationAndReopening() {
        List<TaskEntity> expected = tasks(7);
        seed(5, expected, false);
        verifyMigrationAndWrites(expected);
    }

    /**
     * Создаёт отдельную повреждённую SQLite-базу версии 5: вместо amount в DDL стоит wrong_amount.
     * Открытие через штатный builder добавляет wasNotified, но проверка схемы Room должна
     * завершиться IllegalStateException. После закрытия Room открывает файл напрямую и проверяет
     * откат до user_version = 5, отсутствие wasNotified и room_master_table, сохранение трёх строк
     * и описания/quant первой задачи. Доказывает отсутствие разрушительного fallback и откат
     * неуспешной миграции; остальные поля повреждённой базы и сбой процесса не проверяются.
     */
    @Test public void incompatibleLegacySchemaFailsWithoutDeletingRowsOrPartiallyMigrating() {
        List<TaskEntity> expected = tasks(7);
        seed(5, expected, true);
        database = AppDatabase.createBuilder(context, TEST_DB).allowMainThreadQueries().build();
        assertThrows(IllegalStateException.class, () -> database.taskDao().getTaskCount());
        database.close();
        database = null;
        try (SQLiteDatabase raw = SQLiteDatabase.openDatabase(context.getDatabasePath(TEST_DB).getPath(), null,
                SQLiteDatabase.OPEN_READONLY)) {
            assertEquals(5, raw.getVersion());
            assertFalse(hasTable(raw, "room_master_table"));
            try (Cursor columns = raw.rawQuery("PRAGMA table_info(tasks)", null)) {
                while (columns.moveToNext()) assertNotEquals("wasNotified", columns.getString(1));
            }
            try (Cursor rows = raw.rawQuery("SELECT description, quant FROM tasks ORDER BY id", null)) {
                assertEquals(3, rows.getCount());
                assertTrue(rows.moveToFirst());
                assertEquals(expected.get(0).description, rows.getString(0));
                assertEquals(expected.get(0).quant.intValue(), rows.getInt(1));
            }
        }
    }

    private void seed(int version, List<TaskEntity> tasks, boolean incompatible) {
        // Источники DDL: v1.1.2:intime/app/.../InTimeOpenHelper.java и 0add531:app/.../InTimeOpenHelper.java.
        try (SQLiteDatabase raw = context.openOrCreateDatabase(TEST_DB, Context.MODE_PRIVATE, null)) {
            raw.execSQL("CREATE TABLE main.tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL UNIQUE," +
                    " description TEXT NOT NULL, interval INTEGER NOT NULL," +
                    (incompatible ? " wrong_amount" : " amount") + " INTEGER NOT NULL," +
                    " next_alarm INTEGER NOT NULL DEFAULT 0, next_caution INTEGER NOT NULL DEFAULT 0," +
                    " last_ack INTEGER NOT NULL DEFAULT 0" +
                    (version == 5 ? ", quant INTEGER NOT NULL DEFAULT 1" : "") + ")");
            for (TaskEntity task : tasks) insertRaw(raw, version, task);
            insertRaw(raw, version, task(500, "Удалённая до обновления", 1, 1, 1000, 900, 0, 1));
            raw.execSQL("DELETE FROM tasks WHERE id = 500");
            raw.setVersion(version);
            assertFalse(hasTable(raw, "room_master_table"));
        }
    }

    private static void insertRaw(SQLiteDatabase raw, int version, TaskEntity task) {
        if (version == 4) {
            raw.execSQL("INSERT INTO tasks VALUES (?, ?, ?, ?, ?, ?, ?)", new Object[]{
                    task.id, task.description, task.interval, task.amount, task.nextAlarm, task.nextCaution, task.lastAck});
        } else {
            raw.execSQL("INSERT INTO tasks VALUES (?, ?, ?, ?, ?, ?, ?, ?)", new Object[]{
                    task.id, task.description, task.interval, task.amount, task.nextAlarm, task.nextCaution, task.lastAck, task.quant});
        }
    }

    private void verifyMigrationAndWrites(List<TaskEntity> expected) {
        database = AppDatabase.createBuilder(context, TEST_DB).allowMainThreadQueries().build();
        assertTasks(expected);
        assertEquals(6, database.getOpenHelper().getWritableDatabase().getVersion());
        try (Cursor identity = database.getOpenHelper().getWritableDatabase().query(
                "SELECT identity_hash FROM room_master_table WHERE id = 42")) {
            assertTrue(identity.moveToFirst());
            assertFalse(identity.getString(0).isEmpty());
        }
        database.close();
        database = AppDatabase.createBuilder(context, TEST_DB).allowMainThreadQueries().build();
        assertTasks(expected);
        TaskEntity added = task(0, "Добавленная", 1, 2, 9000, 8500, 1000, 2);
        added.setId(database.taskDao().insert(added));
        assertTrue("AUTOINCREMENT must retain deleted ID 500", added.id > 500);
        added.description = "Изменённая";
        database.taskDao().update(added);
        assertEquals(added, database.taskDao().getRawTaskById(added.id));
        database.taskDao().markTaskNotified(added.id);
        assertTrue(database.taskDao().getRawTaskById(added.id).isWasNotified());
        database.taskDao().acknowledgeTask(added.id, 2000, 10000, 9500);
        added.lastAck = 2000L;
        added.nextAlarm = 10000L;
        added.nextCaution = 9500L;
        assertEquals(added, database.taskDao().getRawTaskById(added.id));
        assertFalse(database.taskDao().getRawTaskById(added.id).isWasNotified());
        database.taskDao().delete(added);
        assertTasks(expected);
    }

    private void assertTasks(List<TaskEntity> expected) {
        assertEquals(expected.size(), database.taskDao().getTaskCount());
        for (TaskEntity task : expected) {
            TaskEntity actual = database.taskDao().getRawTaskById(task.id);
            assertEquals(task, actual);
            assertFalse(actual.isWasNotified());
        }
    }

    private static boolean hasTable(SQLiteDatabase raw, String name) {
        try (Cursor cursor = raw.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                new String[]{name})) { return cursor.moveToFirst(); }
    }

    private static List<TaskEntity> tasks(int quant) {
        return Arrays.asList(task(7, "Полить цветы — тест", 0, 17, 2000, 1900, 1000, 1),
                task(11, "", 3, 2, 0, 0, 0, quant),
                task(42, "Договор", 5, 4, 1700000000000L, 1699999000000L, 1699500000000L, quant == 1 ? 1 : 3));
    }

    private static TaskEntity task(long id, String description, int interval, int amount,
                                   long alarm, long caution, long ack, int quant) {
        TaskEntity task = new TaskEntity(description, interval, amount, alarm, caution, ack, quant);
        task.setId(id);
        return task;
    }
}
