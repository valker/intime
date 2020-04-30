package com.vpe_soft.intime.intime.activity;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Spinner;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.Task;
import com.vpe_soft.intime.intime.util.Util;

import java.util.Objects;

public class NewTaskActivity extends AppCompatActivity{

    private static final String TAG = "NewTaskActivity";

    private long _id;
    private Task _task;

    private NumberPicker numberPicker;
    private Spinner spinner;
    private EditText description;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_new_task);
        Intent intent = getIntent();
        String action = intent.getExtras().getString("action");
        TextView title = findViewById(R.id.newtask_title);
        numberPicker = findViewById(R.id.numberPicker);
        spinner = findViewById(R.id.spinner);
        description = findViewById(R.id.description);
        title.setTypeface(Util.getTypeface(this), Typeface.NORMAL);
        ImageView next = findViewById(R.id.newtask_action);
        switch (action) {
            case "create": {
                title.setText(R.string.title_activity_new_task);
                numberPicker.setMaxValue(10);
                numberPicker.setMinValue(1);
                numberPicker.setValue(1);
                next.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View view) {
                        if(description.getText().toString().equals("")) {
                            Toast.makeText(getContext(), R.string.new_task_description_hint, Toast.LENGTH_SHORT).show();
                        } else {
                            createNewTask(connectInfo());
                            finish();
                        }
                    }
                });
                break;
            }
            case "edit": {
                title.setText(R.string.edit_task_activity_title);
                long id = intent.getExtras().getLong("id");
                _id = id;
                _task = Util.findTaskById(this, id);
                description.setText(_task.getDescription());
                spinner.setSelection(_task.getInterval());
                numberPicker.setMaxValue(10);
                numberPicker.setMinValue(1);
                numberPicker.setValue(_task.getAmount());
                next.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View view) {
                        if(description.getText().toString().equals("")) {
                            Toast.makeText(getContext(), R.string.new_task_description_hint, Toast.LENGTH_SHORT).show();
                        } else {
                            updateTask(connectInfo());
                            finish();
                        }
                    }
                });
                break;
            }
            default:
                Log.e(TAG, "onCreate: unknown action: " + action);
                break;
        }
    }

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

    private ContentValues getContentValuesForNewTask(String description, int interval, int amount) {
        Log.d(TAG, "getContentValuesForNewTask(short)");
        ContentValues contentValues = new ContentValues();
        contentValues.put("description", description);
        contentValues.put("interval", interval);
        contentValues.put("amount", amount);
        return contentValues;
    }

    private Task connectInfo() {
        int amount = numberPicker.getValue();
        String value = spinner.getSelectedItem().toString();
        String[] spinnerItems = getResources().getStringArray(R.array.spinnerItems);
        int interval = -1;
        for(int i = 0; i < spinnerItems.length; ++i) {
            if(Objects.equals(value, spinnerItems[i])) {
                interval = i;
                break;
            }
        }
        String taskDescription = description.getText().toString();
        return new Task(this, taskDescription, interval, amount, 0);
    }

    private void createNewTask(Task task) {
        Log.d(TAG, "createNewTask");

        String description = task.getDescription();
        int interval = task.getInterval();
        int amount = task.getAmount();
        long nextAlarm = task.getNextAlarm();
        long nextCaution  = task.getNextCaution();

        ContentValues contentValues = getContentValuesForNewTask(
                description,
                interval,
                amount,
                nextAlarm,
                nextCaution);

        SQLiteDatabase db = Util.getWritableDatabaseFromContext(this);
        db.insert(Util.TASK_TABLE, null, contentValues);
        db.close();
    }

    private void updateTask(Task task) {
        Log.d(TAG, "updateTask");

        String description = task.getDescription();
        int interval = task.getInterval();
        int amount = task.getAmount();
        long nextAlarm = task.getNextAlarm();
        long nextCaution  = task.getNextCaution();

        ContentValues contentValues;
        contentValues = wasIntervalOrAmountChanged(interval, amount)
                ? getContentValuesForNewTask(description, interval, amount, nextAlarm, nextCaution)
                : getContentValuesForNewTask(description, interval, amount);

        SQLiteDatabase db = Util.getWritableDatabaseFromContext(this);
        db.update(Util.TASK_TABLE, contentValues, "id=" + _id, null);
        db.close();

    }

    private Context getContext() {
        return this;
    }

    private boolean wasIntervalOrAmountChanged(int interval, int amount) {
        return _task.getInterval() != interval || _task.getAmount() != amount;
    }
}