package com.vpe_soft.intime.intime.notifications;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.activity.MainActivityV2;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;

public class NotificationHelper {
    private static final String REMINDER_STATE = "notification_reminder_state";
    private static final String LAST_POST = "last_successful_post";
    private static final long QUIET_INTERVAL_MILLIS = java.util.concurrent.TimeUnit.MINUTES.toMillis(15);
    private NotificationHelper() {
    }

    /** Проверяем разрешение, общий запрет и настройки канала перед отправкой. */
    public static boolean canPostTaskNotifications(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) {
                return false;
            }
            NotificationChannel channel = manager.getNotificationChannel(Constants.TASK_OVERDUE_CHANNEL_ID);
            if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                return false;
            }
        }
        return true;
    }

    /** true означает, что notify выполнен; это не подтверждение прочтения пользователем. */
    @SuppressLint("MissingPermission") // Разрешение проверяется в canPostTaskNotifications.
    public static boolean postTaskNotification(Context context, Notification notification) {
        if (!canPostTaskNotifications(context)) {
            return false;
        }
        try {
            NotificationManagerCompat.from(context).notify(
                    AlarmUtil.NOTIFICATION_TAG, 1, notification);
            // Повторный worker должен учитывать и первую отправку receiver/boot.
            context.getSharedPreferences(REMINDER_STATE, Context.MODE_PRIVATE).edit()
                    .putLong(LAST_POST, System.currentTimeMillis()).apply();
            return true;
        } catch (SecurityException e) {
            // Разрешение могло быть отозвано между проверкой и отправкой.
            Log.w("NotificationHelper", "Notification permission changed before posting", e);
            return false;
        }
    }

    /** Проверка паузы для спокойных повторов; новые задачи не зависят от этой паузы. */
    public static boolean isQuietReminderDue(Context context, long now) {
        long lastPost = context.getSharedPreferences(REMINDER_STATE, Context.MODE_PRIVATE)
                .getLong(LAST_POST, 0);
        // При переводе часов назад не блокируем повторы на неопределённое время.
        return lastPost == 0 || now < lastPost || now - lastPost >= QUIET_INTERVAL_MILLIS;
    }

    public static void ensureTaskOverdueChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        CharSequence name = context.getString(R.string.channel_name);
        String description = context.getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                Constants.TASK_OVERDUE_CHANNEL_ID,
                name,
                importance
        );
        channel.setDescription(description);

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    public static PendingIntent createOpenTaskListPendingIntent(Context context) {
        Intent intent = new Intent(context, MainActivityV2.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    /**
     * Removes all notifications posted by this app. Called when the user opens
     * any v2 screen (launcher or notification tap).
     */
    public static void dismissAllAppNotifications(Context context) {
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        notificationManager.cancelAll();
    }
}
