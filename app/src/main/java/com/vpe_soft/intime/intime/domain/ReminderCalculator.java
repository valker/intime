package com.vpe_soft.intime.intime.domain;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

public class ReminderCalculator {
    public static final int INTERVAL_MINUTE = 0;
    public static final int INTERVAL_HOUR = 1;
    public static final int INTERVAL_DAY = 2;
    public static final int INTERVAL_WEEK = 3;
    public static final int INTERVAL_MONTH = 4;
    public static final int INTERVAL_YEAR = 5;

    private static final int[] CALENDAR_FIELDS = new int[]{
            Calendar.MINUTE,
            Calendar.HOUR,
            Calendar.DAY_OF_YEAR,
            Calendar.WEEK_OF_YEAR,
            Calendar.MONTH,
            Calendar.FIELD_COUNT // substitute for YEAR
    };

    private ReminderCalculator() {
    }

    public static ReminderTimes getNextAlarmAndCaution(
            int interval,
            int amount,
            long acknowledgementTime,
            int quant,
            Locale locale
    ) {
        long nextAlarm = getNextAlarm(interval, amount, acknowledgementTime, quant, locale);
        long cautionPeriod = (long) ((nextAlarm - acknowledgementTime) * 0.95);
        long nextCaution = acknowledgementTime + cautionPeriod;
        return new ReminderTimes(nextAlarm, nextCaution);
    }

    public static long getNextAlarm(
            int interval,
            int amount,
            long acknowledgementTime,
            int quant,
            Locale locale
    ) {
        validateInput(interval, amount, quant);

        Calendar calendar = new GregorianCalendar(locale);
        calendar.setTime(new Date(acknowledgementTime));

        int field = CALENDAR_FIELDS[interval];
        if (field == Calendar.FIELD_COUNT) {
            field = Calendar.MONTH;
            amount = amount * 12;
        }

        calendar.add(field, amount);
        long fullIntervalEnd = calendar.getTime().getTime();
        long quantizedInterval = (fullIntervalEnd - acknowledgementTime) / quant;
        return acknowledgementTime + quantizedInterval;
    }

    private static void validateInput(int interval, int amount, int quant) {
        if (interval < 0 || interval >= CALENDAR_FIELDS.length) {
            throw new IllegalArgumentException("Unknown interval: " + interval);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (quant <= 0) {
            throw new IllegalArgumentException("Quant must be positive");
        }
    }

    public static class ReminderTimes {
        public final long nextAlarm;
        public final long nextCaution;

        public ReminderTimes(long nextAlarm, long nextCaution) {
            this.nextAlarm = nextAlarm;
            this.nextCaution = nextCaution;
        }
    }
}
