package com.vpe_soft.intime.intime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vpe_soft.intime.intime.R
import com.vpe_soft.intime.intime.database.getNumberOfSkippedTasks
import com.vpe_soft.intime.intime.kotlin.Taggable
import com.vpe_soft.intime.intime.kotlin.log
import com.vpe_soft.intime.intime.kotlin.showNotification

class BootReceiver : BroadcastReceiver(), Taggable {
    override val tag = "BootReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        log("onReceive")
        val intentAction = intent.action
        if (intentAction == Intent.ACTION_BOOT_COMPLETED || intentAction == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            log("onReceive", "intentAction = $intentAction")
            //1. get list of tasks that have next alarm between last-run and current time
            // 1.1 get last usage timestamp
            val sharedPreferences =
                context.getSharedPreferences("SessionInfo", Context.MODE_PRIVATE)
            val lastUsageTimestamp = sharedPreferences.getLong("LastUsageTimestamp", 0)
            val currentTimestamp = System.currentTimeMillis()
            // number of tasks were overdue during phone was off
            val tasksCount =
                context.getNumberOfSkippedTasks(lastUsageTimestamp, currentTimestamp)
            //2. if this list is not empty, generate notification

            //TODO: rewrite
            if (tasksCount > 0) {
                context.getString(R.string.boot_completed_overdue_tasks_notification)
                log("onReceive: overdue tasks were found")
                // we will raise a notification
                context.showNotification(
                    context.getString(R.string.boot_completed_overdue_tasks_notification),
                    tag
                )
            } else log("onReceive: not found overdue tasks")

            //3. create alarm (if required for future task)
            context.setupAlarmIfRequired()
        }
    }
}