package com.vpe_soft.intime.intime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vpe_soft.intime.intime.MainActivity.Companion.isOnScreen
import com.vpe_soft.intime.intime.database.getNumberOfOverDueTasks
import com.vpe_soft.intime.intime.kotlin.Taggable
import com.vpe_soft.intime.intime.kotlin.log
import com.vpe_soft.intime.intime.kotlin.showNotification

/**
 * Created by Valentin on 26.08.2015.
 * Receives notifications from AlarmManager about next alarm and pass it to MainActivity
 */
class AlarmReceiver : BroadcastReceiver(), Taggable {
    override val tag = "AlarmReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        log("onReceive")
        //todo: next line may produce errors (need to check)
        var description = intent.getStringExtra("task_description")!!
        val currentTimeMillis = System.currentTimeMillis()
        val overdueCount = context.getNumberOfOverDueTasks(currentTimeMillis)

        // if there are other overdue tasks, modify notification text to let user know about that
        if (overdueCount > 1) description = context.getNotificationString(description, overdueCount)
        val broadcastIntent = Intent(TASK_OVERDUE_ACTION)
        broadcastIntent.putExtra("task_description", description)
        context.sendOrderedBroadcast(broadcastIntent, null)
        if (!isOnScreen) {
            log("onReceive: will show notification")
            context.showNotification(description, tag)
        } else log("onReceive: won't show notification")
        context.setupAlarmIfRequired()
    }
}