package com.vpe_soft.intime.intime.activity;

import android.os.Bundle;
import android.util.Pair;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;
import com.vpe_soft.intime.intime.view_models.TaskViewModel;

public class AddTaskActivity extends V2Activity {
    private EditText editTaskDescription;
    private Button btnSaveTask;
    private TaskViewModel taskViewModel;
    private Spinner spinnerInterval;
    private NumberPicker numberPickerAmount;
    private NumberPicker numberPickerQuant;
    private long editTaskId = -1;
    private long existingLastAck = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);
        setupMainScreenBackButton();

        editTaskDescription = findViewById(R.id.edit_task_description);
        btnSaveTask = findViewById(R.id.btn_save_task);

        TaskViewModel.Factory factory = new TaskViewModel.Factory(getApplication());
        taskViewModel = new ViewModelProvider(this, factory).get(TaskViewModel.class);

        // Настроим Spinner (interval)
        spinnerInterval = findViewById(R.id.spinner_interval);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.interval_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInterval.setAdapter(adapter);

        // Настроим NumberPicker для amount
        numberPickerAmount = findViewById(R.id.number_picker_amount);
        numberPickerAmount.setMinValue(1);
        numberPickerAmount.setMaxValue(365);
        numberPickerAmount.setValue(1);

        // Настроим NumberPicker для quant
        numberPickerQuant = findViewById(R.id.number_picker_quant);
        numberPickerQuant.setMinValue(1);
        numberPickerQuant.setMaxValue(10);
        numberPickerQuant.setValue(1);

        editTaskId = getIntent().getLongExtra("task_id", -1);
        if (editTaskId != -1) {
            setTitle(R.string.edit_task_activity_title);
            taskViewModel.getTaskById(editTaskId).observe(this, task -> {
                if (task != null) {
                    editTaskDescription.setText(task.description);
                    spinnerInterval.setSelection(task.interval);
                    numberPickerAmount.setValue(task.amount);
                    numberPickerQuant.setValue(task.quant);
                    existingLastAck = task.lastAck;
                }
            });
        }

        btnSaveTask.setOnClickListener(view -> saveTask());
    }

    private void saveTask() {
        String description = editTaskDescription.getText().toString().trim();
        if (description.isEmpty()) {
            Toast.makeText(this, "Введите описание задачи", Toast.LENGTH_SHORT).show();
            return;
        }

        int interval = spinnerInterval.getSelectedItemPosition();
        int amount = numberPickerAmount.getValue();
        int quant = numberPickerQuant.getValue();

        final long ackTime = (editTaskId != -1 && existingLastAck > 0) ? existingLastAck : System.currentTimeMillis();
        Pair<Long, Long> next = AlarmUtil.getNextAlarmAndCaution(interval, amount, ackTime, quant, getResources().getConfiguration().locale);

        if (editTaskId != -1) {
            TaskEntity updatedTask = new TaskEntity(description, interval, amount, next.first, next.second, existingLastAck, quant);
            updatedTask.setId(editTaskId);
            taskViewModel.updateTask(updatedTask);
            Toast.makeText(this, "Задача обновлена", Toast.LENGTH_SHORT).show();
        } else {
            TaskEntity newTask = new TaskEntity(description, interval, amount, next.first, next.second, 0, quant);
            taskViewModel.addTask(newTask);
            Toast.makeText(this, "Задача добавлена", Toast.LENGTH_SHORT).show();
        }

        finish();
    }
}
