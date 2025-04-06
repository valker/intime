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

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
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
                return Result.failure(); // Нельзя отправлять уведомления без разрешения
            }
        }

        long now = System.currentTimeMillis();
        List<TaskEntity> tasks = taskRepository.getTasksForNotification(now);

        for (TaskEntity task : tasks) {
            showNotification(task);
        }

        return Result.success();
    }

    @SuppressLint("MissingPermission")
    private void showNotification(TaskEntity task) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "TASK_CHANNEL")
                .setSmallIcon(R.drawable.app_icon/*.ic_alarm*/)
                .setContentTitle("Задача просрочена!")
                .setContentText(task.getDescription())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat manager = NotificationManagerCompat.from(getApplicationContext());
        final Notification notification = builder.build();
        manager.notify(AlarmUtil.NOTIFICATION_TAG, 1, notification);
    }
}
