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
