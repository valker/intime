package com.vpe_soft.intime.intime.view_models;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;

import java.util.List;
import java.util.Locale;

public class TaskViewModel extends AndroidViewModel {
    private final TaskRepository taskRepository;
    private final LiveData<List<TaskEntity>> tasks;
    private final MutableLiveData<Long> currentTime = new MutableLiveData<>();
    private final MutableLiveData<Long> taskIdLiveData = new MutableLiveData<>();
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            currentTime.setValue(System.currentTimeMillis());
            clockHandler.postDelayed(this, 1000);
        }
    };

    // LiveData, содержащая задачу, которую мы должны подтвердить
    public final LiveData<TaskEntity> taskToAcknowledge = Transformations.switchMap(taskIdLiveData, this::getTaskById);

    public TaskViewModel(@NonNull Application application) {
        super(application);
        taskRepository = new TaskRepository(application);
        tasks = taskRepository.getAllTasks();

        // Запускаем таймер обновления времени каждую секунду
        startClock();
    }

    private void startClock() {
        clockTick.run();
    }

    @Override
    protected void onCleared() {
        clockHandler.removeCallbacks(clockTick);
        super.onCleared();
    }

    public LiveData<List<TaskEntity>> getTasks() {
        return tasks;
    }

    public LiveData<Long> getCurrentTime() {
        return currentTime;
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
//        LiveData<TaskEntity> task = getTaskById(taskId);
//        final TaskEntity value = task.getValue();
//        if(value != null) {
//            Locale locale = Locale.getDefault();
//            new Thread(() -> taskRepository.acknowledgeTask(taskId, currentTimeMillis, value.interval, value.amount, value.quant, locale)).start();
//        }
        taskIdLiveData.setValue(taskId);
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
