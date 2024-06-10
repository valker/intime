package com.vpe_soft.intime.intime.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;

public class DatabaseUtil {
    private static final String TAG = "DatabaseUtil";

    private static String[] withId(long id) {
        return new String[]{Long.toString(id)};
    }

    public static final String TASK_TABLE = "main.tasks";
    public static final String NEXT_ALARM_FIELD = "next_alarm";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String ID_FIELD = "id";
    public static final String INTERVAL_FIELD = "interval";
    public static final String AMOUNT_FIELD = "amount";
    public static final String NEXT_CAUTION_FIELD = "next_caution";
    public static final String LAST_ACK_FIELD = "last_ack";
    public static final String QUANT_FIELD = "quant";

    public static Cursor createCursor(InTimeOpenHelper openHelper) {
        SQLiteDatabase database = getReadableDatabaseFromContext(openHelper);
        return database.query(TASK_TABLE,new String[]{DESCRIPTION_FIELD, "id AS _id", NEXT_ALARM_FIELD, NEXT_CAUTION_FIELD}, null, null, null, null, NEXT_ALARM_FIELD);
    }

    public static int getDatabaseLengthFromContext(InTimeOpenHelper openHelper){
        SQLiteDatabase database = getReadableDatabaseFromContext(openHelper);
        long length;
        length = DatabaseUtils.queryNumEntries(database, TASK_TABLE);
        return (int) length;
    }

    public static long getId(int position, InTimeOpenHelper openHelper) {
        Cursor cursor = createCursor(openHelper);
        cursor.moveToPosition(position);
        final int columnIndex = cursor.getColumnIndex("_id");
        if(columnIndex == -1) throw new RuntimeException("Column '_id' not found in the table " +
                                                         "tasks");
        return cursor.getLong(columnIndex);
    }

    public static Task findTaskById(long id, InTimeOpenHelper openHelper) {
        Log.d(TAG, "findTaskById");
        //next line may cause an error (not checked yet)
        SQLiteDatabase database = getReadableDatabaseFromContext(openHelper);
        try (Cursor cursor = database.query(TASK_TABLE,
                                            new String[]{DESCRIPTION_FIELD, INTERVAL_FIELD,
                                                    AMOUNT_FIELD, NEXT_ALARM_FIELD,
                                                    NEXT_CAUTION_FIELD, LAST_ACK_FIELD,
                                                    QUANT_FIELD},
                                            "id=?",
                                            withId(id),
                                            null,
                                            null,
                                            null,
                                            "1")) {
            if (cursor.moveToNext()) {
                Log.d(TAG, "findTaskById: task was found");
                String description = cursor.getString(cursor.getColumnIndexOrThrow(DESCRIPTION_FIELD));
                int interval = cursor.getInt(cursor.getColumnIndexOrThrow(INTERVAL_FIELD));
                int amount = cursor.getInt(cursor.getColumnIndexOrThrow(AMOUNT_FIELD));
                long nextAlarm = cursor.getLong(cursor.getColumnIndexOrThrow(NEXT_ALARM_FIELD));
                long nextCaution = cursor.getLong(cursor.getColumnIndexOrThrow(NEXT_CAUTION_FIELD));
                long lastAck = cursor.getLong(cursor.getColumnIndexOrThrow(LAST_ACK_FIELD));
                int quant = cursor.getInt(cursor.getColumnIndexOrThrow(QUANT_FIELD));
                return new Task(description, interval, amount, nextAlarm, nextCaution, lastAck,
                        quant);
            } else {
                Log.d(TAG, "findTaskById: task not found");
            }
        }

        return null;
    }
    public static SQLiteDatabase getReadableDatabaseFromContext(InTimeOpenHelper openHelper){
        return openHelper.getReadableDatabase();
    }

    public static SQLiteDatabase getWritableDatabaseFromContext(InTimeOpenHelper openHelper){
        return openHelper.getWritableDatabase();
    }

    public static TaskState acknowledgeTask(long id, long currentTimeMillis, Context context, InTimeOpenHelper openHelper) {
        Task task = findTaskById(id, openHelper);
        if (task == null) {
            Log.w(TAG, "Can't find task with id = " + id);
            return null;
        }

        TaskState taskState = new TaskState(task);
        Log.d(TAG, "id " + id);
        Log.d(TAG, "task_desc " + task.getDescription());
        Log.d(TAG, "millis " + currentTimeMillis);
        final long nextAlarmMoment = AlarmUtil.getNextAlarm(task.getInterval(),
                                                            task.getAmount(),
                                                            currentTimeMillis,
                                                            task.getQuant(),
                                                            context.getResources().getConfiguration().locale);
        final long cautionPeriod = (long) ((nextAlarmMoment - currentTimeMillis) * Constants.CAUTION_FACTOR);
        final long nextCautionMoment = currentTimeMillis + cautionPeriod;
        ContentValues values = new ContentValues();
        values.put(NEXT_ALARM_FIELD, nextAlarmMoment);
        values.put(NEXT_CAUTION_FIELD, nextCautionMoment);
        values.put(LAST_ACK_FIELD, currentTimeMillis);
        String whereClause = "id=?";
        SQLiteDatabase database = getWritableDatabaseFromContext(openHelper);
        final int result = database.update(TASK_TABLE, values, whereClause, withId(id));
        if (result != 1) {
            Log.w(TAG, "acknowledgeTask: Cannot update task with id=" + id);
            throw new RuntimeException("cannot update task with id=" + id);
        }

        return taskState;
    }

