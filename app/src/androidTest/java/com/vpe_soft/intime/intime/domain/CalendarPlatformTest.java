package com.vpe_soft.intime.intime.domain;

import static org.junit.Assert.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.vpe_soft.intime.intime.import_export.BackupImport;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class CalendarPlatformTest {
    /**
     * На настоящем Android проверяет Calendar/TimeZone, которые могут отличаться от JVM:
     * високосный год превращается в 28 февраля следующего года, календарный день через DST
     * в New York длится 23 часа с сохранением полудня. Также проверяет последнюю допустимую
     * минуту, допустимый местный 10000 год в UTC+14, отказ для максимальных int-лет и
     * quant с нулевым результатом. Используются явные UTC/New_York/UTC+14 без изменения
     * часового пояса устройства; alarm не публикуется.
     */
    @Test public void androidCalendarPreservesBoundariesAndRejectsUnsafeCalculations() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        assertEquals(millis(2025, Calendar.FEBRUARY, 28, utc), next(5, 1, millis(2024, Calendar.FEBRUARY, 29, utc), 1, utc));
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        long spring = millis(2026, Calendar.MARCH, 7, ny);
        assertEquals(millis(2026, Calendar.MARCH, 8, ny), next(2, 1, spring, 1, ny));
        assertEquals(23 * 3600000L, next(2, 1, spring, 1, ny) - spring);
        long maximum = ReminderCalculator.MAX_SUPPORTED_TIME;
        assertEquals(maximum, next(0, 1, maximum - 60000, 1, utc));
        assertEquals(maximum - 3540000, next(0, 1, maximum - 3600000, 1, TimeZone.getTimeZone("GMT+14:00")));
        assertThrows(IllegalArgumentException.class, () -> next(5, Integer.MAX_VALUE, spring, 1, utc));
        assertThrows(IllegalArgumentException.class, () -> next(0, 1, spring, 60001, utc));
    }

    /**
     * Запускает BackupImport с Android-реализацией org.json: целая по значению дробь 1.0,
     * огромный годовой интервал, дата за пределом 9999 года и слишком большой quant
     * должны отклоняться. Обычный файл без meta принимается с сохранением ID и прошлого срока.
     * Проверяет согласованность Android/JVM-валидации, без чтения или записи базы устройства.
     */
    @Test public void androidJsonRejectsUnsafeParametersAndKeepsLegacyCompatibility() throws Exception {
        for (String row : new String[]{"[7,\"Task\",1,1.0,2000,1900,0,1]",
                "[7,\"Task\",5,2147483647,2000,1900,0,1]",
                "[7,\"Task\",0,1,253402300800000,1900,0,1]",
                "[7,\"Task\",0,1,2000,1900,0,60001]"}) {
            assertThrows(IllegalArgumentException.class, () -> BackupImport.parseTasks(wrap(row)));
        }
        com.vpe_soft.intime.intime.database.entities.TaskEntity task = BackupImport.parseTasks(wrap("[7,\"Task\",1,1,2000,1900,0,1]")).get(0);
        assertEquals(7, task.id);
        assertEquals(2000, task.nextAlarm.longValue());
    }

    private static long next(int interval, int amount, long anchor, int quant, TimeZone zone) {
        return ReminderCalculator.getNextAlarm(interval, amount, anchor, quant, Locale.US, zone);
    }
    private static long millis(int year, int month, int day, TimeZone zone) {
        GregorianCalendar calendar = new GregorianCalendar(zone, Locale.US);
        calendar.clear();
        calendar.set(year, month, day, 12, 0);
        return calendar.getTimeInMillis();
    }
    private static String wrap(String row) { return "{\"tables\":{\"tasks\":{\"rows\":[" + row + "]}}}"; }
}
