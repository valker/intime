package com.vpe_soft.intime.intime.import_export;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.SQLException;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

@RunWith(AndroidJUnit4.class)
public class ImportTransactionPlatformTest {
    private AppDatabase database;
    private TaskEntity first, second;

    @Before public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        first = task(7, "Старая первая", 1, 2, 5000, 4500, 1000, 2);
        first.setWasNotified(true);
        second = task(22, "Старая вторая", 2, 3, 6000, 5500, 2000, 3);
        database.taskDao().insertAll(Arrays.asList(first, second));
    }

    @After public void tearDown() { database.close(); }

    /**
     * На Android создаёт две старые записи в отдельной Room-базе в памяти и AFTER INSERT-триггер,
     * вызывающий RAISE(ABORT) для второй строки корректного backup. Ожидает SQLException с
     * заданным сообщением: после ошибки удаления/вставки должны откатиться, восемь полей старых
     * задач и wasNotified сохраниться, новой строки ID 9 быть не должно. После удаления триггера
     * повторяет импорт и проверяет полную замену. Проверяет настоящие SQLite/Room-транзакции,
     * без production-базы, AlarmManager, callback репозитория и сбоя процесса/commit.
     */
    @Test public void nativeSecondInsertFailureRollsBackAndAllowsRetry() throws Exception {
        List<TaskEntity> imported = replacement();
        String json = BackupExport.toJson(imported);
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_import AFTER INSERT ON tasks WHEN NEW.id = 9 " +
                "BEGIN SELECT RAISE(ABORT, 'forced second insert failure'); END");
        SQLException error = assertThrows(SQLException.class, () -> ImportReplacement.replaceAll(database, json));
        assertTrue(error.toString(), error.getMessage().contains("forced second insert failure"));
        assertEquals(2, database.taskDao().getTaskCount());
        assertEquals(first, database.taskDao().getRawTaskById(7));
        assertEquals(second, database.taskDao().getRawTaskById(22));
        assertTrue(database.taskDao().getRawTaskById(7).isWasNotified());
        assertFalse(database.taskDao().getRawTaskById(22).isWasNotified());
        assertNull(database.taskDao().getRawTaskById(9));
        database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER reject_import");
        ImportReplacement.replaceAll(database, json);
        assertReplacement(imported);
    }

    /**
     * На Android импортирует две новые записи с разными параметрами и временами вместо двух
     * старых, переиспользуя ID 7. Проверяет точное число строк, все восемь сохранённых полей,
     * отсутствие старого ID 22 и wasNotified = false у каждой новой задачи. Затем импортирует
     * пустой rows и ожидает полностью пустую базу. Используется исключительно база в памяти;
     * модель расписания проверяется отдельными локальными сценариями, UI здесь не запускается.
     */
    @Test public void nativeSuccessfulImportReplacesAllFieldsAndEmptyBackupClearsRows() throws Exception {
        List<TaskEntity> imported = replacement();
        ImportReplacement.replaceAll(database, BackupExport.toJson(imported));
        assertReplacement(imported);
        ImportReplacement.replaceAll(database, BackupExport.toJson(Collections.emptyList()));
        assertEquals(0, database.taskDao().getTaskCount());
        assertTrue(database.taskDao().getAllTasksSync().isEmpty());
    }

    /**
     * В отдельной Android Room-базе в памяти уже есть две старые задачи ID 7/22,
     * первая отмечена уведомлённой. Импортирует неизменённый JSON, экспортированный
     * Play APK 25 из двух синтетических UI-задач. Проверяет полную замену списка,
     * отсутствие старых ID, все восемь полей/ID 1/2 и wasNotified=false для обеих
     * новых строк. Используется реальная SQLite/Room-транзакция; личная база,
     * SAF, уведомления и AlarmManager этой проверкой не затрагиваются.
     */
    @Test public void nativePlay25ExportReplacesTasksPreservingAllFields() throws Exception {
        ImportReplacement.replaceAll(database, play25Fixture());
        TaskEntity expectedFirst = task(1, "PlayUpgrade-First-101310", 1, 1,
                1791533596920L, 1791533416920L, 0, 1);
        TaskEntity expectedSecond = task(2, "PlayUpgrade-Second-101310", 2, 1,
                1791616404952L, 1791612084952L, 0, 1);
        assertEquals(2, database.taskDao().getTaskCount());
        assertNull(database.taskDao().getRawTaskById(7));
        assertNull(database.taskDao().getRawTaskById(22));
        assertEquals(expectedFirst, database.taskDao().getRawTaskById(1));
        assertEquals(expectedSecond, database.taskDao().getRawTaskById(2));
        assertFalse(database.taskDao().getRawTaskById(1).isWasNotified());
        assertFalse(database.taskDao().getRawTaskById(2).isWasNotified());
    }

    /**
     * При двух существующих задачах ID 7/22 меняет только backup_version старой
     * Play-фикстуры на неизвестную 2 и вызывает транзакционный импорт. Ожидает
     * IllegalArgumentException, прежнее количество и все поля обеих старых строк,
     * прежние wasNotified и отсутствие новых ID 1/2. Проверяет отказ до замены
     * реальной Android Room-базы в памяти, без UI, расписания и пользовательских данных.
     */
    @Test public void nativeUnsupportedPlayBackupKeepsExistingTasks() throws Exception {
        JSONObject backup = new JSONObject(play25Fixture());
        backup.getJSONObject("meta").put("backup_version", 2);
        assertThrows(IllegalArgumentException.class,
                () -> ImportReplacement.replaceAll(database, backup.toString()));
        assertEquals(2, database.taskDao().getTaskCount());
        assertEquals(first, database.taskDao().getRawTaskById(7));
        assertEquals(second, database.taskDao().getRawTaskById(22));
        assertTrue(database.taskDao().getRawTaskById(7).isWasNotified());
        assertFalse(database.taskDao().getRawTaskById(22).isWasNotified());
        assertNull(database.taskDao().getRawTaskById(1));
        assertNull(database.taskDao().getRawTaskById(2));
    }

    private String play25Fixture() throws Exception {
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("play25-backup.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int size;
            while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private List<TaskEntity> replacement() {
        return Arrays.asList(task(7, "Новая с прежним ID", 4, 2, 9000, 8000, 3000, 4),
                task(9, "Другая новая", 0, 5, 7000, 6500, 4000, 2));
    }

    private void assertReplacement(List<TaskEntity> imported) {
        assertEquals(imported.size(), database.taskDao().getTaskCount());
        assertNull(database.taskDao().getRawTaskById(22));
        for (TaskEntity expected : imported) {
            TaskEntity actual = database.taskDao().getRawTaskById(expected.id);
            assertEquals(expected, actual);
            assertFalse(actual.isWasNotified());
        }
    }

    private static TaskEntity task(long id, String description, int interval, int amount,
                                   long alarm, long caution, long ack, int quant) {
        TaskEntity task = new TaskEntity(description, interval, amount, alarm, caution, ack, quant);
        task.setId(id);
        return task;
    }
}
