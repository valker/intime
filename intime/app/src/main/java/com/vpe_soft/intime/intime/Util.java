package com.vpe_soft.intime.intime;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Created by Valentin on 27.08.2015.
 */
public class Util {

    public final static String TASK_TABLE = "main.tasks";
    public static final String TASK_OVERDUE_ACTION = "com.vpe_soft.intime.intime.TaskOverdue";
    public static final String NOTIFICATION_TAG = "com.vpe_soft.intime.intime.NotificationTag";

    private static final int[] fields= new int[]{
        Calendar.MINUTE,
        Calendar.HOUR,
        Calendar.DAY_OF_YEAR,
        Calendar.WEEK_OF_YEAR,
        Calendar.MONTH
    };

    static long getNextAlarm(int interval, int amount, long currentTimeMillis, Locale locale) {
        Date date = new Date(currentTimeMillis);
        Calendar calendar = new GregorianCalendar(locale);
        calendar.setTime(date);
        final int field = fields[interval];
        //noinspection ResourceType
        calendar.add(field, amount);
        date = calendar.getTime();
        final long time = date.getTime();
        final long value = time;
        return value;
    }

    public static TaskInfo findTaskById(SQLiteDatabase database, long id) {
        final Cursor query = database.query(TASK_TABLE, new String[]{"description", "interval", "amount", "next_alarm"}, "id=" + id, null, null, null, null, "1");
        try {
            if (query.moveToNext()) {
                String description = query.getString(query.getColumnIndexOrThrow("description"));
                int interval = query.getInt(query.getColumnIndexOrThrow("interval"));
                int amount = query.getInt(query.getColumnIndexOrThrow("amount"));
                long nextAlarm = query.getLong(query.getColumnIndexOrThrow("next_alarm"));
                TaskInfo taskInfo = new TaskInfo(description, interval, amount, nextAlarm);
                return taskInfo;
            }
        } finally {
            query.close();
        }

        return null;
    }
}
