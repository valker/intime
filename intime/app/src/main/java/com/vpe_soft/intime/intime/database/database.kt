package com.vpe_soft.intime.intime.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.vpe_soft.intime.intime.kotlin.*
import com.vpe_soft.intime.intime.receiver.getNextAlarm

private const val tag = "DatabaseUtil"
private val Long.withId get() = arrayOf(toString())

const val TASK_TABLE = "main.tasks"
const val NEXT_ALARM_FIELD = "next_alarm"
const val DESCRIPTION_FIELD = "description"
const val ID_FIELD = "id"
const val INTERVAL_FIELD = "interval"
const val AMOUNT_FIELD = "amount"
const val NEXT_CAUTION_FIELD = "next_caution"
const val LAST_ACKNOWLEDGE_FIELD = "last_ack"

fun createCursor(context: Context): Cursor = context.readableDatabase.query(
    TASK_TABLE,
    arrayOf(DESCRIPTION_FIELD, "id AS _id", NEXT_ALARM_FIELD, NEXT_CAUTION_FIELD),
    null,
    null,
    null,
    null,
    NEXT_ALARM_FIELD
)

val Context.databaseLength
    get() = readableDatabase.use { database ->
        DatabaseUtils.queryNumEntries(
            database,
            TASK_TABLE
        ).toInt()
    }

fun Context.getId(position: Int): Long = with(cursor()) {
    moveToPosition(position)
    return getLong(getColumnIndex("_id"))
}

fun Context.findTaskById(id: Long): Task {
    printLog("findTaskById", tag = tag)
    readableDatabase.use { database ->
        database.query(
            TASK_TABLE,
            arrayOf(
                DESCRIPTION_FIELD,
                INTERVAL_FIELD,
                AMOUNT_FIELD,
                NEXT_ALARM_FIELD,
                NEXT_CAUTION_FIELD,
                LAST_ACKNOWLEDGE_FIELD
            ),
            "id=?",
            id.withId,
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToNext()) {
                Log.d(
                    tag,
                    "findTaskById: task was found"
                )
                return with(cursor) {
                    Task(
                        getString(getColumnIndexOrThrow(DESCRIPTION_FIELD)),
                        getInt(getColumnIndexOrThrow(INTERVAL_FIELD)),
                        getInt(getColumnIndexOrThrow(AMOUNT_FIELD)),
                        getLong(getColumnIndexOrThrow(NEXT_ALARM_FIELD)),
                        getLong(getColumnIndexOrThrow(NEXT_CAUTION_FIELD)),
                        getLong(getColumnIndexOrThrow(LAST_ACKNOWLEDGE_FIELD))
                    )
                }
            } else {
                //TODO: create custom exception
                throw RuntimeException()
            }
        }
    }
}

val Context.readableDatabase: SQLiteDatabase get() = inTimeOpenHelper().readableDatabase

val Context.writableDatabase: SQLiteDatabase get() = inTimeOpenHelper().writableDatabase

fun Context.databaseAcknowledge(id: Long, currentTimeMillis: Long): TaskState {
    val task = findTaskById(id)
    printLog("id = $id", "description = ${task.description}", "milliseconds = $currentTimeMillis")
    val nextAlarmMoment: Long = getNextAlarm(
        task.interval,
        task.amount,
        currentTimeMillis,
        locale
    )
    val cautionPeriod = ((nextAlarmMoment - currentTimeMillis) * 0.95).toLong()
    val nextCautionMoment = currentTimeMillis + cautionPeriod
    val values = ContentValues()
    with(values) {
        put(NEXT_ALARM_FIELD, nextAlarmMoment)
        put(NEXT_CAUTION_FIELD, nextCautionMoment)
        put(LAST_ACKNOWLEDGE_FIELD, currentTimeMillis)
    }
    //todo: hmm
    val whereClause = "id=?"
    writableDatabase.use { database ->
        val result = database.update(
            TASK_TABLE,
            values,
            whereClause,
            id.withId
        )
        if (result != 1) {
            Log.w(tag, "acknowledgeTask: Cannot update task with id = $id")
            throw RuntimeException("cannot update task with id=$id")
        }
    }
    return task.taskState()
}

fun Context.getNumberOfOverDueTasks(currentTimeMillis: Long): Long = countTasks(
    this,
    "$NEXT_ALARM_FIELD<?", arrayOf(currentTimeMillis.toString())
)

fun Context.getNumberOfSkippedTasks(
    lastUsageTimestamp: Long,
    currentTimestamp: Long
): Long =
    countTasks(
        this,
        "$NEXT_ALARM_FIELD>? AND $NEXT_ALARM_FIELD<?",
        arrayOf(
            lastUsageTimestamp.toString(),
            currentTimestamp.toString()
        )
    )

private fun countTasks(
    context: Context,
    selection: String,
    selectionArgs: Array<String>
): Long =
    context.readableDatabase.use { database ->
        return DatabaseUtils.queryNumEntries(
            database,
            TASK_TABLE,
            selection,
            selectionArgs
        )
    }

// TODO: check why it is unused
fun Context.deleteTask(id: Long) {
    writableDatabase.use { database ->
        val result = database.delete(
            TASK_TABLE,
            "id=?",
            id.withId
        )
        if (result != 1) throw RuntimeException()
    }
}

fun Context.initTask(task: Task, id: Long? = null) {
    printLog("createNewTask", "id = $id", tag = tag)
    createTaskImp(id, task, this)
}

private fun createTaskImp(id: Long?, task: Task, context: Context) {
    val contentValues = ContentValues()
    if (id != null) {
        contentValues.put(ID_FIELD, id)
    }
    with(contentValues) {
        with(task) {
            put(DESCRIPTION_FIELD, description)
            put(INTERVAL_FIELD, interval)
            put(AMOUNT_FIELD, amount)
            put(NEXT_ALARM_FIELD, nextAlarm)
            put(NEXT_CAUTION_FIELD, nextCaution)
        }
    }
    context.writableDatabase.use { database ->
        database.insert(
            TASK_TABLE,
            null,
            contentValues
        )
    }
}

fun Context.updateTaskDescription(id: Long, task: Task) {
    val contentValues = ContentValues()
    contentValues.put(DESCRIPTION_FIELD, task.description)
    updateTaskImpl(id, this, contentValues)
}

fun Context.updateTask(id: Long, task: Task) {
    val contentValues = ContentValues()
    with(contentValues) {
        with(task) {
            put(DESCRIPTION_FIELD, description)
            put(INTERVAL_FIELD, interval)
            put(AMOUNT_FIELD, amount)
            put(NEXT_ALARM_FIELD, nextAlarm)
            put(NEXT_CAUTION_FIELD, nextCaution)
        }
    }
    updateTaskImpl(id, this, contentValues)
}

/**
 * Roll back the state of task
 *
 * @param id        - identifier ot the task
 * @param taskState - target state of the task
 */
fun Context.rollbackState(id: Long, taskState: TaskState) {
    val contentValues = ContentValues()
    with(contentValues) {
        put(NEXT_ALARM_FIELD, taskState.nextAlarm)
        put(NEXT_CAUTION_FIELD, taskState.nextCaution)
        put(LAST_ACKNOWLEDGE_FIELD, taskState.lastAcknowledge)
    }
    updateTaskImpl(id, this, contentValues)
}

private fun updateTaskImpl(id: Long, context: Context, contentValues: ContentValues) =
    context.writableDatabase.use { db ->
        db.update(
            TASK_TABLE,
            contentValues,
            "id=?",
            id.withId
        )
    }
