package com.vpe_soft.intime.intime.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.vpe_soft.intime.intime.receiver.AlarmUtil;

public class DatabaseUtil {
    private static final String TAG = "DatabaseUtil";

    private static String[] withId(long id) {
        return new String[]{Long.toString(id)};
    }

    public static final String TASK_TABLE = "main.tasks";
    public static final String NEXT_ALARM_FIELD = "next_alarm";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String INTERVAL_FIELD = "interval";
    public static final String AMOUNT_FIELD = "amount";
    public static final String NEXT_CAUTION_FIELD = "next_caution";
    public static final String LAST_ACK_FIELD = "last_ack";

    public static Cursor createCursor(Context context) {
        SQLiteDatabase database = getReadableDatabaseFromContext(context);
        return database.query(TASK_TABLE, new String[]{DESCRIPTION_FIELD, "id AS _id", NEXT_ALARM_FIELD, NEXT_CAUTION_FIELD}, null, null, null, null, NEXT_ALARM_FIELD);
    }

    public static int getDatabaseLengthFromContext(Context context) {
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

    public static SQLiteDatabase getReadableDatabaseFromContext(Context context) {
        InTimeOpenHelper helper = new InTimeOpenHelper(context);
        return helper.getReadableDatabase();
    }

    public static SQLiteDatabase getWritableDatabaseFromContext(Context context) {
        InTimeOpenHelper helper = new InTimeOpenHelper(context);
        return helper.getWritableDatabase();
    }

    public static TaskState acknowledgeTask(long id, long currentTimeMillis, Context context) {
        Task task = findTaskById(context, id);
        if (task == null) {
            Log.w(TAG, "Can't find task with id = " + id);
            return null;
        }

        TaskState taskState = new TaskState(task);
        Log.d(TAG, "id " + id);
        Log.d(TAG, "task_desc " + task.getDescription());
        Log.d(TAG, "millis " + currentTimeMillis);
        final long nextAlarmMoment = AlarmUtil.getNextAlarm(task.getInterval(), task.getAmount(), currentTimeMillis, context.getResources().getConfiguration().locale);
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

        return taskState;
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

    private static long countTasks(Context context, String selection, String[] selectionArgs) {
        try (SQLiteDatabase database = getReadableDatabaseFromContext(context)) {
            return DatabaseUtils.queryNumEntries(
                    database,
                    TASK_TABLE,
                    selection,
                    selectionArgs);
        }
    }

    public static void deleteTask(long id, Context context) {
        try (SQLiteDatabase database = getWritableDatabaseFromContext(context)) {
            int result = database.delete(TASK_TABLE, "id=?", withId(id));
            if (result != 1) {
                throw new RuntimeException();
            }
        }
    }

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

    public static void updateTaskDescription(long id, Task task, Context context) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DESCRIPTION_FIELD, task.getDescription());
        updateTaskImpl(id, context, contentValues);
    }

    public static void updateTask(long id, Task task, Context context) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DESCRIPTION_FIELD, task.getDescription());
        contentValues.put(INTERVAL_FIELD, task.getInterval());
        contentValues.put(AMOUNT_FIELD, task.getAmount());
        contentValues.put(NEXT_ALARM_FIELD, task.getNextAlarm());
        contentValues.put(NEXT_CAUTION_FIELD, task.getNextCaution());
        updateTaskImpl(id, context, contentValues);
    }

    /**
     * Roll back the state of task
     *
     * @param id        - identifier ot the task
     * @param context   - context to get DB
     * @param taskState - target state of the task
     */
    public static void rollBackState(long id, Context context, TaskState taskState) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(NEXT_ALARM_FIELD, taskState.getNextAlarm());
        contentValues.put(NEXT_CAUTION_FIELD, taskState.getNextCaution());
        contentValues.put(LAST_ACK_FIELD, taskState.getLastAck());
        updateTaskImpl(id, context, contentValues);
    }

    private static void updateTaskImpl(long id, Context context, ContentValues contentValues) {
        try (SQLiteDatabase db = getWritableDatabaseFromContext(context)) {
            db.update(TASK_TABLE, contentValues, "id=?", withId(id));
        }
    }
}
