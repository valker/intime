package com.vpe_soft.intime.intime.receiver;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.notifications.NotificationHelper;
import com.vpe_soft.intime.intime.scheduling.SchedulingCoordinator;

import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        final String intentAction = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intentAction)) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                handleBootCompleted(context);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private static void handleBootCompleted(Context context) {
        Log.d(TAG, "handleBootCompleted");
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                Constants.SESSION_INFO_SP_NAME,
                Context.MODE_PRIVATE);
        final long lastUsageTimestamp = sharedPreferences.getLong(Constants.LAST_USAGE_TIMESTAMP_KEY, 0);
        final long currentTimestamp = System.currentTimeMillis();

        TaskDao taskDao = AppDatabase.getInstance(context).taskDao();
        final int skippedTasks = taskDao.countSkippedTasks(lastUsageTimestamp, currentTimestamp);
        if (skippedTasks > 0) {
            Log.d(TAG, "handleBootCompleted: overdue tasks were found while device was off");
            showBootNotification(context);
        } else {
            Log.d(TAG, "handleBootCompleted: no skipped overdue tasks");
        }

        SchedulingCoordinator.reschedule(context);
    }

    private static void showBootNotification(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "showBootNotification: notification permission is not granted");
            return;
        }

        NotificationHelper.ensureTaskOverdueChannel(context);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.TASK_OVERDUE_CHANNEL_ID);
        builder.setContentTitle(context.getString(R.string.channel_name));
        builder.setContentText(context.getString(R.string.boot_completed_overdue_tasks_notification));
        builder.setSmallIcon(R.drawable.notification_icon);
        builder.setContentIntent(NotificationHelper.createOpenTaskListPendingIntent(context));
        Notification notification = builder.build();
        notificationManager.notify(AlarmUtil.NOTIFICATION_TAG, 1, notification);
    }
}
