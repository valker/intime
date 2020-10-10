package com.vpe_soft.intime.intime.kotlin

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.Cursor
import android.util.Log
import android.util.TypedValue
import androidx.core.app.NotificationCompat
import com.vpe_soft.intime.intime.R
import com.vpe_soft.intime.intime.activity.MainActivity
import com.vpe_soft.intime.intime.database.InTimeOpenHelper
import com.vpe_soft.intime.intime.database.Task
import com.vpe_soft.intime.intime.database.TaskState
import com.vpe_soft.intime.intime.database.createCursor
import com.vpe_soft.intime.intime.receiver.AlarmReceiver
import com.vpe_soft.intime.intime.receiver.NOTIFICATION_TAG
import java.util.*

val Float.px
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        Resources.getSystem().displayMetrics
    )

val Long.date
    get() = Date(this)

val Context.alarmIntent
    get() = Intent(this, AlarmReceiver::class.java)

val Context.mainIntent
    get() = Intent(this, MainActivity::class.java)

val Context.mainPendingIntent: PendingIntent
    get() = PendingIntent.getActivity(this, 0, mainIntent, 0)

//todo: replace with non-deprecated implementation
fun Context.showNotification(string: String, logTag: String = "no tag") {
    Log.d(logTag, "showNotification")
    val notificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val builder = NotificationCompat.Builder(this)
        .setContentTitle(resources.getString(R.string.app_name))
        .setContentText(string)
        .setSmallIcon(R.drawable.notification_icon)
        .setDefaults(Notification.DEFAULT_ALL)
        .setContentIntent(mainPendingIntent)

    notificationManager.notify(NOTIFICATION_TAG, 1, builder.build())
}

val Context.helper get() = InTimeOpenHelper(this)

val Context.cursor: Cursor get() = createCursor(this)

val Task.taskState get() = TaskState(nextAlarm, nextCaution, lastAcknowledge)