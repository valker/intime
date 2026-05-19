package com.vpe_soft.intime.intime.ui;

import android.content.Context;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import java.util.concurrent.TimeUnit;

public final class RelativeTimeFormatter {

    public enum TaskDisplayState {
        OVERDUE,
        CAUTION,
        UPCOMING
    }

    private RelativeTimeFormatter() {
    }

    public static TaskDisplayState getDisplayState(TaskEntity task, long now) {
        if (task.getNextAlarm() <= now) {
            return TaskDisplayState.OVERDUE;
        }
        if (task.nextCaution != null && task.nextCaution <= now) {
            return TaskDisplayState.CAUTION;
        }
        return TaskDisplayState.UPCOMING;
    }

    public static String formatNextAlarm(Context context, long nextAlarm, long now) {
        long diffMs = nextAlarm - now;
        if (diffMs < 0) {
            return formatOverdue(context, -diffMs);
        }
        if (isSameDay(nextAlarm, now)) {
            return context.getString(R.string.relative_time_today);
        }
        if (isSameDay(nextAlarm, now + TimeUnit.DAYS.toMillis(1))) {
            return context.getString(R.string.relative_time_tomorrow);
        }

        long hours = TimeUnit.MILLISECONDS.toHours(diffMs);
        if (hours < 48) {
            return context.getResources().getQuantityString(
                    R.plurals.relative_time_in_hours,
                    (int) Math.max(1, hours),
                    (int) Math.max(1, hours)
            );
        }

        long days = TimeUnit.MILLISECONDS.toDays(diffMs);
        return context.getResources().getQuantityString(
                R.plurals.relative_time_in_days,
                (int) Math.max(1, days),
                (int) Math.max(1, days)
        );
    }

    private static String formatOverdue(Context context, long overdueMs) {
        long hours = TimeUnit.MILLISECONDS.toHours(overdueMs);
        if (hours < 48) {
            return context.getResources().getQuantityString(
                    R.plurals.relative_time_overdue_hours,
                    (int) Math.max(1, hours),
                    (int) Math.max(1, hours)
            );
        }
        long days = TimeUnit.MILLISECONDS.toDays(overdueMs);
        return context.getResources().getQuantityString(
                R.plurals.relative_time_overdue_days,
                (int) Math.max(1, days),
                (int) Math.max(1, days)
        );
    }

    private static boolean isSameDay(long timeA, long timeB) {
        java.util.Calendar a = java.util.Calendar.getInstance();
        a.setTimeInMillis(timeA);
        java.util.Calendar b = java.util.Calendar.getInstance();
        b.setTimeInMillis(timeB);
        return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)
                && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR);
    }
}
