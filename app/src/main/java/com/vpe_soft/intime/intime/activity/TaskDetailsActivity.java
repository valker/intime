package com.vpe_soft.intime.intime.activity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
import com.vpe_soft.intime.intime.view_models.TaskViewModel;

public class TaskDetailsActivity extends V2Activity {

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
                .setTitle(R.string.delete_task_title)
                .setMessage(R.string.delete_task_message)
                .setPositiveButton(R.string.delete_task_confirm, (dialog, which) -> deleteTask())
                .setNegativeButton(R.string.delete_task_cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteTask() {
        if (taskId != -1) {
            taskViewModel.deleteTaskById(taskId);
            Toast.makeText(this, R.string.task_deleted, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}