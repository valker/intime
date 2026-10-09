package com.vpe_soft.intime.intime.import_export;

import static org.junit.Assert.*;

import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class Play25BackupImportTest {
    /**
     * Исходный файл экспортирован настоящим Play APK 25/1.1.12 на проектном API 35:
     * только две созданные тестом задачи, meta.backup_version=1, db_version=5 и created_at.
     * Передаёт неизменный файл в parseTasks, сверяет все восемь полей и ID обеих задач
     * с фиксированными ожидаемыми значениями и wasNotified=false. Проверяет поддержку
     * исторического JSON, без установки APK, записи в Room, UI или пользовательских данных.
     */
    @Test public void actualPlay25ExportPreservesEveryTaskField() throws Exception {
        assertTasks(BackupImport.parseTasks(fixture()));
    }

    /**
     * Берёт ту же корректную Play-фикстуру и по одному заменяет backup_version на
     * неизвестную, строковую или дробную версию, db_version на ноль/строку, created_at
     * на отрицательное/строку, либо добавляет второй маркер version. Каждый вариант
     * должен дать IllegalArgumentException до получения списка задач. Это проверка
     * строгих метаданных: передаётся сырой JSON без нормализации 1.0 в 1 через
     * JSONObject.toString. Неизменность базы/расписания здесь не проверяется.
     */
    @Test public void rejectsMalformedOrAmbiguousHistoricalMetadata() throws Exception {
        for (String meta : new String[]{"{\"backup_version\":2}", "{\"backup_version\":\"1\"}",
                "{\"backup_version\":1.0}", "{\"backup_version\":1,\"db_version\":0}",
                "{\"backup_version\":1,\"db_version\":\"5\"}",
                "{\"backup_version\":1,\"created_at\":-1}",
                "{\"backup_version\":1,\"created_at\":\"date\"}",
                "{\"backup_version\":1,\"version\":1}",
                "{\"backup_version\":1,\"version\":2}"}) {
            String tables = new JSONObject(fixture()).getJSONObject("tables").toString();
            String json = "{\"meta\":" + meta + ",\"tables\":" + tables + "}";
            assertThrows(meta, IllegalArgumentException.class,
                    () -> BackupImport.parseTasks(json));
        }
    }

    /**
     * В корректном историческом файле заменяет rows пустым массивом и вызывает парсер.
     * Ожидает пустой список, подтверждающий допустимость старой пустой резервной копии.
     * Полную очистку Room при импорте проверяют отдельные транзакционные тесты.
     */
    @Test public void acceptsEmptyHistoricalBackup() throws Exception {
        JSONObject root = new JSONObject(fixture());
        root.getJSONObject("tables").getJSONObject("tasks").put("rows", new org.json.JSONArray());
        assertTrue(BackupImport.parseTasks(root.toString()).isEmpty());
    }

    /**
     * Разбирает реальный синтетический экспорт Play 25, повторно экспортирует задачи
     * текущим BackupExport и импортирует новый JSON. Ожидает meta.version=1 без старого
     * backup_version, все восемь полей/ID обеих задач без изменений и wasNotified=false.
     * Проверяет переход формата, не равенство времени экспорта и не Android SAF/Room.
     */
    @Test public void historicalTasksRoundTripThroughCurrentFormat() throws Exception {
        String current = BackupExport.toJson(BackupImport.parseTasks(fixture()));
        JSONObject meta = new JSONObject(current).getJSONObject("meta");
        assertEquals(1, meta.getInt("version"));
        assertFalse(meta.has("backup_version"));
        assertTasks(BackupImport.parseTasks(current));
    }

    private static void assertTasks(List<TaskEntity> actual) {
        TaskEntity first = new TaskEntity("PlayUpgrade-First-101310", 1, 1,
                1791533596920L, 1791533416920L, 0, 1);
        first.setId(1);
        TaskEntity second = new TaskEntity("PlayUpgrade-Second-101310", 2, 1,
                1791616404952L, 1791612084952L, 0, 1);
        second.setId(2);
        assertEquals(Arrays.asList(first, second), actual);
        for (TaskEntity task : actual) assertFalse(task.isWasNotified());
    }

    private static String fixture() throws Exception {
        try (InputStream input = Play25BackupImportTest.class.getResourceAsStream("/play25-backup.json")) {
            assertNotNull("Shared Play fixture missing", input);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int size;
            while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
