package com.vpe_soft.intime.intime.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

import android.os.Build;
import android.util.Log;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.activity.MainActivity;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.DatabaseUtil;
import com.vpe_soft.intime.intime.database.InTimeOpenHelper;

/** 
 * Created by Valentin on 26.08.2015.
 * Receives notifications from AlarmManager about next alarm and pass it to MainActivity
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        String notificationString = null;
        long overdueTaskId = -1;
        try {
            notificationString = intent.getStringExtra(Constants.EXTRA_TASK_DESCRIPTION);
            overdueTaskId = intent.getLongExtra(Constants.EXTRA_TASK_ID, -1);
        } catch( Exception e) {
            Log.e(TAG, "onReceive: unexpected error", e);
        }


        // prevent empty description
        notificationString = notificationString == null || notificationString.length() == 0
                ? "unknown"
                : notificationString;

        final long currentTimeMillis = System.currentTimeMillis();
        try (InTimeOpenHelper openHelper = new InTimeOpenHelper(context)) {
            long overdueCount = DatabaseUtil.getNumberOfOverDueTasks(currentTimeMillis, openHelper);

            // if there are other overdue tasks, modify notification text to let user know about that
            if(overdueCount > 1) {
                notificationString = AlarmUtil.getNotificationString(context, notificationString, overdueCount);
            }

            Intent broadcastIntent = new Intent(Constants.TASK_OVERDUE_ACTION);
            broadcastIntent.putExtra(Constants.EXTRA_TASK_DESCRIPTION, notificationString);
            context.sendOrderedBroadcast(broadcastIntent, null);

            if(!MainActivity.isOnScreen) {
                Log.d(TAG, "onReceive: will show notification");
                showNotification(context, notificationString, overdueTaskId);
            } else {
                Log.d(TAG, "onReceive: won't show notification");
            }

            AlarmUtil.setupAlarmIfRequired(context, openHelper);
        }
    }

    private static void showNotification(Context context, String notificationString, long overdueTaskId) {
        Log.d(TAG, "showNotification");

        NotificationCompat.Builder builder;
        NotificationManager notificationManager;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // https://developer.android.com/training/notify-user/build-notification
            // gets notification manager (new style), creates notification channel and notification builder
            CharSequence name = context.getString(R.string.channel_name);
            String description = context.getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(Constants.TASK_OVERDUE_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            builder = new NotificationCompat.Builder(context, Constants.TASK_OVERDUE_CHANNEL_ID);
        } else {
            // gets notification manager (old style) and creates notification builder
            notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            builder = new NotificationCompat.Builder(context);
        }

        Notification notification = createNotification(context, notificationString, builder, overdueTaskId);
        notificationManager.notify(AlarmUtil.NOTIFICATION_TAG, 1, notification);

    }

    private static Notification createNotification(Context context, String contentText, NotificationCompat.Builder builder, long overdueTaskId) {
        builder.setContentTitle(context.getResources().getString(R.string.app_name));
        builder.setContentText(contentText);
        builder.setSmallIcon(R.drawable.notification_icon);
        builder.setDefaults(Notification.DEFAULT_ALL);

        Intent mainActIntent = new Intent(context, MainActivity.class);
        PendingIntent mainActivityIntent =
                PendingIntent.getActivity(context, 0, mainActIntent, PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(mainActivityIntent);
        if(overdueTaskId >= 0) {
            Intent ackTaskIntent = new Intent(context, AckReceiver.class);
            ackTaskIntent.setAction(Constants.ACTION_ACKNOWLEDGE);
            ackTaskIntent.putExtra(Constants.EXTRA_TASK_ID, overdueTaskId);
            PendingIntent acknowledgePendingIntent = PendingIntent.getBroadcast(context,
                                                                                0,
                                                                                ackTaskIntent,
                                                                                PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.acknowledge,
                              context.getString(R.string.acknowledge_from_notification),
                              acknowledgePendingIntent);
        }

        return builder.build();
    }
}