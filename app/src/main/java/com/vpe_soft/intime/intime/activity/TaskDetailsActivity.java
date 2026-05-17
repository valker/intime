package com.vpe_soft.intime.intime.activity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
import com.vpe_soft.intime.intime.view_models.TaskViewModel;

public class TaskDetailsActivity extends AppCompatActivity {

    private TaskViewModel taskViewModel;
    private long taskId;
    private TaskRepository taskRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        taskRepository = new TaskRepository(getApplication());
        setContentView(R.layout.activity_task_details);

        taskId = getIntent().getLongExtra("task_id", -1);

        TaskViewModel.Factory factory = new TaskViewModel.Factory(getApplication());
        taskViewModel = new ViewModelProvider(this, factory)
                .get(TaskViewModel.class);

        taskViewModel.getTaskById(taskId).observe(this, task -> {
            if (task != null) {
                // Обновляем UI данными из task
                TextView textView = findViewById(R.id.task_description);
                textView.setText(task.getDescription());
                textView = findViewById(R.id.task_id);
                textView.setText("Task ID: " + taskId);

//                textViewTitle.setText(task.getTitle());
//                textViewDescription.setText(task.getDescription());
                // Заполняем другие поля...
            }
        });


        Button btnDelete = findViewById(R.id.btnDeleteTask);
        btnDelete.setOnClickListener(v -> showDeleteConfirmationDialog());

        Button btnAck = findViewById(R.id.btnAckTask);
        btnAck.setOnClickListener(v -> {
            taskRepository.acknowledgeTaskAsync(taskId);
            finish();
        });
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Удалить задачу?")
                .setMessage("Вы уверены, что хотите удалить эту задачу?")
                .setPositiveButton("Удалить", (dialog, which) -> deleteTask())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deleteTask() {
        if (taskId != -1) {
            taskViewModel.deleteTaskById(taskId);
            Toast.makeText(this, "Задача удалена", Toast.LENGTH_SHORT).show();
            finish(); // Закрываем экран
        }
    }
}