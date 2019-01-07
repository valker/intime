package com.vpe_soft.intime.intime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Created by Valentin on 27.08.2015.
 */
public class Util {

    private static final String TAG = "Util";

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
        Log.d(TAG, "getNextAlarm");
        Date date = new Date(currentTimeMillis);
        Calendar calendar = new GregorianCalendar(locale);
        calendar.setTime(date);
        final int field = fields[interval];
        //noinspection ResourceType
        calendar.add(field, amount);
        date = calendar.getTime();
        return date.getTime();
    }

    public static TaskInfo findTaskById(SQLiteDatabase database, long id) {
        Log.d(TAG, "findTaskById");
        try (Cursor query = database.query(TASK_TABLE, new String[]{"description", "interval", "amount", "next_alarm"}, "id=" + id, null, null, null, null, "1")) {
            if (query.moveToNext()) {
                Log.d(TAG, "findTaskById: task was found");
                String description = query.getString(query.getColumnIndexOrThrow("description"));
                int interval = query.getInt(query.getColumnIndexOrThrow("interval"));
                int amount = query.getInt(query.getColumnIndexOrThrow("amount"));
                long nextAlarm = query.getLong(query.getColumnIndexOrThrow("next_alarm"));
                return new TaskInfo(description, interval, amount, nextAlarm);
            } else {
                Log.d(TAG, "findTaskById: task not found");
            }
        }

        return null;
    }

    static void setupAlarmIfRequired(Context context) {
        Log.d(TAG, "setupAlarmIfRequired");
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        InTimeOpenHelper openHelper = new InTimeOpenHelper(context);
        try (SQLiteDatabase database = openHelper.getReadableDatabase()) {
            final long currentTimestamp = System.currentTimeMillis();
            try (Cursor next_alarm = database.query(TASK_TABLE, new String[]{"id", "next_alarm", "description"}, "next_alarm>" + currentTimestamp, null, null, null, "next_alarm", "1")) {
                if (next_alarm.moveToNext()) {
                    Log.d(TAG, "setupAlarmIfRequired: task was found. going to setup alarm");
                    long nextAlarm = next_alarm.getLong(next_alarm.getColumnIndexOrThrow("next_alarm"));
                    alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            nextAlarm,
                            createPendingIntent(
                                    context,
                                    next_alarm.getString(next_alarm.getColumnIndexOrThrow("description"))));
                } else {
                    Log.d(TAG, "setupAlarmIfRequired: no task with alarm in future found");
                }
            }
        }
    }

    private static PendingIntent createPendingIntent(Context context, String taskDescription) {
        Log.d(TAG, "createPendingIntent");
        final Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("task_description", taskDescription);
        return PendingIntent.getBroadcast(context, 199709, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }
}
