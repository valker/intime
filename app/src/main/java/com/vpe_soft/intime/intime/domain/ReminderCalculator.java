package com.vpe_soft.intime.intime.domain;

import com.vpe_soft.intime.intime.Constants;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class ReminderCalculator {
    public static final int INTERVAL_MINUTE = 0;
    public static final int INTERVAL_HOUR = 1;
    public static final int INTERVAL_DAY = 2;
    public static final int INTERVAL_WEEK = 3;
    public static final int INTERVAL_MONTH = 4;
    public static final int INTERVAL_YEAR = 5;
    /** Поддерживаем Unix-миллисекунды от эпохи до 9999-12-31T23:59:59.999Z. */
    public static final long MAX_SUPPORTED_TIME = 253402300799999L;

    private static final int[] CALENDAR_FIELDS = new int[]{
            Calendar.MINUTE,
            Calendar.HOUR_OF_DAY,
            Calendar.DAY_OF_YEAR,
            Calendar.WEEK_OF_YEAR,
            Calendar.MONTH,
            Calendar.YEAR
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
        long duration = Math.subtractExact(nextAlarm, acknowledgementTime);
        // floor(95%): сначала делим, чтобы не переполнить умножение и не терять точность double.
        long cautionPeriod = Math.addExact(Math.multiplyExact(duration / 100, Constants.CAUTION_PERCENT),
                (duration % 100) * Constants.CAUTION_PERCENT / 100);
        long nextCaution = Math.addExact(acknowledgementTime, cautionPeriod);
        return new ReminderTimes(nextAlarm, nextCaution);
    }

    public static long getNextAlarm(
            int interval,
            int amount,
            long acknowledgementTime,
            int quant,
            Locale locale
    ) {
        return getNextAlarm(interval, amount, acknowledgementTime, quant, locale, TimeZone.getDefault());
    }

    /** Явный часовой пояс нужен для воспроизводимых расчётов календарных и DST-границ. */
    public static long getNextAlarm(int interval, int amount, long acknowledgementTime,
                                    int quant, Locale locale, TimeZone timeZone) {
        validateInput(interval, amount, quant);
        if (acknowledgementTime < 0 || acknowledgementTime > MAX_SUPPORTED_TIME) {
            throw new IllegalArgumentException("Acknowledgement time is outside supported dates");
        }

        Calendar calendar = new GregorianCalendar(timeZone, locale);
        calendar.setTimeInMillis(acknowledgementTime);

        int field = CALENDAR_FIELDS[interval];
        // Ограничиваем аргумент ДО Calendar.add: его внутренние int-операции также
        // могут переполниться. Грубая верхняя граница оставляет запас для переходов DST.
        // Последние часы UTC-9999 в UTC+14 уже относятся к местному 10000 году.
        // Окончательную границу проверяем по Unix-времени после Calendar.add.
        long yearsLeft = Math.max(1L, 10000L - calendar.get(Calendar.YEAR));
        long daysLeft = yearsLeft * 366;
        long maximumAmount;
        switch (interval) {
            case INTERVAL_YEAR: maximumAmount = yearsLeft; break;
            case INTERVAL_MONTH: maximumAmount = yearsLeft * 12; break;
            case INTERVAL_WEEK: maximumAmount = daysLeft / 7; break;
            case INTERVAL_DAY: maximumAmount = daysLeft; break;
            case INTERVAL_HOUR: maximumAmount = daysLeft * 25; break;
            default: maximumAmount = daysLeft * 25 * 60;
        }
        if (amount > maximumAmount) throw new IllegalArgumentException("Interval exceeds supported dates");

        calendar.add(field, amount);
        long fullIntervalEnd = calendar.getTimeInMillis();
        if (fullIntervalEnd <= acknowledgementTime || fullIntervalEnd > MAX_SUPPORTED_TIME) {
            throw new IllegalArgumentException("Calculated interval exceeds supported dates");
        }
        long quantizedInterval = Math.subtractExact(fullIntervalEnd, acknowledgementTime) / quant;
        if (quantizedInterval == 0) throw new IllegalArgumentException("Quant produces an interval below one millisecond");
        return Math.addExact(acknowledgementTime, quantizedInterval);
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
