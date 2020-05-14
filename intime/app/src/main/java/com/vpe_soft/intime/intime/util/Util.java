package com.vpe_soft.intime.intime.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.os.Build;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.InTimeOpenHelper;
import com.vpe_soft.intime.intime.database.Task;
import com.vpe_soft.intime.intime.receiver.AlarmReceiver;

import java.text.ChoiceFormat;
import java.text.MessageFormat;
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

    private static final String SKELETON = "jjmm ddMMyyyy";

    private static final String TASK_TABLE = "main.tasks";
    private static final String NEXT_ALARM_FIELD = "next_alarm";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String INTERVAL_FIELD = "interval";
    private static final String AMOUNT_FIELD = "amount";
    private static final String NEXT_CAUTION_FIELD = "next_caution";
    private static final String LAST_ACK_FIELD = "last_ack";
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

    public static float getCardCornerRadius(Context context) {
        return Util.toPx(context, 10);
    }

    public static float toPx(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    public static long getNextAlarm(int interval, int amount, long lastAck, Locale locale) {
        Log.d(TAG, "getNextAlarm");
        Date date = new Date(lastAck);
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
        return database.query(Util.TASK_TABLE,new String[]{DESCRIPTION_FIELD, "id AS _id", NEXT_ALARM_FIELD, NEXT_CAUTION_FIELD}, null, null, null, null, NEXT_ALARM_FIELD);
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
        try (SQLiteDatabase database = getReadableDatabaseFromContext(context)) {
            long length;
            length = DatabaseUtils.queryNumEntries(database, TASK_TABLE);
            return (int) length;
        }
    }

    public static long getId(Context context, int position) {
        Cursor cursor = createCursor(context);
        cursor.moveToPosition(position);
        return cursor.getLong(cursor.getColumnIndex("_id"));
    }

    public static Task findTaskById(Context context, long id) {
        Log.d(TAG, "findTaskById");
        //next line may cause an error (not checked yet)
        try (SQLiteDatabase database = getReadableDatabaseFromContext(context)) {
            try (Cursor cursor = database.query(TASK_TABLE, new String[]{DESCRIPTION_FIELD, INTERVAL_FIELD, AMOUNT_FIELD, NEXT_ALARM_FIELD, NEXT_CAUTION_FIELD, LAST_ACK_FIELD}, "id=?", withId(id), null, null, null, "1")) {
                if (cursor.moveToNext()) {
                    Log.d(TAG, "findTaskById: task was found");
                    String description = cursor.getString(cursor.getColumnIndexOrThrow(DESCRIPTION_FIELD));
                    int interval = cursor.getInt(cursor.getColumnIndexOrThrow(INTERVAL_FIELD));
                    int amount = cursor.getInt(cursor.getColumnIndexOrThrow(AMOUNT_FIELD));
                    long nextAlarm = cursor.getLong(cursor.getColumnIndexOrThrow(NEXT_ALARM_FIELD));
                    long nextCaution = cursor.getLong(cursor.getColumnIndexOrThrow(NEXT_CAUTION_FIELD));
                    long lastAck = cursor.getLong(cursor.getColumnIndexOrThrow(LAST_ACK_FIELD));
                    return new Task(description, interval, amount, nextAlarm, nextCaution, lastAck);
                } else {
                    Log.d(TAG, "findTaskById: task not found");
                }
            }
        }

        return null;
    }

    private static SQLiteDatabase getReadableDatabaseFromContext(Context context){
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
        try (SQLiteDatabase database = getReadableDatabaseFromContext(context)) {
            final long currentTimestamp = System.currentTimeMillis();
            try (Cursor next_alarm = database.query(TASK_TABLE, new String[]{"id", NEXT_ALARM_FIELD, DESCRIPTION_FIELD}, "next_alarm>?", new String[]{Long.toString(currentTimestamp)}, null, null, NEXT_ALARM_FIELD, "1")) {
                if (next_alarm.moveToNext()) {
                    Log.d(TAG, "setupAlarmIfRequired: task was found. going to setup alarm");
                    long nextAlarm = next_alarm.getLong(next_alarm.getColumnIndexOrThrow(NEXT_ALARM_FIELD));
                    final PendingIntent pendingIntent = createPendingIntent(
                            context,
                            next_alarm.getString(next_alarm.getColumnIndexOrThrow(DESCRIPTION_FIELD)));
                    if(Build.VERSION.SDK_INT>=23) {
                        alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                nextAlarm,
                                pendingIntent);
                    }else {
                        alarmManager.set(
                                AlarmManager.RTC_WAKEUP,
                                nextAlarm,
                                pendingIntent);
                    }
                } else {
                    Log.d(TAG, "setupAlarmIfRequired: no task with alarm in future found");
                }
            }
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

    public static boolean acknowledgeTask(long id, long currentTimeMillis, Context context) {
        Task task = findTaskById(context, id);
        if (task == null) {
            Log.w(TAG, "Can't find task with id = " + id);
            return true;
        }
        Log.d(TAG, "id " + id);
        Log.d(TAG, "task_desc " + task.getDescription());
        Log.d(TAG, "millis " + currentTimeMillis);
        final long nextAlarmMoment = getNextAlarm(task.getInterval(), task.getAmount(), currentTimeMillis, context.getResources().getConfiguration().locale);
        final long cautionPeriod = (long) ((nextAlarmMoment - currentTimeMillis) * 0.95);
        final long nextCautionMoment = currentTimeMillis + cautionPeriod;
        ContentValues values = new ContentValues();
        values.put(NEXT_ALARM_FIELD, nextAlarmMoment);
        values.put(NEXT_CAUTION_FIELD, nextCautionMoment);
        values.put(LAST_ACK_FIELD, currentTimeMillis);
        String whereClause = "id=?";
        try (SQLiteDatabase database = getWritableDatabaseFromContext(context)) {
            final int result = database.update(TASK_TABLE, values, whereClause, withId(id));
            if (result != 1) {
                Log.w(TAG, "acknowledgeTask: Cannot update task with id=" + id);
                throw new RuntimeException("cannot update task with id=" + id);
            }
        }

        return false;
    }

    private static PendingIntent createPendingIntent(Context context, String taskDescription) {
        Log.d(TAG, "createPendingIntent");
        final Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("task_description", taskDescription);
        return PendingIntent.getBroadcast(context, 199709, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    public static long getNumberOfOverDueTasks(Context context, long currentTimeMillis) {
        return countTasks(
                context,
                NEXT_ALARM_FIELD + "<?",
                new String[]{Long.toString(currentTimeMillis)});
    }

    public static long getNumberOfSkippedTasks(Context context, long lastUsageTimestamp, long currentTimestamp) {
        return countTasks(
                context,
                NEXT_ALARM_FIELD + ">?" + " AND " + NEXT_ALARM_FIELD + "<?",
                new String[]{Long.toString(lastUsageTimestamp), Long.toString(currentTimestamp)});
    }

    /**
     * Count tasks by given selection
     * @param context
     * @param selection
     * @param selectionArgs
     * @return
     */
    private static long countTasks(Context context, String selection, String[] selectionArgs) {
        try (SQLiteDatabase database = getReadableDatabaseFromContext(context)) {
            long rowsCount = DatabaseUtils.queryNumEntries(
                    database,
                    TASK_TABLE,
                    selection,
                    selectionArgs);
            return rowsCount;
        }
    }

    /**
     * Delete task with given id from DB
     * @param id
     * @param context
     */
    public static void deleteTask(long id, Context context) {
        try (SQLiteDatabase database = getWritableDatabaseFromContext(context)) {
            int result = database.delete(TASK_TABLE, "id=?", withId(id));
            if (result != 1) {
                throw new RuntimeException();
            }
        }
    }

    /**
     * Create new task in the DB with given attributes
     * @param task
     * @param context
     */
    public static void createNewTask(Task task, Context context) {
        Log.d(TAG, "createNewTask");

        ContentValues contentValues = new ContentValues();
        contentValues.put(DESCRIPTION_FIELD, task.getDescription());
        contentValues.put(INTERVAL_FIELD, task.getInterval());
        contentValues.put(AMOUNT_FIELD, task.getAmount());
        contentValues.put(NEXT_ALARM_FIELD, task.getNextAlarm());
        contentValues.put(NEXT_CAUTION_FIELD, task.getNextCaution());

        try (SQLiteDatabase db = getWritableDatabaseFromContext(context)) {
            db.insert(TASK_TABLE, null, contentValues);
        }
    }

    /**
     * Update only description of task (in the DB)
     * @param id
     * @param task
     * @param context
     */
    public static void updateTaskDescription(long id, Task task, Context context)
    {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DESCRIPTION_FIELD, task.getDescription());
        updateTaskImpl(id, context, contentValues);
    }

    /**
     * Update all attributes of task (int the DB)
     * @param id
     * @param task
     * @param context
     */
    public static void updateTask(long id, Task task, Context context)
    {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DESCRIPTION_FIELD, task.getDescription());
        contentValues.put(INTERVAL_FIELD, task.getInterval());
        contentValues.put(AMOUNT_FIELD, task.getAmount());
        contentValues.put(NEXT_ALARM_FIELD, task.getNextAlarm());
        contentValues.put(NEXT_CAUTION_FIELD, task.getNextCaution());
        updateTaskImpl(id, context, contentValues);
    }

    /**
     * Update task in the DB with given id and content values
     * @param id
     * @param context
     * @param contentValues
     */
    private static void updateTaskImpl(long id, Context context, ContentValues contentValues) {
        try (SQLiteDatabase db = Util.getWritableDatabaseFromContext(context)) {
            db.update(Util.TASK_TABLE, contentValues, "id=?", withId(id));
        }
    }

    /**
     * Returns a WHERE arguments with given id
     * @param id identifier of a task
     * @return where arguments for database query/update/delete
     */
    private static String[] withId(long id) {
        return new String[]{Long.toString(id)};
    }
}
