package com.vpe_soft.intime.intime.kotlin

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.Cursor
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.AbsSpinner
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vpe_soft.intime.intime.R
import com.vpe_soft.intime.intime.activity.MainActivity
import com.vpe_soft.intime.intime.database.InTimeOpenHelper
import com.vpe_soft.intime.intime.database.Task
import com.vpe_soft.intime.intime.database.TaskState
import com.vpe_soft.intime.intime.database.createCursor
import com.vpe_soft.intime.intime.receiver.AlarmReceiver
import com.vpe_soft.intime.intime.receiver.NOTIFICATION_TAG
import com.vpe_soft.intime.intime.recyclerview.TaskRecyclerViewAdapter
import java.util.*

//todo: create exception for get = 0, get = {}, etc (UnsupportedException)

val millis get() = System.currentTimeMillis()

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

val Context.locale: Locale
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) resources.configuration.locales.get(0)
        else @Suppress("DEPRECATION") resources.configuration.locale

val Context.linearLayoutManager get() = LinearLayoutManager(this)

val Context.newRecyclerViewAdapter get() = TaskRecyclerViewAdapter(this)

val Task.taskState get() = TaskState(nextAlarm, nextCaution, lastAcknowledge)

var View.clickListener: (View) -> Unit
    get() = {}
    set(value) = setOnClickListener(value)

var View.longClickListener: (View) -> Unit
    get() = {}
    set(value) = setOnLongClickListener {
        value(it)
        true
    }

var AppCompatActivity.contentView: Int
    get() = 0
    set(value) = setContentView(value)

var AppCompatActivity.toolbar: Toolbar?
    get() = null
    set(value) = setSupportActionBar(value)

var EditText.textChangesListener: () -> Unit
    get() = {}
    set(value) = addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            value()
        }

        override fun afterTextChanged(s: Editable) {}
    })

var EditText.value: String
    get() = text.toString()
    set(value) = setText(value)

var EditText.hintColor: Int
    get() = 0
    set(value) = setHintTextColor(value)

var EditText.tint: Int
    get() = 0
    set(value) = DrawableCompat.setTint(background, value)

var AbsSpinner.selection: Int
    get() = selectedItemPosition
    set(value) = setSelection(value)

var RecyclerView.backgroundColor: Int
    get() = 0
    set(value) = setBackgroundColor(value)