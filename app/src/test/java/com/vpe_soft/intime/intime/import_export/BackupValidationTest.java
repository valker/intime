package com.vpe_soft.intime.intime.import_export;

import static org.junit.Assert.*;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Collections;
import java.util.List;

public class BackupValidationTest {
    private static final String ROW = "[7,\"Task\",1,2,2000,1900,0,1]";

    /**
     * Разбирает все шесть допустимых interval от 0 до 5 в старом формате без meta и columns.
     * Проверяет сохранение interval, положительных amount/quant и ID. Доказывает совместимость
     * старой позиционной структуры; расчёт сроков для этих интервалов здесь не выполняется.
     */
    @Test public void legacyFilesAcceptEverySupportedInterval() throws Exception {
        for (int interval = 0; interval <= 5; interval++) {
            TaskEntity task = BackupImport.parseTasks(wrap("[7,\"Task\"," + interval + ",2,2000,1900,0,1]")).get(0);
            assertEquals(interval, task.interval.intValue());
            assertEquals(2, task.amount.intValue());
            assertEquals(1, task.quant.intValue());
            assertEquals(7L, task.id);
        }
    }

    /**
     * Экспортирует одну корректную задачу новым exporter. Проверяет meta.version = 1 и точный
     * порядок восьми columns, затем импортирует и сверяет все восемь сохранённых полей.
     * Состояние wasNotified намеренно не входит в backup: после разбора оно должно быть false.
     * Проверяет контракт JSON, без записи в Room и без публикации уведомления.
     */
    @Test public void exportedVersionAndColumnsRoundTripAllFields() throws Exception {
        TaskEntity original = new TaskEntity("Тестовая задача", 5, 2, 2000, 1900, 1000, 3);
        original.setId(7);
        original.setWasNotified(true);
        String json = BackupExport.toJson(Collections.singletonList(original));
        JSONObject root = new JSONObject(json);
        assertEquals(1, root.getJSONObject("meta").getInt("version"));
        JSONArray columns = root.getJSONObject("tables").getJSONObject("tasks").getJSONArray("columns");
        assertEquals(8, columns.length());
        String[] expected = {"id", "description", "interval", "amount", "next_alarm", "next_caution", "last_ack", "quant"};
        for (int i = 0; i < 8; i++) assertEquals(expected[i], columns.getString(i));
        TaskEntity actual = BackupImport.parseTasks(json).get(0);
        assertEquals(original, actual);
        assertFalse(actual.isWasNotified());
    }

    /**
     * В явно присутствующей meta передаёт неизвестные версии, строковую/дробную версию,
     * пустой объект, null и массив вместо объекта. Все варианты должны отклоняться, без
     * неявного перехода к legacy-v1. Также проверяет неправильный exportedAt; его отсутствие
     * при корректной версии допускается, поскольку это необязательная отметка экспорта.
     */
    @Test public void rejectsUnsupportedOrMalformedMetadata() throws Exception {
        for (String meta : new String[]{"{\"version\":0}", "{\"version\":2}", "{\"version\":\"1\"}",
                "{\"version\":1.0}", "{}", "null", "[]", "{\"version\":1,\"exportedAt\":-1}"}) {
            reject("{\"meta\":" + meta + ",\"tables\":{\"tasks\":{\"rows\":[" + ROW + "]}}}");
        }
        assertEquals(1, BackupImport.parseTasks("{\"meta\":{\"version\":1},\"tables\":{\"tasks\":{\"rows\":[" + ROW + "]}}}").size());
    }

    /**
     * Проверяет неверные контейнеры tables/tasks/rows, строку вместо строки-массива,
     * лишнее девятое поле, неверный или переставленный columns и второй JSON-объект после корня.
     * Каждый вариант должен закончиться IllegalArgumentException до создания списка задач.
     * Корректный rows = [] остаётся допустимым запросом полной очистки базы.
     */
    @Test public void rejectsAmbiguousStructureAndAcceptsExplicitEmptyRows() throws Exception {
        for (String json : new String[]{"[]", "{\"tables\":[]}", "{\"tables\":{\"tasks\":[]}}",
                "{\"tables\":{\"tasks\":{\"rows\":null}}}", wrap("\"not a row\""),
                wrap("[7,\"Task\",1,2,2000,1900,0,1,99]"),
                "{\"tables\":{\"tasks\":{\"columns\":[],\"rows\":[]}}}",
                "{\"tables\":{\"tasks\":{\"columns\":[\"description\",\"id\",\"interval\",\"amount\",\"next_alarm\",\"next_caution\",\"last_ack\",\"quant\"],\"rows\":[]}}}",
                wrap(ROW) + " {}"}) reject(json);
        assertTrue(BackupImport.parseTasks(wrap("")).isEmpty());
    }

