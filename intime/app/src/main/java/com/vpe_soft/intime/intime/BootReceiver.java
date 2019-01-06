package com.vpe_soft.intime.intime;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

/**
 * Created by Valentin on 26.08.2015.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            Log.d("VP", "onReceive: BOOT_COMPLETED");
            //1. get list of tasks that have next alarm between last-run and current time
            // 1.1 get last usage timestamp
            SharedPreferences sharedPreferences = context.getSharedPreferences("SessionInfo", Context.MODE_PRIVATE);
            long lastUsageTimestamp = sharedPreferences.getLong("LastUsageTimestamp", 0);
            long currentTimestamp = System.currentTimeMillis();
            InTimeOpenHelper openHelper = new InTimeOpenHelper(context);
            try (SQLiteDatabase database = openHelper.getReadableDatabase()) {
                long tasksCount = DatabaseUtils.queryNumEntries(
                        database,
                        "tasks",
                        "next_alarm>? AND next_alarm<?",
                        new String[]{Long.toString(lastUsageTimestamp), Long.toString(currentTimestamp)});

                //2. if this list is not empty, generate notification
                if(tasksCount > 0) {
                    // number of tasks were overdue during phone was off
                    // we will raise a notification
                    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
                    builder.setContentTitle(context.getResources().getString(R.string.app_name));
                    builder.setContentText("During phone was off a number of tasks were overdue.");
                    builder.setSmallIcon(R.drawable.notification_icon);
                    Intent mainActIntent = new Intent(context, MainActivity.class);
                    PendingIntent mainActivityIntent = PendingIntent.getActivity(context, 0, mainActIntent, 0);
                    builder.setContentIntent(mainActivityIntent);
                    Notification notification = builder.build();
                    notificationManager.notify(Util.NOTIFICATION_TAG, 1, notification);
                }
            }

            //3. create alarm (if required for future task)
            Util.setupAlarmIfRequired(context);
        }
    }
}
