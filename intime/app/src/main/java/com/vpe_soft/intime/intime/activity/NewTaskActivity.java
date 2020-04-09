package com.vpe_soft.intime.intime.activity;

import android.content.ContentValues;
import android.content.Intent;
import androidx.appcompat.widget.Toolbar;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Spinner;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.Task;
import com.vpe_soft.intime.intime.database.InTimeOpenHelper;
import com.vpe_soft.intime.intime.util.Util;

import java.util.Objects;


abstract class TaskViewModel {
    final NewTaskActivity _parent;

    TaskViewModel(NewTaskActivity parent) {
        _parent = parent;
    }

    void OnClick() {
        NumberPicker np = _parent.findViewById(R.id.numberPicker);
        int amount = np.getValue();
        Spinner spinner = _parent.findViewById(R.id.spinner);
        String value = spinner.getSelectedItem().toString();
        String[] spinnerItems = _parent.getResources().getStringArray(R.array.spinnerItems);
        int interval = -1;
        for(int i = 0; i < spinnerItems.length; ++i) {
            if(Objects.equals(value, spinnerItems[i])) {
                interval = i;
                break;
            }
        }

        EditText editText = _parent.findViewById(R.id.description);
        String description = editText.getText().toString();
        if(description.equals("")){
            Toast.makeText(_parent.getApplicationContext(),R.string.new_task_description_hint,Toast.LENGTH_SHORT).show();
        }else{
            OnHandleTask(description, interval, amount, System.currentTimeMillis());
            NavUtils.navigateUpFromSameTask(_parent);
        }
    }

    protected abstract void OnHandleTask(String description, int interval, int amount, long currentTimeMillis);
}
class CreateTaskViewModel extends TaskViewModel {
    CreateTaskViewModel(NewTaskActivity parent) {
        super(parent);
    }

    @Override
    protected void OnHandleTask(String description, int interval, int amount, long currentTimeMillis) {
        _parent.createNewTask(description, interval, amount, currentTimeMillis);
    }
}
class UpdateTaskViewModel extends TaskViewModel {
    UpdateTaskViewModel(NewTaskActivity parent) {
        super(parent);
    }

    @Override
    protected void OnHandleTask(String description, int interval, int amount, long currentTimeMillis) {
        _parent.updateTask(description, interval, amount, currentTimeMillis);
    }
}

public class NewTaskActivity extends AppCompatActivity{

    private static final String TAG = "NewTaskActivity";

    private long _id;
    private Task _task;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_new_task);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Intent intent = getIntent();
        String action = intent.getExtras().getString("action");
        switch (action) {
            case "create": {
                setTitle(R.string.title_activity_new_task);
                NumberPicker np = findViewById(R.id.numberPicker);
                np.setMaxValue(10);
                np.setMinValue(1);
                np.setValue(3);
                final Button createTaskButton = findViewById(R.id.button_create_task);
                final Button updateTaskButton = findViewById(R.id.button_update_task);
                createTaskButton.setVisibility(View.VISIBLE);
                updateTaskButton.setVisibility(View.INVISIBLE);

                break;
            }
            case "edit": {
                setTitle(R.string.edit_task_activity_title);
                long id = intent.getExtras().getLong("id");
                _id = id;
                InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
                try (SQLiteDatabase database = openHelper.getReadableDatabase()) {
                    _task = Util.findTaskById(this, id);
                }
                EditText description = findViewById(R.id.description);
                description.setText(_task.getDescription());
                final Spinner spinner = findViewById(R.id.spinner);
                spinner.setSelection(_task.getInterval());

                final NumberPicker numberPicker = findViewById(R.id.numberPicker);
                //TODO:add sounds for views using playSoundEffect
                //numberPicker.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                numberPicker.setMaxValue(10);
                numberPicker.setMinValue(1);
                numberPicker.setValue(_task.getAmount());
                final Button createTaskButton = findViewById(R.id.button_create_task);
                final Button updateTaskButton = findViewById(R.id.button_update_task);
                createTaskButton.setVisibility(View.INVISIBLE);
                updateTaskButton.setVisibility(View.VISIBLE);
                break;
            }
            default:
                Log.e(TAG, "onCreate: unknown action: " + action);
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_new_task, menu);
        return true;
    }

    public void OnButtonCreateTaskClicked(View view) {
        Log.d(TAG, "OnButtonCreateTaskClicked");
        new CreateTaskViewModel(this).OnClick();
    }

    void createNewTask(String description, int interval, int amount, long currentTimeMillis) {
        Log.d(TAG, "createNewTask");

        final long nextAlarm = Util.getNextAlarm(interval, amount, currentTimeMillis, getResources().getConfiguration().locale);
        final long cautionPeriod = (long) ((nextAlarm - currentTimeMillis) * 0.95);
        final long nextCaution  = currentTimeMillis + cautionPeriod;

        ContentValues contentValues = getContentValuesForNewTask(
                description,
                interval,
                amount,
                nextAlarm,
                nextCaution);

        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        try {
            try (SQLiteDatabase db = openHelper.getWritableDatabase()){
                db.insert(Util.TASK_TABLE, null, contentValues);
            }
        } finally {
            openHelper.close();
        }
    }

    @NonNull
    private ContentValues getContentValuesForNewTask(String description, int interval, int amount, long nextAlarm, long nextCaution) {
        Log.d(TAG, "getContentValuesForNewTask(long)");
        ContentValues contentValues = new ContentValues();
        contentValues.put("description", description);
        contentValues.put("interval", interval);
        contentValues.put("amount", amount);
        contentValues.put("next_alarm", nextAlarm);
        contentValues.put("next_caution", nextCaution);
        return contentValues;
    }

    @NonNull
    private ContentValues getContentValuesForNewTask(String description, int interval, int amount) {
        Log.d(TAG, "getContentValuesForNewTask(short)");
        ContentValues contentValues = new ContentValues();
        contentValues.put("description", description);
        contentValues.put("interval", interval);
        contentValues.put("amount", amount);
        return contentValues;
    }

    public void OnButtonUpdateTaskClicked(View view) {
        Log.d(TAG, "OnButtonUpdateTaskClicked");
        new UpdateTaskViewModel(this).OnClick();
    }

    void updateTask(String description, int interval, int amount, long currentTimeMillis) {
        Log.d(TAG, "updateTask");

        final long nextAlarm = Util.getNextAlarm(interval, amount, currentTimeMillis, getResources().getConfiguration().locale);
        final long cautionPeriod = (long) ((nextAlarm - currentTimeMillis) * 0.95);
        final long nextCaution  = currentTimeMillis + cautionPeriod;

        ContentValues contentValues;
        contentValues = WasIntervalOrAmountChanged(interval, amount)
                ? getContentValuesForNewTask(description, interval, amount, nextAlarm, nextCaution)
                : getContentValuesForNewTask(description, interval, amount);

        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        try {
            try (SQLiteDatabase db = openHelper.getWritableDatabase()){
                db.update(Util.TASK_TABLE, contentValues, "id=" + _id, null);
            }
        } finally {
            openHelper.close();
        }

    }

    private boolean WasIntervalOrAmountChanged(int interval, int amount) {
        return _task.getInterval() != interval || _task.getAmount() != amount;
    }
}