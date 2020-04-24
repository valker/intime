package com.vpe_soft.intime.intime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.text.ChoiceFormat;
import java.text.MessageFormat;
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
    static final String DESCRIPTION_FIELD = "description";
    static final String INTERVAL_FIELD = "interval";
    static final String AMOUNT_FIELD = "amount";
    static final String NEXT_CAUTION_FIELD = "next_caution";
    static final String LAST_ACK_FIELD = "last_ack";
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
        try (Cursor query = database.query(
                TASK_TABLE,
                new String[]{DESCRIPTION_FIELD, INTERVAL_FIELD, AMOUNT_FIELD, NEXT_ALARM_FIELD, NEXT_CAUTION_FIELD, LAST_ACK_FIELD},
                "id=?",
                new String[]{Long.toString(id)},
                null, null, null, "1")) {
            if (query.moveToNext()) {
                Log.d(TAG, "findTaskById: task was found");
                String description = query.getString(query.getColumnIndexOrThrow(DESCRIPTION_FIELD));
                int interval = query.getInt(query.getColumnIndexOrThrow(INTERVAL_FIELD));
                int amount = query.getInt(query.getColumnIndexOrThrow(AMOUNT_FIELD));
                long nextAlarm = query.getLong(query.getColumnIndexOrThrow(NEXT_ALARM_FIELD));
                long nextCaution = query.getLong(query.getColumnIndexOrThrow(NEXT_CAUTION_FIELD));
                long lastAck = query.getLong(query.getColumnIndexOrThrow(LAST_ACK_FIELD));
                return new TaskInfo(id, description, interval, amount, nextAlarm, nextCaution, lastAck);
            } else {
                Log.d(TAG, "findTaskById: task not found");
            }
        }

        return null;
    }

    static void setupAlarmIfRequired(Context context) {
        Log.d(TAG, "setupAlarmIfRequired");
        NextTaskInfo nextAlarm = getNextAlarmTask(context, System.currentTimeMillis());
        if (nextAlarm != null) {
            Log.d(TAG, "setupAlarmIfRequired: task was found. going to setup alarm");
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                nextAlarm.getNextAlarm(),
                createPendingIntent(
                    context,
                    nextAlarm.getDescription()));
        } else {
            Log.d(TAG, "setupAlarmIfRequired: no task with alarm in future found");
        }
    }

    public static String getNotificationString(Context context, String taskDescription, long overdueTasksCount) {
        String formatString = context.getString(R.string.notification_format);
        Locale locale = context.getResources().getConfiguration().locale;
        MessageFormat format = new MessageFormat(formatString, locale);
        ChoiceFormat cfn = getTaskChoiceFormat(locale.getISO3Language());
        format.setFormatByArgumentIndex(2, cfn);
        Object[] args = {taskDescription, overdueTasksCount - 1, overdueTasksCount - 1};
        taskDescription = format.format(args);
        return taskDescription;
    }

    public static ChoiceFormat getTaskChoiceFormat(String iso3Language) {
        if(iso3Language.equals("rus")) {
            double[] limits = {1,2,5,21,22,25};
            String[] texts = {"задача","задачи", "задач", "задача", "задачи", "задач"};
            return new ChoiceFormat(limits, texts);
        }
        else {
            // other language - english by default
            double[] limits = {1,2};
            String[] texts = {"task", "tasks"};
            return new ChoiceFormat(limits, texts);
        }
    }

    static class NextTaskInfo {
        private final long _nextAlarm;
        private final String _description;

        NextTaskInfo(long nextAlarm, String description) {
            _nextAlarm = nextAlarm;
            _description = description;
        }

        public long getNextAlarm() {
            return _nextAlarm;
        }

        public String getDescription() {
            return _description;
        }
    }

    static NextTaskInfo getNextAlarmTask(Context context, long currentTimestamp) {
        Log.d(TAG, "getNextAlarm");
        InTimeOpenHelper openHelper = new InTimeOpenHelper(context);
        try (SQLiteDatabase database = openHelper.getReadableDatabase()) {
            try (Cursor cursor = database.query(TASK_TABLE, new String[]{ NEXT_ALARM_FIELD, DESCRIPTION_FIELD}, NEXT_ALARM_FIELD + ">?", new String[]{Long.toString(currentTimestamp)}, null, null, NEXT_ALARM_FIELD, "1")) {
                if (cursor.moveToNext()) {
                    long nextAlarm = cursor.getLong(cursor.getColumnIndexOrThrow(NEXT_ALARM_FIELD));
                    String description = cursor.getString(cursor.getColumnIndexOrThrow(DESCRIPTION_FIELD));
                    NextTaskInfo task = new NextTaskInfo(nextAlarm, description);
                    return task;
                } else {
                    return null;
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
                    new String[]{Long.toString(currentTimeMillis)});
            return rowsCount;
        }
    }

    public static long getNumberOfSkippedTasks(Context context, long lastUsageTimestamp, long currentTimestamp) {
        try (SQLiteDatabase database = new InTimeOpenHelper(context).getReadableDatabase()) {
            long tasksCount = DatabaseUtils.queryNumEntries(
                    database,
                    TASK_TABLE,
                    NEXT_ALARM_FIELD + ">?" + " AND " + NEXT_ALARM_FIELD + "<?",
                    new String[]{Long.toString(lastUsageTimestamp), Long.toString(currentTimestamp)});
            return tasksCount;
        }
    }
}
