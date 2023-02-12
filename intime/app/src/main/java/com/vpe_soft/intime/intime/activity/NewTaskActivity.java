package com.vpe_soft.intime.intime.activity;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.graphics.drawable.DrawableCompat;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.NumberPicker;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.DatabaseUtil;
import com.vpe_soft.intime.intime.database.InTimeOpenHelper;
import com.vpe_soft.intime.intime.database.Task;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;
import com.vpe_soft.intime.intime.view.ViewUtil;

public class NewTaskActivity extends AppCompatActivity{

    private static final String TAG = "NewTaskActivity";

    private long _id;
    private Task _task;

    private Colors colors;

    private boolean editTextError = false;

    private NumberPicker numberPickerAmount;
    private NumberPicker numberPickerQuant;
    private AppCompatSpinner spinner;
    private EditText description;
    private InTimeOpenHelper openHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        openHelper = new InTimeOpenHelper(this);
        colors = new Colors(this);
        setContentView(R.layout.activity_new_task);
        TextView title = findViewById(R.id.newtask_title);
        TextView description_text = findViewById(R.id.textView4);
        TextView intervals_text = findViewById(R.id.textView5);
        TextView amount_text = findViewById(R.id.textView6);
        TextView quant_text = findViewById(R.id.textView7);
        View next = findViewById(R.id.newtask_action);
        description = findViewById(R.id.description);
        numberPickerAmount = findViewById(R.id.numberPickerAmount);
        numberPickerQuant = findViewById(R.id.numberPickerQuant);
        spinner = findViewById(R.id.spinner);
        title.setTypeface(ViewUtil.getTypeface(this), Typeface.NORMAL);
        description_text.setTypeface(ViewUtil.getTypeface(this), Typeface.NORMAL);
        intervals_text.setTypeface(ViewUtil.getTypeface(this), Typeface.NORMAL);
        amount_text.setTypeface(ViewUtil.getTypeface(this), Typeface.NORMAL);
        quant_text.setTypeface(ViewUtil.getTypeface(this), Typeface.NORMAL);
        description_text.setTypeface(ViewUtil.getTypeface(this), Typeface.NORMAL);
        description.setTypeface(ViewUtil.getTypeface(this), Typeface.NORMAL);
        spinner.setAdapter(new SpinnerAdapter(this, R.layout.spinner_item, getResources().getStringArray(R.array.spinnerItems)));
        description.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (editTextError) {
                    setEditTextState(0);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        final Bundle extras = getIntent().getExtras();
        final String action = extras != null
                ? extras.getString(Constants.ACTION_EXTRA_NAME)
                : Constants.CREATE_EXTRA_VALUE; // default action if nothing provided via extras
        switch (action) {
            case Constants.CREATE_EXTRA_VALUE: {
                title.setText(R.string.new_task_activity_title);
                numberPickerAmount.setMaxValue(10);
                numberPickerAmount.setMinValue(1);
                numberPickerAmount.setValue(1);
                numberPickerQuant.setMaxValue(10);
                numberPickerQuant.setMinValue(1);
                numberPickerQuant.setValue(1);
                next.setContentDescription(getString(R.string.content_description_add_task));
                next.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View view) {
                        if(description.getText().length() == 0) {
                            setEditTextState(1);
                        } else {
                            DatabaseUtil.createNewTask(connectInfo(System.currentTimeMillis()), openHelper);
                            finish();
                        }
                    }
                });
                break;
            }
            case Constants.EDIT_EXTRA_VALUE: {
                title.setText(R.string.edit_task_activity_title);
                long id = extras.getLong(Constants.ID_EXTRA_NAME);
                _id = id;
                _task = DatabaseUtil.findTaskById(id, openHelper);
                description.setText(_task.getDescription());
                spinner.setSelection(_task.getInterval());
                numberPickerAmount.setMaxValue(10);
                numberPickerAmount.setMinValue(1);
                numberPickerAmount.setValue(_task.getAmount());
                numberPickerQuant.setMaxValue(10);
                numberPickerQuant.setMinValue(1);
                final int quant = _task.getQuant();
                numberPickerQuant.setValue(quant);
                next.setContentDescription(getString(R.string.content_description_update_task));
                next.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View view) {
                        if(description.getText().length() == 0) {
                            setEditTextState(1);
                        } else {
                            updateTask(connectInfo(_task.getLastAcknowledge()));
                            finish();
                        }
                    }
                });
                break;
            }
            default:
                finish();
                break;
        }
    }

    @Override
    protected void onDestroy() {
        openHelper.close();
        super.onDestroy();
    }

    private void setEditTextState (int state) {
        if (state == 0) {
            //normal state 757575
            description.setHintTextColor(colors.editTextHint);
            DrawableCompat.setTint(description.getBackground(), colors.editTextTint);
        } else {
            //error state
            editTextError = true;
            description.setHintTextColor(colors.editTextErrorHint);
            DrawableCompat.setTint(description.getBackground(), colors.editTextErrorTint);
        }
    }

    private Task connectInfo(long lastAck) {
        int amount = numberPickerAmount.getValue();
        int interval = spinner.getSelectedItemPosition();
        String taskDescription = description.getText().toString();
        int quant = numberPickerQuant.getValue();
        long nextAlarm = AlarmUtil.getNextAlarm(interval, amount, lastAck, quant,
                getResources().getConfiguration().locale);
        long cautionPeriod = (long) ((nextAlarm - lastAck) * 0.95);
        long nextCaution  = lastAck + cautionPeriod;
        return new Task(taskDescription, interval, amount, nextAlarm, nextCaution, lastAck, quant);
    }

    private void updateTask(Task task) {
        Log.d(TAG, "updateTask");

        if(wasIntervalOrAmountChanged(task.getInterval(), task.getAmount(), task.getQuant())) {
            DatabaseUtil.updateTask(_id, task, openHelper);
        } else {
            DatabaseUtil.updateTaskDescription(_id, task, openHelper);
        }
    }

    private Context getContext() {
        return this;
    }

    private boolean wasIntervalOrAmountChanged(int interval, int amount, int quant) {
        return _task.getInterval() != interval || _task.getAmount() != amount || _task.getQuant() != quant;
    }

    private class SpinnerAdapter extends ArrayAdapter<String> {

        private SpinnerAdapter(Context context, int resource, String[] items) {
            super(context, resource, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView = (TextView) super.getView(position, convertView, parent);
            textView.setTypeface(ViewUtil.getTypeface(getContext()));
            return textView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView textView = (TextView) super.getDropDownView(position, convertView, parent);
            textView.setTypeface(ViewUtil.getTypeface(getContext()));
            return textView;
        }
    }
}