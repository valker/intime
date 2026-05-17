package com.vpe_soft.intime.intime.activity;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.Toast;
import android.os.Build;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.adapters.TaskAdapter;
import com.vpe_soft.intime.intime.scheduling.SchedulingCoordinator;
import com.vpe_soft.intime.intime.view_models.TaskViewModel;
import com.vpe_soft.intime.intime.workers.TaskNotificationWorker;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivityV2 extends AppCompatActivity {
    private TaskViewModel taskViewModel;
    private TaskAdapter taskAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        // Создаём адаптер и передаём обработчик кликов
        taskAdapter = new TaskAdapter(task -> {
            Intent intent = new Intent(MainActivityV2.this, TaskDetailsActivity.class);
            intent.putExtra("task_id", task.getId()); // Передаём ID задачи
            startActivity(intent);
        });
        recyclerView.setAdapter(taskAdapter);

        // Инициализируем ViewModel
        taskViewModel = new ViewModelProvider(this, new TaskViewModel.Factory(getApplication()))
                .get(TaskViewModel.class);

        // Подписываемся на обновления списка задач
        taskViewModel.getTasks().observe(this, tasks -> {
            Log.d("DEBUG", "Tasks updated: " + tasks.size());
            taskAdapter.submitList(tasks);
        });

        taskViewModel.getCurrentTime().observe(this, currentTime -> {
            taskAdapter.notifyDataSetChanged();
        });

        FloatingActionButton fabAddTask = findViewById(R.id.fab_add_task);
        fabAddTask.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivityV2.this, AddTaskActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btn_open_settings).setOnClickListener(view -> {
            startActivity(new Intent(MainActivityV2.this, SettingsActivity.class));
        });

        // проверяем наличие разрешений, на отправку уведомлений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_NOTIFICATIONS);
            }
        }

        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(TaskNotificationWorker.class.getName())
                .observe(this, workInfos -> {
                    if (workInfos != null && !workInfos.isEmpty()) {
                        Log.d("WorkManager", "Worker state: " + workInfos.get(0).getState());
                    } else {
                        Log.d("WorkManager", "Worker не запущен!");
                    }
                });

        Executors.newSingleThreadExecutor().execute(() -> SchedulingCoordinator.reschedule(getApplicationContext()));

        // запускаем воркер отправки уведомлений
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(TaskNotificationWorker.class.getName(), ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(TaskNotificationWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                                .setRequiresBatteryNotLow(true)
                                .build())
                        .build()
        );

    }

    // обработка результата о запросе уведомлений
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Разрешение на уведомления получено");
            } else {
                Toast.makeText(this, "Уведомления отключены. Вы можете включить их в настройках.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private static final int REQUEST_CODE_NOTIFICATIONS = 1;

    @Override
    protected void onResume() {
        super.onResume();
        MainActivity.isOnScreen = true;
        Executors.newSingleThreadExecutor().execute(
                () -> SchedulingCoordinator.reschedule(getApplicationContext()));
    }

    @Override
    protected void onPause() {
        MainActivity.isOnScreen = false;
        SharedPreferences sharedPreferences = getSharedPreferences(
                Constants.SESSION_INFO_SP_NAME,
                Context.MODE_PRIVATE);
        sharedPreferences.edit()
                .putLong(Constants.LAST_USAGE_TIMESTAMP_KEY, System.currentTimeMillis())
                .apply();
        super.onPause();
    }
}
