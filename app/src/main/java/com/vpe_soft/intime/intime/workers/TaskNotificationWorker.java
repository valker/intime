package com.vpe_soft.intime.intime.workers;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
import com.vpe_soft.intime.intime.notifications.NotificationHelper;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;

import java.util.List;

public class TaskNotificationWorker extends Worker {
    private final TaskRepository taskRepository;

    public TaskNotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        taskRepository = new TaskRepository(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return Result.success();
            }
        }

        long now = System.currentTimeMillis();
        List<TaskEntity> tasks = taskRepository.getTasksForNotification(now);

        if (tasks.isEmpty()) {
            return Result.success();
        }

        showNotification(tasks);
        for (TaskEntity task : tasks) {
            taskRepository.markTaskNotified(task.getId());
        }

        return Result.success();
    }

    @SuppressLint("MissingPermission")
    private void showNotification(List<TaskEntity> tasks) {
        Context context = getApplicationContext();
        NotificationHelper.ensureTaskOverdueChannel(context);
        String contentText = getNotificationContentText(context, tasks);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.TASK_OVERDUE_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(context.getString(R.string.channel_name))
                .setContentText(contentText)
                .setContentIntent(NotificationHelper.createOpenTaskListPendingIntent(context))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        final Notification notification = builder.build();
        manager.notify(AlarmUtil.NOTIFICATION_TAG, 1, notification);
    }

    private String getNotificationContentText(Context context, List<TaskEntity> tasks) {
        TaskEntity firstTask = tasks.get(0);
        if (tasks.size() == 1) {
            return firstTask.getDescription();
        }
        return AlarmUtil.getNotificationString(context, firstTask.getDescription(), tasks.size());
    }
}
