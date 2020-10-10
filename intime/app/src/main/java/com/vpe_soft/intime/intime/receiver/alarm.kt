package com.vpe_soft.intime.intime.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import com.vpe_soft.intime.intime.R
import com.vpe_soft.intime.intime.database.DESCRIPTION_FIELD
import com.vpe_soft.intime.intime.database.NEXT_ALARM_FIELD
import com.vpe_soft.intime.intime.database.TASK_TABLE
import com.vpe_soft.intime.intime.database.readableDatabase
import com.vpe_soft.intime.intime.kotlin.alarmIntent
import com.vpe_soft.intime.intime.kotlin.date
import java.text.ChoiceFormat
import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.*

private const val tag = "AlarmUtil"
private const val skeleton = "jjmm ddMMyyyy"

const val TASK_OVERDUE_ACTION = "com.vpe_soft.intime.intime.TaskOverdue"
const val NOTIFICATION_TAG = "com.vpe_soft.intime.intime.NotificationTag"

private val fields = intArrayOf(
    Calendar.MINUTE,
    Calendar.HOUR,
    Calendar.DAY_OF_YEAR,
    Calendar.WEEK_OF_YEAR,
    Calendar.MONTH,
    Calendar.FIELD_COUNT //substitute for YEAR
)

fun getNextAlarm(interval: Int, amount: Int, lastAck: Long, locale: Locale): Long {
    var newAmount = amount
    Log.d(tag, "getNextAlarm")
    val calendar: Calendar = GregorianCalendar(locale)
    calendar.time = lastAck.date
    var field = fields[interval]
    if (field == Calendar.FIELD_COUNT) {
        // YEAR is not supported by calendar.add, so emulate it as 12 months
        field = Calendar.MONTH
        newAmount *= 12
    }
    calendar.add(field, amount)
    return calendar.time.time
}

fun getDateFromNextAlarm(locale: Locale, nextAlarm: Long): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    val format = SimpleDateFormat(pattern, locale)
    format.timeZone = TimeZone.getDefault()
    return format.format(nextAlarm.date)
}

fun Context.setupAlarmIfRequired() {
    Log.d(tag, "setupAlarmIfRequired")
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    readableDatabase.use { database ->
        val currentTimestamp = System.currentTimeMillis()
        database.query(
            TASK_TABLE,
            arrayOf(
                "id",
                NEXT_ALARM_FIELD,
                DESCRIPTION_FIELD
            ),
            "next_alarm>?",
            arrayOf(currentTimestamp.toString()),
            null,
            null,
            NEXT_ALARM_FIELD,
            "1"
        ).use { cursor ->
            if (cursor.moveToNext()) {
                Log.d(
                    tag,
                    "setupAlarmIfRequired: task was found. going to setup alarm"
                )
                val nextAlarm =
                    cursor.getLong(cursor.getColumnIndexOrThrow(NEXT_ALARM_FIELD))
                val pendingIntent = createPendingIntent(
                    this,
                    cursor.getString(cursor.getColumnIndexOrThrow(DESCRIPTION_FIELD))
                )
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarm,
                    pendingIntent
                )
            } else Log.d(tag, "setupAlarmIfRequired: no task with alarm in future found")
        }
    }
}

fun Context.getNotificationString(
    taskDescription: String,
    overdueTasksCount: Long
): String {
    val formatString = getString(R.string.notification_format)
    val locale = resources.configuration.locale
    val format = MessageFormat(formatString, locale)
    val cfn = locale.isO3Language.taskChoiceFormat
    format.setFormatByArgumentIndex(2, cfn)
    val args = arrayOf(taskDescription, overdueTasksCount - 1, overdueTasksCount - 1)
    return format.format(args)
}

val String.taskChoiceFormat
    get() =
        if (this == "rus") {
            val limits = doubleArrayOf(1.0, 2.0, 5.0, 21.0, 22.0, 25.0)
            val texts = arrayOf("задача", "задачи", "задач", "задача", "задачи", "задач")
            ChoiceFormat(limits, texts)
        } else {
            // other language - english by default
            val limits = doubleArrayOf(1.0, 2.0)
            val texts = arrayOf("task", "tasks")
            ChoiceFormat(limits, texts)
        }

private fun createPendingIntent(context: Context, taskDescription: String): PendingIntent {
    Log.d(tag, "createPendingIntent")
    return PendingIntent.getBroadcast(
        context,
        199709,
        context.alarmIntent.putExtra("task_description", taskDescription),
        PendingIntent.FLAG_CANCEL_CURRENT
    )
}