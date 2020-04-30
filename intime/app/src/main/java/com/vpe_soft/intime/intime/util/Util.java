package com.vpe_soft.intime.intime.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;

import com.vpe_soft.intime.intime.database.InTimeOpenHelper;
import com.vpe_soft.intime.intime.database.Task;
import com.vpe_soft.intime.intime.receiver.AlarmReceiver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by Valentin on 27.08.2015.
 */

/**
 * Modified by kylichist on 9.12.2019.
 */

public class Util {

    private static final String TAG = "Util";

    public static final String SKELETON = "jjmm ddMMyyyy";

    public static final String TASK_TABLE = "main.tasks";
    public static final String TASK_OVERDUE_ACTION = "com.vpe_soft.intime.intime.TaskOverdue";
    public static final String NOTIFICATION_TAG = "com.vpe_soft.intime.intime.NotificationTag";

    private static final int[] fields= new int[]{
        Calendar.MINUTE,
        Calendar.HOUR,
        Calendar.DAY_OF_YEAR,
        Calendar.WEEK_OF_YEAR,
        Calendar.MONTH,
        Calendar.FIELD_COUNT //substitute for YEAR
    };

    public static long getNextAlarm(int interval, int amount, long currentTimeMillis, Locale locale) {
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

    public static Cursor createCursor(Context context) {
        SQLiteDatabase database = Util.getReadableDatabaseFromContext(context);
        return database.query(Util.TASK_TABLE,new String[]{"description", "id AS _id", "next_alarm", "next_caution"}, null, null, null, null, "next_alarm");
    }

    public static String getDateFromNextAlarm(Locale locale, long nextAlarm){
        Date date = new Date(nextAlarm);
        String pattern = DateFormat.getBestDateTimePattern(locale, SKELETON);
        SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(date);
    }

    public static Typeface getTypeface(Context context){
        return Typeface.createFromAsset(context.getAssets(),"font/font.ttf");
    }

    public static int getDatabaseLengthFromContext(Context context){
        SQLiteDatabase database = getReadableDatabaseFromContext(context);
        long length = DatabaseUtils.queryNumEntries(database, TASK_TABLE);
        database.close();
        return (int) length;
    }

    public static int getDatabaseLength(SQLiteDatabase database){
        long length = DatabaseUtils.queryNumEntries(database, TASK_TABLE);
        return (int) length;
    }

    public static long getId(Context context, int position) {
        Cursor cursor = createCursor(context);
        cursor.moveToPosition(position);
        return cursor.getLong(cursor.getColumnIndex("_id"));
    }

    public static Task findTaskById(Context context, long id) {
        Log.d(TAG, "findTaskById");
        //next line may cause an error (not checked yet)
        try (Cursor query = getReadableDatabaseFromContext(context).query(TASK_TABLE, new String[]{"description", "interval", "amount", "next_alarm", "next_caution", "last_ack"}, "id=" + id, null, null, null, null, "1")) {
            if (query.moveToNext()) {
                Log.d(TAG, "findTaskById: task was found");
                String description = query.getString(query.getColumnIndexOrThrow("description"));
                int interval = query.getInt(query.getColumnIndexOrThrow("interval"));
                int amount = query.getInt(query.getColumnIndexOrThrow("amount"));
                long nextAlarm = query.getLong(query.getColumnIndexOrThrow("next_alarm"));
                long nextCaution = query.getLong(query.getColumnIndexOrThrow("next_caution"));
                long lastAck = query.getLong(query.getColumnIndexOrThrow("last_ack"));
                return new Task(description, interval, amount, nextAlarm, nextCaution, lastAck);
            } else {
                Log.d(TAG, "findTaskById: task not found");
            }
        }

        return null;
    }

    public static Task[] getTasksFromDatabase(Context context){
        SQLiteDatabase database = getReadableDatabaseFromContext(context);
        int length = getDatabaseLength(database);
        Task[] tasks = new Task[length];
        if(length != 0){
            for(int a = 1; a <= length; a++){
                try (Cursor query = database.query(TASK_TABLE, new String[]{"description", "interval", "amount", "next_alarm", "next_caution", "last_ack"}, "id=" + a, null, null, null, null, "1")) {
                    if (query.moveToNext()) {
                        Log.d(TAG, "findTaskById: task was found");
                        String description = query.getString(query.getColumnIndexOrThrow("description"));
                        int interval = query.getInt(query.getColumnIndexOrThrow("interval"));
                        int amount = query.getInt(query.getColumnIndexOrThrow("amount"));
                        long nextAlarm = query.getLong(query.getColumnIndexOrThrow("next_alarm"));
                        long nextCaution = query.getLong(query.getColumnIndexOrThrow("next_caution"));
                        long lastAck = query.getLong(query.getColumnIndexOrThrow("last_ack"));
                        tasks[a - 1] = new Task(description, interval, amount, nextAlarm, nextCaution, lastAck);
                    }
                }
            }
        } else {
            Log.d(TAG, "findTaskById: task not found");
        }
        return tasks;
    }

    public static SQLiteDatabase getReadableDatabaseFromContext(Context context){
        InTimeOpenHelper helper = new InTimeOpenHelper(context);
        return helper.getReadableDatabase();
    }

    public static SQLiteDatabase getWritableDatabaseFromContext(Context context){
        InTimeOpenHelper helper = new InTimeOpenHelper(context);
        return helper.getWritableDatabase();
    }

    public static void setupAlarmIfRequired(Context context) {
        Log.d(TAG, "setupAlarmIfRequired");
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        InTimeOpenHelper openHelper = new InTimeOpenHelper(context);
        try (SQLiteDatabase database = openHelper.getReadableDatabase()) {
            final long currentTimestamp = System.currentTimeMillis();
            try (Cursor next_alarm = database.query(TASK_TABLE, new String[]{"id", "next_alarm", "description"}, "next_alarm>" + currentTimestamp, null, null, null, "next_alarm", "1")) {
                if (next_alarm.moveToNext()) {
                    Log.d(TAG, "setupAlarmIfRequired: task was found. going to setup alarm");
                    long nextAlarm = next_alarm.getLong(next_alarm.getColumnIndexOrThrow("next_alarm"));
                    if(Build.VERSION.SDK_INT>=23) {
                        alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                nextAlarm,
                                createPendingIntent(
                                        context,
                                        next_alarm.getString(next_alarm.getColumnIndexOrThrow("description"))));
                    }else {
                        alarmManager.set(
                                AlarmManager.RTC_WAKEUP,
                                nextAlarm,
                                createPendingIntent(
                                        context,
                                        next_alarm.getString(next_alarm.getColumnIndexOrThrow("description"))));
                    }
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