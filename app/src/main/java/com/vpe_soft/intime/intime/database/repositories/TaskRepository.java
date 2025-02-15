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

    public TaskRepository(Application application) {
        AppDatabase db = Room.databaseBuilder(application, AppDatabase.class, Constants.dbName)
                .fallbackToDestructiveMigrationFrom(5)
                .build();
        taskDao = db.taskDao();
    }

    public LiveData<List<TaskEntity>> getAllTasks() {
        return taskDao.getAllTasks();
    }

    public void insert(TaskEntity task) {
        Executors.newSingleThreadExecutor().execute(() -> taskDao.insert(task));
    }

    public void update(TaskEntity task) {
        Executors.newSingleThreadExecutor().execute(() -> taskDao.update(task));
    }

    public void delete(TaskEntity task) {
        Executors.newSingleThreadExecutor().execute(() -> taskDao.delete(task));
    }
}

