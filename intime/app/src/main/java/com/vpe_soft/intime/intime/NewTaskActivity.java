package com.vpe_soft.intime.intime;

import android.content.ContentValues;
import android.content.Intent;
import android.support.v7.widget.Toolbar;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Spinner;

import java.util.Objects;
import android.widget.*;
import android.view.*;
import android.graphics.*;

public class NewTaskActivity extends AppCompatActivity implements NewTaskFragment.OnFragmentInteractionListener {
    private Toolbar toolbar;
    private long _id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_task);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Intent intent = getIntent();
        String action = intent.getExtras().getString("action");
        if(action.equals("create")) {
            NumberPicker np = (NumberPicker) findViewById(R.id.numberPicker);
            np.setMaxValue(10);
            np.setMinValue(1);
            np.setValue(3);
            final Button createTaskButton = (Button) findViewById(R.id.button_create_task);
            final Button updateTaskButton = (Button) findViewById(R.id.button_update_task);
            createTaskButton.setVisibility(View.VISIBLE);
            updateTaskButton.setVisibility(View.INVISIBLE);
 
        } else if(action.equals("edit")) {
            long id = intent.getExtras().getLong("id");
            _id = id;
            InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
            SQLiteDatabase database = openHelper.getReadableDatabase();
            final TaskInfo taskInfo = Util.findTaskById(database, id);
            EditText description = (EditText) findViewById(R.id.description);
            description.setText(taskInfo.getDescription());
            final Spinner spinner = (Spinner) findViewById(R.id.spinner);
            spinner.setSelection(taskInfo.getInterval());

            final NumberPicker numberPicker = (NumberPicker) findViewById(R.id.numberPicker);
			//TODO:add sounds for views using playSoundEffect
			//numberPicker.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            numberPicker.setMaxValue(10);
            numberPicker.setMinValue(1);
            numberPicker.setValue(taskInfo.getAmount());
            final Button createTaskButton = (Button) findViewById(R.id.button_create_task);
            final Button updateTaskButton = (Button) findViewById(R.id.button_update_task);
            createTaskButton.setVisibility(View.INVISIBLE);
            updateTaskButton.setVisibility(View.VISIBLE);
        } else {
            Log.e("VP", "unknown action:" + action);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_new_task, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        //int id = item.getItemId();

        //noinspection SimplifiableIfStatement
       // if (id == R.id.action_settings) {
           // return true;
        //}

       return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFragmentInteraction(Uri uri) {
        //todo: implement
    }

    public void OnButtonCreateTaskClicked(View view) {
        Log.d("VP", "button create task clicked");
        NumberPicker np = (NumberPicker) findViewById(R.id.numberPicker);
        int amount = np.getValue();
        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        String value = spinner.getSelectedItem().toString();
        String[] spinnerItems = getResources().getStringArray(R.array.spinnerItems);
        int interval = -1;
        for(int i = 0; i < spinnerItems.length; ++i) {
            if(Objects.equals(value, spinnerItems[i])) {
                interval = i;
                break;
            }
        }

        EditText editText = (EditText) findViewById(R.id.description);
        String description = editText.getText().toString();
        long currentTimeMillis = System.currentTimeMillis();
		Log.d("VP",description);
		if(description.equals("")){
			Toast.makeText(getApplicationContext(),R.string.newtask_hint,Toast.LENGTH_SHORT).show();
		}else{
        createNewTask(description, interval, amount, currentTimeMillis);
        NavUtils.navigateUpFromSameTask(this);}
    }

    private void createNewTask(String description, int interval, int amount, long currentTimeMillis) {
        Log.d("VP", "createNewTask");

        final long nextAlarm = Util.getNextAlarm(interval, amount, currentTimeMillis, getResources().getConfiguration().locale);

        ContentValues contentValues = getContentValuesForNewTask(
                description,
                interval,
                amount,
                nextAlarm);

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
    private ContentValues getContentValuesForNewTask(String description, int interval, int amount, long nextAlarm) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("description", description);
        contentValues.put("interval", interval);
        contentValues.put("amount", amount);
        contentValues.put("next_alarm", nextAlarm);
        return contentValues;
    }

    @NonNull
    private ContentValues getContentValuesForNewTask(String description, int interval, int amount) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("description", description);
        contentValues.put("interval", interval);
        contentValues.put("amount", amount);
        return contentValues;
    }


    public void OnButtonUpdateTaskClicked(View view) {
        Log.d("VP", "button update task clicked");
        NumberPicker np = (NumberPicker) findViewById(R.id.numberPicker);
        int amount = np.getValue();
        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        String value = spinner.getSelectedItem().toString();
        String[] spinnerItems = getResources().getStringArray(R.array.spinnerItems);
        int interval = -1;
        for(int i = 0; i < spinnerItems.length; ++i) {
            if(Objects.equals(value, spinnerItems[i])) {
                interval = i;
                break;
            }
        }

        EditText editText = (EditText) findViewById(R.id.description);
        String description = editText.getText().toString();
        updateTask(_id, description, interval, amount);
        NavUtils.navigateUpFromSameTask(this);
    }

    private void updateTask(long id, String description, int interval, int amount) {
        Log.d("VP", "updateTask");

        ContentValues contentValues = getContentValuesForNewTask(
                description,
                interval,
                amount);

        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        try {
            try (SQLiteDatabase db = openHelper.getWritableDatabase()){
                db.update(Util.TASK_TABLE, contentValues, "id=" + id, null);
            }
        } finally {
            openHelper.close();
        }

    }
}
