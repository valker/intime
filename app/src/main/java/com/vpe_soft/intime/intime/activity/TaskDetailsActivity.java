package com.vpe_soft.intime.intime.activity;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.vpe_soft.intime.intime.R;

public class TaskDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_details);

        int taskId = getIntent().getIntExtra("task_id", -1);

        TextView textView = findViewById(R.id.task_details_text);
        textView.setText("Task ID: " + taskId);
    }
}