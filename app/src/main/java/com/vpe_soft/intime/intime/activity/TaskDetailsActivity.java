package com.vpe_soft.intime.intime.activity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.vpe_soft.intime.intime.R;

public class TaskDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_details);

        long taskId = getIntent().getLongExtra("task_id", -1);

        TextView textView = findViewById(R.id.task_details_text);
        textView.setText("Task ID: " + taskId);

        Button btnDelete = findViewById(R.id.btnDeleteTask);
        btnDelete.setOnClickListener(v -> showDeleteConfirmationDialog());
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
        if (task != null) {
            taskViewModel.deleteTask(task);
            Toast.makeText(this, "Задача удалена", Toast.LENGTH_SHORT).show();
            finish(); // Закрываем экран
        }
    }


}