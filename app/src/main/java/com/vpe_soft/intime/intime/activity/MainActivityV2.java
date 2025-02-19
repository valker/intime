package com.vpe_soft.intime.intime.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.adapters.TaskAdapter;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.AppDatabase_Impl;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.view_models.TaskViewModel;

public class MainActivityV2 extends AppCompatActivity {
    private TaskViewModel taskViewModel;
    private TaskAdapter taskAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Создаём адаптер и передаём обработчик кликов
        taskAdapter = new TaskAdapter(task -> {
            Intent intent = new Intent(MainActivityV2.this, TaskDetailsActivity.class);
            intent.putExtra("task_id", task.getId()); // Передаём ID задачи
            startActivity(intent);
        });
        recyclerView.setAdapter(taskAdapter);

        // Инициализируем ViewModel
        AppDatabase db = AppDatabase.getInstance(this);
        TaskDao taskDao = db.taskDao();
        taskViewModel = new ViewModelProvider(this, new TaskViewModel.Factory(taskDao)).get(TaskViewModel.class);

        // Подписываемся на обновления списка задач
        taskViewModel.getTasks().observe(this, taskAdapter::submitList);

        FloatingActionButton fabAddTask = findViewById(R.id.fab_add_task);
        fabAddTask.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivityV2.this, AddTaskActivity.class);
            startActivity(intent);
        });
    }
}
