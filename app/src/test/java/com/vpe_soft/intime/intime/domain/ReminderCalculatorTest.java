package com.vpe_soft.intime.intime.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class ReminderCalculatorTest {
    private static final Locale LOCALE = Locale.US;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /**
     * Проверяет: Расчёт от подтверждения 17 мая 2026 года в 10:30. При интервале два часа и quant = 1
     * ожидается напоминание в 12:30 того же дня.
     */
    @Test
    public void getNextAlarm_calculatesFromAcknowledgementTime() {
        long acknowledgementTime = utcMillis(2026, Calendar.MAY, 17, 10, 30);

        long nextAlarm = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_HOUR,
                2,
                acknowledgementTime,
                1,
                LOCALE
        );

        assertEquals(utcMillis(2026, Calendar.MAY, 17, 12, 30), nextAlarm);
    }

    /**
     * Проверяет: Деление двухчасового интервала на четыре части через quant = 4. От подтверждения в
     * 10:00 ожидается следующий срок в 10:30, через 30 минут.
     */
    @Test
    public void getNextAlarm_quantSplitsFullInterval() {
        long acknowledgementTime = utcMillis(2026, Calendar.MAY, 17, 10, 0);

        long nextAlarm = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_HOUR,
                2,
                acknowledgementTime,
                4,
                LOCALE
        );

        assertEquals(utcMillis(2026, Calendar.MAY, 17, 10, 30), nextAlarm);
    }

    /**
     * Проверяет: Календарное прибавление одного месяца к 31 января 2026 года в 09:00. Ожидается 28
     * февраля в 09:00, с ограничением дня последним днём февраля.
     */
    @Test
    public void getNextAlarm_addsMonthsUsingCalendarRules() {
        long acknowledgementTime = utcMillis(2026, Calendar.JANUARY, 31, 9, 0);

        long nextAlarm = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_MONTH,
                1,
                acknowledgementTime,
                1,
                LOCALE
        );

        assertEquals(utcMillis(2026, Calendar.FEBRUARY, 28, 9, 0), nextAlarm);
    }

    /**
     * Проверяет: Прибавление одного года к 29 февраля 2024 года в 08:15. Ожидается 28 февраля 2025
     * года в 08:15: високосная дата корректируется, время сохраняется.
     */
    @Test
    public void getNextAlarm_addsYearsAsTwelveMonths() {
        long acknowledgementTime = utcMillis(2024, Calendar.FEBRUARY, 29, 8, 15);

        long nextAlarm = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_YEAR,
                1,
                acknowledgementTime,
                1,
                LOCALE
        );

        assertEquals(utcMillis(2025, Calendar.FEBRUARY, 28, 8, 15), nextAlarm);
    }

    /**
     * Проверяет: Расчёт основного срока и предупреждения для интервала 100 минут от 10:00. Ожидаются
     * nextAlarm в 11:40 и nextCaution в 11:35, через 95% интервала.
     */
    @Test
    public void getNextAlarmAndCaution_usesNinetyFivePercentOfInterval() {
        long acknowledgementTime = utcMillis(2026, Calendar.MAY, 17, 10, 0);

        ReminderCalculator.ReminderTimes next = ReminderCalculator.getNextAlarmAndCaution(
                ReminderCalculator.INTERVAL_MINUTE,
                100,
                acknowledgementTime,
                1,
                LOCALE
        );

        assertEquals(utcMillis(2026, Calendar.MAY, 17, 11, 40), next.nextAlarm);
        assertEquals(utcMillis(2026, Calendar.MAY, 17, 11, 35), next.nextCaution);
    }

    /**
     * Проверяет: Прибавление 45 минут к подтверждению в 10:30. Ожидается 11:15 того же дня, включая
     * корректный переход через границу часа.
     */
    @Test
    public void getNextAlarm_calculatesMinuteIntervals() {
        long acknowledgementTime = utcMillis(2026, Calendar.MAY, 17, 10, 30);

        long nextAlarm = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_MINUTE,
                45,
                acknowledgementTime,
                1,
                LOCALE
        );

        assertEquals(utcMillis(2026, Calendar.MAY, 17, 11, 15), nextAlarm);
    }

    /**
     * Проверяет: Расчёт двухнедельного интервала от 17 мая 2026 года в 10:00. Ожидается 31 мая в
     * 10:00, ровно через четырнадцать календарных дней.
     */
    @Test
    public void getNextAlarm_calculatesWeekIntervals() {
        long acknowledgementTime = utcMillis(2026, Calendar.MAY, 17, 10, 0);

        long nextAlarm = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_WEEK,
                2,
                acknowledgementTime,
                1,
                LOCALE
        );

        long twoWeeksLater = utcMillis(2026, Calendar.MAY, 31, 10, 0);
        assertEquals(twoWeeksLater, nextAlarm);
    }

    /**
     * Проверяет: Прибавление трёх часов к 17 мая 2026 года в 23:00. Ожидается 18 мая в 02:00:
     * проверяется изменение даты при переходе через полночь.
     */
    @Test
    public void getNextAlarm_handlesDayTransitionCorrectly() {
        long acknowledgementTime = utcMillis(2026, Calendar.MAY, 17, 23, 0);

        long nextAlarm = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_HOUR,
                3,
                acknowledgementTime,
                1,
                LOCALE
        );

        assertEquals(utcMillis(2026, Calendar.MAY, 18, 2, 0), nextAlarm);
    }

    /**
     * Проверяет: Прибавление одного дня к 29 февраля 2024 года в 10:00. Ожидается 1 марта в 10:00, с
     * корректным переходом после високосного дня.
     */
    @Test
    public void getNextAlarm_handlesDayOfYearCorrectly_LeapYear() {
        long acknowledgementTime = utcMillis(2024, Calendar.FEBRUARY, 29, 10, 0);

        long nextAlarm = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_DAY,
                1,
                acknowledgementTime,
                1,
                LOCALE
        );

        assertEquals(utcMillis(2024, Calendar.MARCH, 1, 10, 0), nextAlarm);
    }

    /**
     * Проверяет: Прибавление одного дня к 30 ноября 2026 года в 10:00. Ожидается 1 декабря в 10:00:
     * проверяется переход к следующему месяцу.
     */
    @Test
    public void getNextAlarm_handlesDayOfYearCorrectly_EndOfMonth() {
        long acknowledgementTime = utcMillis(2026, Calendar.NOVEMBER, 30, 10, 0);

        long nextAlarm = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_DAY,
                1,
                acknowledgementTime,
                1,
                LOCALE
        );

        assertEquals(utcMillis(2026, Calendar.DECEMBER, 1, 10, 0), nextAlarm);
    }

    /**
     * Проверяет: Три отдельных вызова с некорректными параметрами: тип интервала -1, amount = 0 и
     * quant = 0. Каждый должен выбросить IllegalArgumentException вместо расчёта даты.
     */
    @Test
    public void getNextAlarm_rejectsInvalidInput() {
        long acknowledgementTime = utcMillis(2026, Calendar.MAY, 17, 10, 0);

        assertThrows(IllegalArgumentException.class, () ->
                ReminderCalculator.getNextAlarm(-1, 1, acknowledgementTime, 1, LOCALE));
        assertThrows(IllegalArgumentException.class, () ->
                ReminderCalculator.getNextAlarm(ReminderCalculator.INTERVAL_DAY, 0, acknowledgementTime, 1, LOCALE));
        assertThrows(IllegalArgumentException.class, () ->
                ReminderCalculator.getNextAlarm(ReminderCalculator.INTERVAL_DAY, 1, acknowledgementTime, 0, LOCALE));
    }

    /**
     * Проверяет: Один двухчасовой интервал от той же Unix-отметки вычисляется явно в UTC и
     * US/Eastern. Ожидается одинаковый следующий Unix-срок: часы измеряют реальную длительность.
     * Календарные дни и переходы DST проверяются отдельно в ReminderBoundariesTest.
     */
    @Test
    public void getNextAlarm_isConsistentAcrossTimeZones() {
        long acknowledgementTime = utcMillis(2026, Calendar.MAY, 17, 10, 0);

        long nextAlarmUTC = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_HOUR,
                2,
                acknowledgementTime,
                1,
                LOCALE, UTC
        );

        TimeZone estZone = TimeZone.getTimeZone("US/Eastern");
        long nextAlarmEST = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_HOUR,
                2,
                acknowledgementTime,
                1,
                Locale.US, estZone
        );

        assertEquals(nextAlarmUTC, nextAlarmEST);
    }

    private static long utcMillis(int year, int month, int day, int hour, int minute) {
        GregorianCalendar calendar = new GregorianCalendar(UTC, LOCALE);
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar.getTimeInMillis();
    }
}
