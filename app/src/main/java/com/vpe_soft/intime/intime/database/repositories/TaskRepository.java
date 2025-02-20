package com.vpe_soft.intime.intime.database.repositories;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.room.Room;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import java.util.List;
import java.util.concurrent.Executors;

public class TaskRepository {
    private final TaskDao taskDao;
    private LiveData<List<TaskEntity>> allTasks;

    public TaskRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
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
        Executors.newSingleThreadExecutor().execute(() -> task.setId(taskDao.insert(task)));
    }

    public void update(TaskEntity task) {
        Executors.newSingleThreadExecutor().execute(() -> taskDao.update(task));
    }

    public void delete(TaskEntity task) {
        Executors.newSingleThreadExecutor().execute(() -> taskDao.delete(task));
    }

    public void deleteTaskById(long taskId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            final TaskEntity taskById = taskDao.getRawTaskById(taskId);
            taskDao.delete(taskById);
        });
    }
}

