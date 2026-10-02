package com.vpe_soft.intime.intime.receiver;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

import android.os.Build;
import android.net.Uri;
import android.util.Log;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.ui.UiVisibility;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.notifications.NotificationHelper;
import com.vpe_soft.intime.intime.scheduling.SchedulingCoordinator;

import java.util.concurrent.Executors;

/**
 * Receives AlarmManager callbacks when the nearest scheduled task becomes due.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        final PendingResult pendingResult = goAsync();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                handleAlarm(context, intent);
            } finally {
                pendingResult.finish();
            }
        });
    }

    static void handleAlarm(Context context, Intent intent) {
        String notificationString = intent.getStringExtra(Constants.EXTRA_TASK_DESCRIPTION);
        long overdueTaskId = intent.getLongExtra(Constants.EXTRA_TASK_ID, -1);

        notificationString = notificationString == null || notificationString.isEmpty()
                ? "unknown"
                : notificationString;

        final long currentTimeMillis = System.currentTimeMillis();
        TaskDao taskDao = AppDatabase.getInstance(context).taskDao();
        int overdueCount = taskDao.countOverdueTasks(currentTimeMillis);

        if (overdueCount > 1) {
            notificationString = AlarmUtil.getNotificationString(context, notificationString, overdueCount);
        }

        Intent broadcastIntent = new Intent(Constants.TASK_OVERDUE_ACTION);
        broadcastIntent.putExtra(Constants.EXTRA_TASK_DESCRIPTION, notificationString);
        context.sendOrderedBroadcast(broadcastIntent, null);

        if (!UiVisibility.isV2UiVisible()) {
            Log.d(TAG, "handleAlarm: trying to post notification");
            boolean posted = showNotification(context, notificationString, overdueTaskId);
            if (posted && overdueTaskId >= 0) {
                taskDao.markTaskNotified(overdueTaskId);
            }
        } else {
            Log.d(TAG, "handleAlarm: won't show notification");
        }

        SchedulingCoordinator.reschedule(context);
    }

    private static boolean showNotification(Context context, String notificationString, long overdueTaskId) {
        Log.d(TAG, "showNotification");

        NotificationCompat.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationHelper.ensureTaskOverdueChannel(context);
            builder = new NotificationCompat.Builder(context, Constants.TASK_OVERDUE_CHANNEL_ID);
        } else {
            builder = new NotificationCompat.Builder(context);
        }

        Notification notification = createNotification(context, notificationString, builder, overdueTaskId);
        return NotificationHelper.postTaskNotification(context, notification);
    }

    static Notification createNotification(Context context, String contentText, NotificationCompat.Builder builder, long overdueTaskId) {
        builder.setContentTitle(context.getResources().getString(R.string.app_name));
        builder.setContentText(contentText);
        builder.setSmallIcon(R.drawable.notification_icon);
        builder.setDefaults(Notification.DEFAULT_ALL);

        builder.setContentIntent(NotificationHelper.createOpenTaskListPendingIntent(context));
        if (overdueTaskId >= 0) {
            Intent ackTaskIntent = new Intent(context, AckReceiver.class);
            ackTaskIntent.setAction(Constants.ACTION_ACKNOWLEDGE);
            // Extras не участвуют в идентичности PendingIntent: ID задаём также в URI.
            ackTaskIntent.setData(Uri.parse("intime://ack/task/" + overdueTaskId));
            ackTaskIntent.putExtra(Constants.EXTRA_TASK_ID, overdueTaskId);
            PendingIntent acknowledgePendingIntent = PendingIntent.getBroadcast(context,
                    0,
                    ackTaskIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(R.drawable.acknowledge,
                    context.getString(R.string.acknowledge_from_notification),
                    acknowledgePendingIntent);
        }

        return builder.build();
    }
}