    /**
     * Подставляет interval вне 0–5, amount/quant нулевые, отрицательные и больше int.MAX_VALUE.
     * Проверяет отклонение каждой строки и сообщение с именем неверного поля. Граничные
     * положительные int.MAX_VALUE принимаются как представимые параметры; безопасность
     * вычисления календарного срока с ними проверяется отдельно в R3.2.
     */
    @Test public void rejectsInvalidParametersWithoutIntegerTruncation() throws Exception {
        for (String[] value : new String[][]{{"2", "-1"}, {"2", "6"}, {"3", "0"}, {"3", "-1"},
                {"3", "2147483648"}, {"7", "0"}, {"7", "-1"}, {"7", "2147483648"}}) {
            rejectField(Integer.parseInt(value[0]), value[1]);
        }
        assertEquals(Integer.MAX_VALUE, BackupImport.parseTasks(wrap("[7,\"Task\",1,2147483647,2000,1900,0,2147483647]")).get(0).amount.intValue());
    }

    /**
     * Для каждого из семи числовых полей подставляет строку, дробь, целое с десятичной точкой,
     * boolean, null и число вне long. Для description проверяет число, boolean и null.
     * Все варианты отклоняются: геттеры JSON не должны преобразовать тип или усечь число.
     * Это проверка типов значений в строке, а не строгого синтаксиса JSON по RFC.
     */
    @Test public void rejectsCoercedNumericValuesAndNonStringDescriptions() throws Exception {
        for (int column : new int[]{0, 2, 3, 4, 5, 6, 7}) {
            for (String value : new String[]{"\"1\"", "1.5", "1.0", "true", "null", "9223372036854775808"}) rejectField(column, value);
        }
        for (String value : new String[]{"123", "true", "null"}) rejectField(1, value);
    }

    /**
     * Отклоняет ID = 0 (автоназначение Room), отрицательный ID, long.MAX_VALUE
     * (исчерпание AUTOINCREMENT) и две строки с одинаковым положительным ID.
     * Один большой положительный ID больше int.MAX_VALUE должен сохраняться без усечения.
     * Уникальность проверяется внутри файла, а не относительно заменяемой базы.
     */
    @Test public void rejectsInvalidAndDuplicateIdsAndPreservesLongIds() throws Exception {
        for (String value : new String[]{"0", "-1", "9223372036854775807"}) rejectField(0, value);
        reject(wrap(ROW + "," + ROW));
        assertEquals(4294967296L, BackupImport.parseTasks(wrap("[4294967296,\"Task\",1,2,2000,1900,0,1]")).get(0).id);
    }

    /**
     * Проверяет отказ для отрицательных трёх временных полей и сохранение нулевых значений.
     * Поля должны быть неотрицательными целыми Unix-миллисекундами в пределах long.
     * Отношения lastAck/caution/alarm и дальнейшая календарная арифметика не проверяются.
     */
    @Test public void rejectsNegativeTimestampsAndAcceptsZero() throws Exception {
        for (int column : new int[]{4, 5, 6}) rejectField(column, "-1");
        List<TaskEntity> tasks = BackupImport.parseTasks(wrap("[7,\"Task\",1,2,0,0,0,1]"));
        assertEquals(0L, tasks.get(0).nextAlarm.longValue());
        assertEquals(0L, tasks.get(0).nextCaution.longValue());
        assertEquals(0L, tasks.get(0).lastAck.longValue());
    }

    private void rejectField(int column, String value) throws Exception {
        String[] fields = {"7", "\"Task\"", "1", "2", "2000", "1900", "0", "1"};
        fields[column] = value;
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BackupImport.parseTasks(wrap("[" + String.join(",", fields) + "]")));
        assertTrue(error.getMessage(), error.getMessage().contains(BackupImport.COLUMNS[column]));
    }
    private static String wrap(String rows) { return "{\"tables\":{\"tasks\":{\"rows\":[" + rows + "]}}}"; }
    private static void reject(String json) { assertThrows(IllegalArgumentException.class, () -> BackupImport.parseTasks(json)); }
}
