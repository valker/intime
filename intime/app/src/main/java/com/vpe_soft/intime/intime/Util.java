package com.vpe_soft.intime.intime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Created by Valentin on 27.08.2015.
 */
class Util {

    private static final String TAG = "Util";

    static final String TASK_TABLE = "main.tasks";
    static final String NEXT_ALARM_FIELD = "next_alarm";
    static final String TASK_OVERDUE_ACTION = "com.vpe_soft.intime.intime.TaskOverdue";
    static final String NOTIFICATION_TAG = "com.vpe_soft.intime.intime.NotificationTag";

    private static final int[] fields= new int[]{
        Calendar.MINUTE,
        Calendar.HOUR,
        Calendar.DAY_OF_YEAR,
        Calendar.WEEK_OF_YEAR,
        Calendar.MONTH,
        Calendar.FIELD_COUNT // substitute for YEAR
    };

    static long getNextAlarm(int interval, int amount, long currentTimeMillis, Locale locale) {
        Log.d(TAG, "getNextAlarm");
        Date date = new Date(currentTimeMillis);
        Calendar calendar = new GregorianCalendar(locale);
        calendar.setTime(date);
        int field = fields[interval];
        if(field == Calendar.FIELD_COUNT) {
            // YEAR is not supported by calendar.add, so emulate it as 12 months
            field = Calendar.MONTH;
            amount = amount * 12;
        }
        //noinspection ResourceType
        calendar.add(field, amount);
        date = calendar.getTime();
        return date.getTime();
    }

    public static TaskInfo findTaskById(SQLiteDatabase database, long id) {
        Log.d(TAG, "findTaskById");
        try (Cursor query = database.query(TASK_TABLE, new String[]{"description", "interval", "amount", NEXT_ALARM_FIELD, "next_caution", "last_ack"}, "id=" + id, null, null, null, null, "1")) {
            if (query.moveToNext()) {
                Log.d(TAG, "findTaskById: task was found");
                String description = query.getString(query.getColumnIndexOrThrow("description"));
                int interval = query.getInt(query.getColumnIndexOrThrow("interval"));
                int amount = query.getInt(query.getColumnIndexOrThrow("amount"));
                long nextAlarm = query.getLong(query.getColumnIndexOrThrow(NEXT_ALARM_FIELD));
                long nextCaution = query.getLong(query.getColumnIndexOrThrow("next_caution"));
                long lastAck = query.getLong(query.getColumnIndexOrThrow("last_ack"));
                return new TaskInfo(id, description, interval, amount, nextAlarm, nextCaution, lastAck);
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
            try (Cursor next_alarm = database.query(TASK_TABLE, new String[]{"id", NEXT_ALARM_FIELD, "description"}, "next_alarm>?", new String[]{String.valueOf(currentTimestamp)}, null, null, NEXT_ALARM_FIELD, "1")) {
                if (next_alarm.moveToNext()) {
                    Log.d(TAG, "setupAlarmIfRequired: task was found. going to setup alarm");
                    long nextAlarm = next_alarm.getLong(next_alarm.getColumnIndexOrThrow(NEXT_ALARM_FIELD));
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

    public static long getNumberOfOverDueTasks(Context context, long currentTimeMillis) {
        try (SQLiteDatabase database = new InTimeOpenHelper(context).getReadableDatabase()) {
            long rowsCount = DatabaseUtils.queryNumEntries(
                    database,
                    TASK_TABLE,
                    NEXT_ALARM_FIELD+"<?",
                    new String[]{String.valueOf(currentTimeMillis)});
            return rowsCount;
        }
    }

    public static long getNumberOfSkippedTasks(Context context, long lastUsageTimestamp, long currentTimestamp) {
        try (SQLiteDatabase database = new InTimeOpenHelper(context).getReadableDatabase()) {
            long tasksCount = DatabaseUtils.queryNumEntries(
                    database,
                    "tasks",
                    NEXT_ALARM_FIELD + ">?" + " AND " + NEXT_ALARM_FIELD + "<?",
                    new String[]{Long.toString(lastUsageTimestamp), Long.toString(currentTimestamp)});
            return tasksCount;
        }
    }
}
