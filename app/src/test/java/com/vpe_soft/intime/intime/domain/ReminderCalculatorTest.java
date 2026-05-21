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

    @Test
    public void getNextAlarm_isConsistentAcrossTimeZones() {
        long acknowledgementTime = utcMillis(2026, Calendar.MAY, 17, 10, 0);

        long nextAlarmUTC = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_HOUR,
                2,
                acknowledgementTime,
                1,
                LOCALE
        );

        TimeZone estZone = TimeZone.getTimeZone("US/Eastern");
        long nextAlarmEST = ReminderCalculator.getNextAlarm(
                ReminderCalculator.INTERVAL_HOUR,
                2,
                acknowledgementTime,
                1,
                Locale.US
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
