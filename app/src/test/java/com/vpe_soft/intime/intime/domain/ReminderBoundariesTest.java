package com.vpe_soft.intime.intime.domain;

import static org.junit.Assert.*;
import org.junit.Test;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class ReminderBoundariesTest {
    private static final Locale LOCALE = Locale.US;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /**
     * Для каждого из шести интервалов передаёт отрицательную отметку, long.MIN/MAX_VALUE
     * и миллисекунду за концом 9999 года. Ожидает IllegalArgumentException вместо обёрнутой
     * даты. Epoch = 0 остаётся допустимым: одна минута в UTC должна дать 60000.
     * Это проверка диапазона входа, а не Android AlarmManager.
     */
    @Test public void rejectsOutOfRangeAnchorsAndAcceptsEpoch() {
        for (int interval = 0; interval <= 5; interval++) {
            int selected = interval;
            for (long anchor : new long[]{-1, Long.MIN_VALUE, Long.MAX_VALUE, ReminderCalculator.MAX_SUPPORTED_TIME + 1}) {
                assertThrows(IllegalArgumentException.class, () -> next(selected, 1, anchor, 1, UTC));
            }
        }
        assertEquals(60000, next(0, 1, 0, 1, UTC));
    }

    /**
     * Начинает за минуту до последней поддерживаемой миллисекунды и ожидает точный конец
     * 9999 года. Сдвиг начала вперёд на одну миллисекунду должен отклоняться. Проверяет
     * обе стороны границы и отсутствие переполнения календаря; часовой пояс явно UTC.
     */
    @Test public void acceptsLastRepresentableMinuteAndRejectsOneMillisecondPastLimit() {
        long last = ReminderCalculator.MAX_SUPPORTED_TIME;
        assertEquals(last, next(0, 1, last - 60000, 1, UTC));
        assertThrows(IllegalArgumentException.class, () -> next(0, 1, last - 59999, 1, UTC));
    }

    /**
     * Передаёт максимальный int в часах, днях, неделях, месяцах и годах, включая опасное
     * старое умножение лет на 12. Все расчёты от современной даты должны отклоняться.
     * Для минут такой amount всё ещё помещается в диапазон дат: ожидается точное прибавление
     * int.MAX_VALUE * 60000 в long. Проверяет отказ без усечения и допустимую большую величину.
     */
    @Test public void largeAmountsCannotWrapCalendarButLargeMinutesRemainSupported() {
        long anchor = millis(2026, Calendar.JANUARY, 1, 0, 0, UTC);
        for (int interval = 1; interval <= 5; interval++) {
            int selected = interval;
            assertThrows(IllegalArgumentException.class, () -> next(selected, Integer.MAX_VALUE, anchor, 1, UTC));
        }
        assertEquals(anchor + Integer.MAX_VALUE * 60000L, next(0, Integer.MAX_VALUE, anchor, 1, UTC));
    }

    /**
     * Делит одну минуту на 60000 частей: ожидает продвижение ровно на миллисекунду.
     * Деление на 60001 и int.MAX_VALUE должно отклоняться, а не возвращать сам ACK.
     * Проверяет обе стороны границы нулевого интервала; допустимая миллисекунда не обещает
     * такую же точность доставки Android.
     */
    @Test public void quantMustProduceAtLeastOneMillisecond() {
        assertEquals(1, next(0, 1, 0, 60000, UTC));
        assertThrows(IllegalArgumentException.class, () -> next(0, 1, 0, 60001, UTC));
        assertThrows(IllegalArgumentException.class, () -> next(0, 1, 0, Integer.MAX_VALUE, UTC));
    }

    /**
     * Для большого минутного интервала и нескольких quant сравнивает caution с независимым
     * BigInteger-расчётом floor(duration * 95 / 100). Проверяет границы ACK <= caution < alarm
     * и точность до миллисекунды без double. Часовой пояс временно фиксирует в UTC и возвращает
     * исходное значение в finally; публикация предупреждения не проверяется.
     */
    @Test public void cautionUsesExactIntegerPercentageForLargeIntervals() {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(UTC);
        try {
            long anchor = millis(2026, Calendar.JANUARY, 1, 0, 0, UTC);
            for (int quant : new int[]{1, 3, 7, 11, 97, Integer.MAX_VALUE}) {
                ReminderCalculator.ReminderTimes times = ReminderCalculator.getNextAlarmAndCaution(0, Integer.MAX_VALUE, anchor, quant, LOCALE);
                long expected = BigInteger.valueOf(times.nextAlarm - anchor).multiply(BigInteger.valueOf(95))
                        .divide(BigInteger.valueOf(100)).longValueExact();
                assertEquals(anchor + expected, times.nextCaution);
                assertTrue(times.nextCaution >= anchor);
                assertTrue(times.nextCaution < times.nextAlarm);
            }
        } finally { TimeZone.setDefault(previous); }
    }

    /**
     * Проверяет прямое календарное прибавление: 31 января + месяц в високосном и обычном году,
     * 31 января + два месяца, 29 февраля + один и четыре года, неделю через Новый год.
     * Ожидаемые даты задаются явно в UTC: не подменяются повторным вызовом самого калькулятора.
     */
    @Test public void monthsYearsAndWeeksPreserveCalendarBoundaryRules() {
        assertEquals(millis(2024, Calendar.FEBRUARY, 29, 9, 0, UTC), next(4, 1, millis(2024, Calendar.JANUARY, 31, 9, 0, UTC), 1, UTC));
        assertEquals(millis(2025, Calendar.FEBRUARY, 28, 9, 0, UTC), next(4, 1, millis(2025, Calendar.JANUARY, 31, 9, 0, UTC), 1, UTC));
        assertEquals(millis(2024, Calendar.MARCH, 31, 9, 0, UTC), next(4, 2, millis(2024, Calendar.JANUARY, 31, 9, 0, UTC), 1, UTC));
        long leap = millis(2024, Calendar.FEBRUARY, 29, 9, 0, UTC);
        assertEquals(millis(2025, Calendar.FEBRUARY, 28, 9, 0, UTC), next(5, 1, leap, 1, UTC));
        assertEquals(millis(2028, Calendar.FEBRUARY, 29, 9, 0, UTC), next(5, 4, leap, 1, UTC));
        assertEquals(millis(2027, Calendar.JANUARY, 4, 9, 0, UTC), next(3, 1, millis(2026, Calendar.DECEMBER, 28, 9, 0, UTC), 1, UTC));
    }

    /**
     * В America/New_York прибавляет календарный день через весеннюю и осеннюю смену времени
     * 2026 года. Ожидает тот же местный полдень и реальные длительности 23/25 часов.
     * Через весеннюю границу один час от 01:30 должен дать 03:30, но ровно час Unix-времени.
     * Проверяет явный часовой пояс JVM, без изменения настроек устройства.
     */
    @Test public void daylightSavingDaysKeepWallClockAndHoursKeepElapsedTime() {
        TimeZone zone = TimeZone.getTimeZone("America/New_York");
        long spring = millis(2026, Calendar.MARCH, 7, 12, 0, zone);
        long autumn = millis(2026, Calendar.OCTOBER, 31, 12, 0, zone);
        long springEnd = next(2, 1, spring, 1, zone);
        long autumnEnd = next(2, 1, autumn, 1, zone);
        assertEquals(millis(2026, Calendar.MARCH, 8, 12, 0, zone), springEnd);
        assertEquals(23 * 3600000L, springEnd - spring);
        assertEquals(millis(2026, Calendar.NOVEMBER, 1, 12, 0, zone), autumnEnd);
        assertEquals(25 * 3600000L, autumnEnd - autumn);
        long beforeGap = millis(2026, Calendar.MARCH, 8, 1, 30, zone);
        assertEquals(millis(2026, Calendar.MARCH, 8, 3, 30, zone), next(1, 1, beforeGap, 1, zone));
        assertEquals(3600000, next(1, 1, beforeGap, 1, zone) - beforeGap);
    }

    /**
     * Делит календарный день, содержащий весеннюю смену времени, на две части. Ожидает
     * половину фактических 23 часов (11 часов 30 минут), а не половину условных 24 часов.
     * Это фиксирует существующую семантику quant как деления elapsed-интервала.
     */
    @Test public void quantSplitsActualDurationAcrossDaylightSavingBoundary() {
        TimeZone zone = TimeZone.getTimeZone("America/New_York");
        long anchor = millis(2026, Calendar.MARCH, 7, 12, 0, zone);
        assertEquals(anchor + 11 * 3600000L + 30 * 60000L, next(2, 1, anchor, 2, zone));
    }

    /**
     * Берёт UTC-момент за час до конца 9999 года в поясе UTC+14, где местный календарный год
     * уже 10000. Одна минута должна приниматься с точным Unix-сроком; результат за пределом
     * поддерживаемого UTC-диапазона должен отклоняться. Проверяет UTC-границу, не вводя
     * ошибочное ограничение по местному номеру года.
     */
    @Test public void utcLimitAllowsLocalYearTenThousandInPositiveTimeZone() {
        TimeZone positive = TimeZone.getTimeZone("GMT+14:00");
        long maximum = ReminderCalculator.MAX_SUPPORTED_TIME;
        assertEquals(maximum - 3540000, next(0, 1, maximum - 3600000, 1, positive));
        assertThrows(IllegalArgumentException.class, () -> next(0, 1, maximum - 59999, 1, positive));
    }

    private static long next(int interval, int amount, long anchor, int quant, TimeZone zone) {
        return ReminderCalculator.getNextAlarm(interval, amount, anchor, quant, LOCALE, zone);
    }
    private static long millis(int year, int month, int day, int hour, int minute, TimeZone zone) {
        GregorianCalendar calendar = new GregorianCalendar(zone, LOCALE);
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar.getTimeInMillis();
    }
}
