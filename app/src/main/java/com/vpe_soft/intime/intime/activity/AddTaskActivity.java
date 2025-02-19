package com.vpe_soft.intime.intime.activity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.view_models.TaskViewModel;

public class AddTaskActivity extends AppCompatActivity {
    private EditText editTaskDescription;
    private Button btnSaveTask;
    private TaskViewModel taskViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        editTaskDescription = findViewById(R.id.edit_task_description);
        btnSaveTask = findViewById(R.id.btn_save_task);

        TaskDao taskDao = AppDatabase.getInstance(this).taskDao(); // Получаем DAO
        TaskViewModel.Factory factory = new TaskViewModel.Factory(taskDao); // Передаем DAO в фабрику
        taskViewModel = new ViewModelProvider(this, factory).get(TaskViewModel.class);

        btnSaveTask.setOnClickListener(view -> saveTask());
    }

    private void saveTask() {
        String description = editTaskDescription.getText().toString().trim();
        if (description.isEmpty()) {
            Toast.makeText(this, "Введите описание задачи", Toast.LENGTH_SHORT).show();
            return;
        }

        TaskEntity newTask = new TaskEntity(description, System.currentTimeMillis());
        taskViewModel.insertTask(newTask);

        finish(); // Закрываем экран после сохранения
    }
}
