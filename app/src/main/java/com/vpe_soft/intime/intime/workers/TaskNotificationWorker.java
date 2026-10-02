package com.vpe_soft.intime.intime.workers;

import android.app.Notification;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.ui.UiVisibility;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
import com.vpe_soft.intime.intime.notifications.NotificationHelper;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;

import java.util.List;

/**
 * Periodic reconciliation and silent repeats for overdue tasks.
 * Does not schedule alarms. Notifications open the task list and never include ACK.
 */
public class TaskNotificationWorker extends Worker {
    private final TaskRepository taskRepository;

    public TaskNotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        taskRepository = new TaskRepository(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Используем ту же Room-транзакцию, что receiver, до отметки всех задач.
        return AppDatabase.getInstance(getApplicationContext()).runInTransaction(this::notifyDueTasks);
    }

    private Result notifyDueTasks() {
        if (UiVisibility.isV2UiVisible()
                || !NotificationHelper.canPostTaskNotifications(getApplicationContext())) {
            return Result.success();
        }

        long now = System.currentTimeMillis();
        List<TaskEntity> pending = taskRepository.getTasksForNotification(now);
        List<TaskEntity> tasks = AppDatabase.getInstance(getApplicationContext())
                .taskDao().getOverdueTasks(now);

        if (tasks.isEmpty()) {
            return Result.success();
        }
        if (pending.isEmpty()
                && !NotificationHelper.isQuietReminderDue(getApplicationContext(), now)) {
            return Result.success();
        }

        if (!showNotification(tasks)) {
            return Result.success();
        }
        for (TaskEntity task : pending) {
            taskRepository.markTaskNotified(task.getId());
        }

        return Result.success();
    }

    private boolean showNotification(List<TaskEntity> tasks) {
        Context context = getApplicationContext();
        NotificationHelper.ensureTaskOverdueChannel(context);
        String contentText = getNotificationContentText(context, tasks);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.TASK_OVERDUE_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(context.getString(R.string.channel_name))
                .setContentText(contentText)
                .setContentIntent(NotificationHelper.createOpenTaskListPendingIntent(context))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true);

        final Notification notification = builder.build();
        return NotificationHelper.postTaskNotification(context, notification);
    }

    private String getNotificationContentText(Context context, List<TaskEntity> tasks) {
        TaskEntity firstTask = tasks.get(0);
        if (tasks.size() == 1) {
            return firstTask.getDescription();
        }
        return AlarmUtil.getNotificationString(context, firstTask.getDescription(), tasks.size());
    }
}
