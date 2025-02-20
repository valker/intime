package com.vpe_soft.intime.intime.activity;

import android.os.Bundle;
import android.util.Pair;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;
import com.vpe_soft.intime.intime.view_models.TaskViewModel;

public class AddTaskActivity extends AppCompatActivity {
    private EditText editTaskDescription;
    private Button btnSaveTask;
    private TaskViewModel taskViewModel;
    private Spinner spinnerInterval;
    private NumberPicker numberPickerAmount;
    private NumberPicker numberPickerQuant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        editTaskDescription = findViewById(R.id.edit_task_description);
        btnSaveTask = findViewById(R.id.btn_save_task);

        TaskViewModel.Factory factory = new TaskViewModel.Factory(getApplication());
        taskViewModel = new ViewModelProvider(this, factory).get(TaskViewModel.class);

        // Настроим Spinner (interval)
        spinnerInterval = findViewById(R.id.spinner_interval);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.interval_options, // Создадим в ресурсах
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

        btnSaveTask.setOnClickListener(view -> saveTask());
    }

    private void saveTask() {
        String description = editTaskDescription.getText().toString().trim();
        if (description.isEmpty()) {
            Toast.makeText(this, "Введите описание задачи", Toast.LENGTH_SHORT).show();
            return;
        }

        int interval = spinnerInterval.getSelectedItemPosition(); // 0 - минута, 1 - час, 2 - день и т.д.
        int amount = numberPickerAmount.getValue(); // Количество интервалов
        int quant = numberPickerQuant.getValue();   // Дробление интервала

        final long lastAck = System.currentTimeMillis();
        Pair<Long, Long> next = AlarmUtil.getNextAlarmAndCaution(interval, amount, lastAck, quant, getResources().getConfiguration().locale);

        TaskEntity newTask = new TaskEntity(description, interval, amount, next.first, next.second, 0, quant);
        taskViewModel.addTask(newTask);

        Toast.makeText(this, "Задача добавлена", Toast.LENGTH_SHORT).show();
        finish(); // Закрываем экран после сохранения
    }
}
