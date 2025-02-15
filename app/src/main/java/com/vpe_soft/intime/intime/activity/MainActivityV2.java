package com.vpe_soft.intime.intime.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.adapters.TaskAdapter;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.view_models.TaskViewModel;

public class MainActivityV2 extends AppCompatActivity {
    private TaskViewModel taskViewModel;
    private TaskAdapter taskAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Создаём адаптер и передаём обработчик кликов
        taskAdapter = new TaskAdapter(task -> {
            // TODO: открыть экран редактирования
        });
        recyclerView.setAdapter(taskAdapter);

        // Инициализируем ViewModel
        AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "tasks.db").build();
        TaskDao taskDao = db.taskDao();
        taskViewModel = new ViewModelProvider(this, new TaskViewModel.Factory(taskDao)).get(TaskViewModel.class);

        // Подписываемся на обновления списка задач
        taskViewModel.getTasks().observe(this, taskAdapter::submitList);
    }
}
