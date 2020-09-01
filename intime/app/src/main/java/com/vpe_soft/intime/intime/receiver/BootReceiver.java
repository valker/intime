package com.vpe_soft.intime.intime.receiver;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.activity.MainActivity;
import com.vpe_soft.intime.intime.database.DatabaseUtil;

/**
 * Created by Valentin on 26.08.2015.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        final String intentAction = intent.getAction();
        if (intentAction.equals(Intent.ACTION_BOOT_COMPLETED)
                ||
                intentAction.equals(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        ) {
            Log.d(TAG, "onReceive: " + intentAction);
            //1. get list of tasks that have next alarm between last-run and current time
            // 1.1 get last usage timestamp
            SharedPreferences sharedPreferences = context.getSharedPreferences("SessionInfo", Context.MODE_PRIVATE);
            final long lastUsageTimestamp = sharedPreferences.getLong("LastUsageTimestamp", 0);
            final long currentTimestamp = System.currentTimeMillis();
            // number of tasks were overdue during phone was off
            final long tasksCount = DatabaseUtil.getNumberOfSkippedTasks(context, lastUsageTimestamp, currentTimestamp);
            //2. if this list is not empty, generate notification
            if (tasksCount > 0) {
                Log.d(TAG, "onReceive: overdue tasks were found");
                // we will raise a notification
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
                builder.setContentTitle(context.getResources().getString(R.string.app_name));
                builder.setContentText(context.getString(R.string.boot_completed_overdue_tasks_notification));
                builder.setSmallIcon(R.drawable.notification_icon);
                Intent mainActIntent = new Intent(context, MainActivity.class);
                PendingIntent mainActivityIntent = PendingIntent.getActivity(context, 0, mainActIntent, 0);
                builder.setContentIntent(mainActivityIntent);
                Notification notification = builder.build();
                notificationManager.notify(AlarmUtil.NOTIFICATION_TAG, 1, notification);
            } else {
                Log.d(TAG, "onReceive: not found overdue tasks");
            }

            //3. create alarm (if required for future task)
            AlarmUtil.setupAlarmIfRequired(context);
        }
    }
}
