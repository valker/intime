package com.vpe_soft.intime.intime.database.repositories;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import androidx.lifecycle.LiveData;

import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.InTimeOpenHelper;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.import_export.BackupImport;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class TaskRepository {
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

    public void acknowledgeTask(long taskId, long currentTimeMillis, int interval, int amount, int quant, Locale locale) {
        //long nextAlarm = calculateNextTime(currentTimeMillis, interval, amount, quant);
        //long nextCaution = calculateNextTime(currentTimeMillis, interval, amount, quant / 2);
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
     * Replaces all tasks with those from backup JSON. Runs on background; callbacks are invoked on main thread.
     */
    public void replaceAllWithImportFromJson(Context context, String jsonContent, Runnable onSuccess, java.util.function.Consumer<Exception> onError) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<TaskEntity> tasks = BackupImport.parseTasks(jsonContent);
                db.runInTransaction(() -> {
                    taskDao.deleteAll();
                    if (!tasks.isEmpty()) {
                        taskDao.insertAll(tasks);
                    }
                });
                rescheduleNextAlarm();
                mainHandler.post(onSuccess);
            } catch (Exception e) {
                mainHandler.post(() -> onError.accept(e));
            }
        });
    }

    private void rescheduleNextAlarm() {
        try (InTimeOpenHelper openHelper = new InTimeOpenHelper(appContext)) {
            AlarmUtil.setupAlarmIfRequired(appContext, openHelper);
        }
    }
}

