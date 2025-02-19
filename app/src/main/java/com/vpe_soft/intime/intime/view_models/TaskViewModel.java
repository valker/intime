package com.vpe_soft.intime.intime.view_models;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TaskViewModel extends ViewModel {
    private final TaskDao taskDao;
    private final LiveData<List<TaskEntity>> tasks;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public TaskViewModel(TaskDao taskDao) {
        this.taskDao = taskDao;
        this.tasks = taskDao.getAllTasks();
    }

    public LiveData<List<TaskEntity>> getTasks() {
        return tasks;
    }

    public  void insertTask(TaskEntity task) {
        executor.execute(() -> taskDao.insert(task));
    }

    public void updateTask(TaskEntity task) {
        executor.execute(() -> taskDao.update(task));
    }

    public void deleteTask(TaskEntity task) {
        executor.execute(() -> taskDao.delete(task));
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final TaskDao taskDao;

        public Factory(TaskDao taskDao) {
            this.taskDao = taskDao;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (modelClass.isAssignableFrom(TaskViewModel.class)) {
                return (T) new TaskViewModel(taskDao);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}