    public static long getNumberOfOverDueTasks(long currentTimeMillis, InTimeOpenHelper openHelper) {
        return countTasks(
                NEXT_ALARM_FIELD + "<?",
                new String[]{Long.toString(currentTimeMillis)}, openHelper);
    }

    public static long getNumberOfSkippedTasks(long lastUsageTimestamp, long currentTimestamp, InTimeOpenHelper openHelper) {
        return countTasks(
                NEXT_ALARM_FIELD + ">?" + " AND " + NEXT_ALARM_FIELD + "<?",
                new String[]{Long.toString(lastUsageTimestamp), Long.toString(currentTimestamp)}, openHelper);
    }
    private static long countTasks(String selection, String[] selectionArgs, InTimeOpenHelper openHelper) {
        SQLiteDatabase database = getReadableDatabaseFromContext(openHelper);
        long rowsCount = DatabaseUtils.queryNumEntries(
                database,
                TASK_TABLE,
                selection,
                selectionArgs);
        return rowsCount;
    }
    public static void deleteTask(long id, InTimeOpenHelper openHelper) {
        SQLiteDatabase database = getWritableDatabaseFromContext(openHelper);
        int result = database.delete(TASK_TABLE, "id=?", withId(id));
        if (result != 1) {
            throw new RuntimeException();
        }
    }
    public static void createNewTask(Task task, InTimeOpenHelper openHelper) {
        Log.d(TAG, "createNewTask");

        createTaskImp(null, task, openHelper);
    }

    public static void createNewTask(long id, Task task, InTimeOpenHelper openHelper) {
        Log.d(TAG, "createNewTask with ID");

        createTaskImp(id, task, openHelper);
    }

    private static void createTaskImp(Long id, Task task, InTimeOpenHelper openHelper) {
        ContentValues contentValues = new ContentValues();

        if(id != null) {
            contentValues.put(ID_FIELD, id);
        }
        contentValues.put(DESCRIPTION_FIELD, task.getDescription());
        contentValues.put(INTERVAL_FIELD, task.getInterval());
        contentValues.put(AMOUNT_FIELD, task.getAmount());
        contentValues.put(NEXT_ALARM_FIELD, task.getNextAlarm());
        contentValues.put(NEXT_CAUTION_FIELD, task.getNextCaution());
        contentValues.put(QUANT_FIELD, task.getQuant());

        SQLiteDatabase db = getWritableDatabaseFromContext(openHelper);
        db.insert(TASK_TABLE, null, contentValues);
    }

    public static void updateTaskDescription(long id, Task task, InTimeOpenHelper openHelper)
    {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DESCRIPTION_FIELD, task.getDescription());
        updateTaskImpl(id, contentValues, openHelper);
    }

    public static void updateTask(long id, Task task, InTimeOpenHelper openHelper)
    {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DESCRIPTION_FIELD, task.getDescription());
        contentValues.put(INTERVAL_FIELD, task.getInterval());
        contentValues.put(AMOUNT_FIELD, task.getAmount());
        contentValues.put(NEXT_ALARM_FIELD, task.getNextAlarm());
        contentValues.put(NEXT_CAUTION_FIELD, task.getNextCaution());
        contentValues.put(QUANT_FIELD, task.getQuant());
        updateTaskImpl(id, contentValues, openHelper);
    }

    /**
     * Roll back the state of task
     * @param id - identifier ot the task
     * @param taskState - target state of the task
     * @param openHelper
     */
    public static void rollBackState(long id, TaskState taskState, InTimeOpenHelper openHelper) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(NEXT_ALARM_FIELD, taskState.getNextAlarm());
        contentValues.put(NEXT_CAUTION_FIELD, taskState.getNextCaution());
        contentValues.put(LAST_ACK_FIELD, taskState.getLastAck());
        updateTaskImpl(id, contentValues, openHelper);
    }

    private static void updateTaskImpl(long id, ContentValues contentValues, InTimeOpenHelper openHelper) {
        SQLiteDatabase db = getWritableDatabaseFromContext(openHelper);
        db.update(TASK_TABLE, contentValues, "id=?", withId(id));
    }
}
