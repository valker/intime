package com.vpe_soft.intime.intime.view_models;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;

import java.util.List;

public class TaskViewModel extends AndroidViewModel {
    private final TaskRepository taskRepository;
    private final LiveData<List<TaskEntity>> tasks;

    public TaskViewModel(@NonNull Application application) {
        super(application);
        taskRepository = new TaskRepository(application);
        tasks = taskRepository.getAllTasks();
    }

    public LiveData<List<TaskEntity>> getTasks() {
        return tasks;
    }

    public LiveData<TaskEntity> getTaskById(long taskId) {
        return taskRepository.getTaskById(taskId);
    }

    public  void addTask(TaskEntity task) {
        taskRepository.insert(task);
    }

    public void updateTask(TaskEntity task) {
        taskRepository.update(task);
    }

    public void deleteTask(TaskEntity task) {
        taskRepository.delete(task);
    }

    public void deleteTaskById(long taskId) {
        taskRepository.deleteTaskById(taskId);
    }

    public void ack(long taskId, long currentTimeMillis) {
        LiveData<TaskEntity> task = getTaskById(taskId);
        final TaskEntity value = task.getValue();
        if(value != null) {
            value.nextAlarm = currentTimeMillis + 100000;
            taskRepository.update(value);
        }
    }

    public static class Factory implements ViewModelProvider.Factory {
        Application application;

        public Factory(Application application) {
            this.application = application;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (modelClass.isAssignableFrom(TaskViewModel.class)) {
                return (T) new TaskViewModel(application);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}
