package com.vpe_soft.intime.intime.database.repositories;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import androidx.lifecycle.LiveData;

import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.import_export.BackupExport;
import com.vpe_soft.intime.intime.import_export.ImportReplacement;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;
import com.vpe_soft.intime.intime.scheduling.SchedulingCoordinator;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class TaskRepository {
    private static final String TAG = "TaskRepository";

    private final AppDatabase db;
    private final TaskDao taskDao;
    private final Context appContext;
    private LiveData<List<TaskEntity>> allTasks;

    public TaskRepository(Context context) {
        appContext = context.getApplicationContext();
        db = AppDatabase.getInstance(context);
        taskDao = db.taskDao();
        allTasks = taskDao.getAllTasks();
    }

    public LiveData<List<TaskEntity>> getAllTasks() {
        return allTasks;
    }

    public LiveData<TaskEntity> getTaskById(long taskId) {
        return taskDao.getTaskById(taskId);
    }

    public void insert(TaskEntity task) {
        Executors.newSingleThreadExecutor().execute(() -> {
            task.setId(taskDao.insert(task));
            rescheduleNextAlarm();
        });
    }

    public void update(TaskEntity task) {
        Executors.newSingleThreadExecutor().execute(() -> {
            taskDao.update(task);
            rescheduleNextAlarm();
        });
    }

    public void delete(TaskEntity task) {
        Executors.newSingleThreadExecutor().execute(() -> {
            taskDao.delete(task);
            rescheduleNextAlarm();
        });
    }

    public void deleteTaskById(long taskId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            final TaskEntity taskById = taskDao.getRawTaskById(taskId);
            if (taskById != null) {
                taskDao.delete(taskById);
                rescheduleNextAlarm();
            }
        });
    }

    /**
     * Acknowledges a task on a background thread. Safe to call from broadcast receivers and UI.
     */
    public void acknowledgeTaskAsync(long taskId) {
        Executors.newSingleThreadExecutor().execute(() -> acknowledgeTaskById(taskId));
    }

    /**
     * Must run on a background thread (uses Room and schedules alarms).
     */
    public void acknowledgeTaskById(long taskId) {
        TaskEntity task = taskDao.getRawTaskById(taskId);
        if (task == null) {
            Log.w(TAG, "acknowledgeTaskById: task not found, id=" + taskId);
            return;
        }
        Locale locale = resolveLocale();
        acknowledgeTask(
                taskId,
                System.currentTimeMillis(),
                task.interval,
                task.amount,
                task.quant,
                locale
        );
    }

    public void acknowledgeTask(long taskId, long currentTimeMillis, int interval, int amount, int quant, Locale locale) {
        final Pair<Long, Long> nextAlarmAndCaution = AlarmUtil.getNextAlarmAndCaution(interval, amount, currentTimeMillis, quant, locale);

        taskDao.acknowledgeTask(taskId, currentTimeMillis, nextAlarmAndCaution.first, nextAlarmAndCaution.second);
        rescheduleNextAlarm();
    }

    public List<TaskEntity> getTasksForNotification(long now) {
        return taskDao.getTasksForNotification(now);
    }

    public void markTaskNotified(long taskId) {
        taskDao.markTaskNotified(taskId);
    }

    /**
     * Builds backup JSON on a background thread; callbacks run on the main thread.
     */
    public void exportAllTasksToJson(
            java.util.function.Consumer<String> onJsonReady,
            java.util.function.Consumer<Exception> onError) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json = BackupExport.toJson(taskDao.getAllTasksSync());
                mainHandler.post(() -> onJsonReady.accept(json));
            } catch (Exception e) {
                mainHandler.post(() -> onError.accept(e));
            }
        });
    }

    /**
     * Replaces all tasks with those from backup JSON. Runs on background; callbacks are invoked on main thread.
     */
    public void replaceAllWithImportFromJson(Context context, String jsonContent, Runnable onSuccess, java.util.function.Consumer<Exception> onError) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ImportReplacement.replaceAll(db, jsonContent);
                rescheduleNextAlarm();
                mainHandler.post(onSuccess);
            } catch (Exception e) {
                mainHandler.post(() -> onError.accept(e));
            }
        });
    }

    private Locale resolveLocale() {
        if (!appContext.getResources().getConfiguration().getLocales().isEmpty()) {
            return appContext.getResources().getConfiguration().getLocales().get(0);
        }
        return Locale.getDefault();
    }

    private void rescheduleNextAlarm() {
        SchedulingCoordinator.reschedule(appContext);
    }
}

