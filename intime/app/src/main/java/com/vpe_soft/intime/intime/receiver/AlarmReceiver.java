package com.vpe_soft.intime.intime.receiver;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.activity.MainActivity;
import com.vpe_soft.intime.intime.database.DatabaseUtil;

/**
 * Created by Valentin on 26.08.2015.
 * Receives notifications from AlarmManager about next alarm and pass it to MainActivity
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        String s = null;
        try {
            s = intent.getStringExtra("task_description");
        } catch (Exception e) {
            Log.e(TAG, "onReceive: unexpected error", e);
        }


        // prevent empty description
        s = s == null || s.length() == 0
                ? "unknown"
                : s;

        final long currentTimeMillis = System.currentTimeMillis();
        long overdueCount = DatabaseUtil.getNumberOfOverDueTasks(context, currentTimeMillis);

        // if there are other overdue tasks, modify notification text to let user know about that
        if (overdueCount > 1) {
            s = AlarmUtil.getNotificationString(context, s, overdueCount);
        }

        Intent broadcastIntent = new Intent(AlarmUtil.TASK_OVERDUE_ACTION);
        broadcastIntent.putExtra("task_description", s);
        context.sendOrderedBroadcast(broadcastIntent, null);

        if (!MainActivity.isOnScreen) {
            Log.d(TAG, "onReceive: will show notification");
            showNotification(context, s);
        } else {
            Log.d(TAG, "onReceive: won't show notification");
        }

        AlarmUtil.setupAlarmIfRequired(context);
    }

    private static void showNotification(Context context, String s) {
        Log.d(TAG, "showNotification");

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentTitle(context.getResources().getString(R.string.app_name));
        builder.setContentText(s);
        builder.setSmallIcon(R.drawable.notification_icon);
        builder.setDefaults(Notification.DEFAULT_ALL);

        Intent mainActIntent = new Intent(context, MainActivity.class);
        PendingIntent mainActivityIntent = PendingIntent.getActivity(context, 0, mainActIntent, 0);
        builder.setContentIntent(mainActivityIntent);
        Notification notification = builder.build();
        notificationManager.notify(AlarmUtil.NOTIFICATION_TAG, 1, notification);
    }
}