package com.vpe_soft.intime.intime.import_export;

import static org.junit.Assert.*;
import com.vpe_soft.intime.intime.domain.ReminderCalculator;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import org.junit.Test;

public class BackupBoundariesTest {
    /**
     * Передаёт формально положительные int-параметры, для которых нельзя вычислить безопасный
     * следующий ACK: огромные годы/недели/часы и quant, обнуляющий минутный интервал.
     * Также проверяет последний поддерживаемый lastAck, от которого нельзя продвинуться.
     * Ожидает отказ с номером строки и пояснением о невозможном расчёте, до записи в Room.
     */
    @Test public void rejectsRepresentableButUncomputableSchedules() {
        for (String row : new String[]{
                "[7,\"Years\",5,2147483647,2000,1900,0,1]",
                "[7,\"Weeks\",3,2147483647,2000,1900,0,1]",
                "[7,\"Hours\",1,2147483647,2000,1900,0,1]",
                "[7,\"Tiny\",0,1,2000,1900,0,60001]",
                "[7,\"Last anchor\",0,1,2000,1900," + ReminderCalculator.MAX_SUPPORTED_TIME + ",1]"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> BackupImport.parseTasks(wrap(row)));
            assertTrue(error.getMessage().contains("Row 0"));
            assertTrue(error.getMessage().contains("uncomputable schedule"));
        }
    }

    /**
     * Подставляет миллисекунду после 9999 года во все три операционные временные поля.
     * Каждый файл должен отклоняться с именем поля. При next_alarm на самой верхней границе
     * и lastAck = 0 импорт должен сохранить срок без пересчёта, используя безопасные параметры.
     * Время exportedAt не участвует в расписании и здесь не проверяется.
     */
    @Test public void rejectsOutOfRangeStoredDatesAndPreservesSupportedDeadline() throws Exception {
        long maximum = ReminderCalculator.MAX_SUPPORTED_TIME;
        for (int column : new int[]{4, 5, 6}) {
            String[] fields = {"7", "\"Task\"", "0", "1", "2000", "1900", "0", "1"};
            fields[column] = Long.toString(maximum + 1);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> BackupImport.parseTasks(wrap("[" + String.join(",", fields) + "]")));
            assertTrue(error.getMessage().contains(BackupImport.COLUMNS[column]));
        }
        TaskEntity task = BackupImport.parseTasks(wrap("[7,\"Task\",0,1," + maximum + ",1900,0,1]")).get(0);
        assertEquals(maximum, task.nextAlarm.longValue());
        assertEquals(1900L, task.nextCaution.longValue());
        assertEquals(0L, task.lastAck.longValue());
    }

    private static String wrap(String row) { return "{\"tables\":{\"tasks\":{\"rows\":[" + row + "]}}}"; }
}
