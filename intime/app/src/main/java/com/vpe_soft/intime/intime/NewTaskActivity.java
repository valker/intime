package com.vpe_soft.intime.intime;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Spinner;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Objects;

public class NewTaskActivity extends AppCompatActivity implements NewTaskFragment.OnFragmentInteractionListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_task);
        NumberPicker np = (NumberPicker) findViewById(R.id.numberPicker);
        np.setMaxValue(10);
        np.setMinValue(1);
        np.setValue(3);
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
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

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

        createNewTask(description, interval, amount, currentTimeMillis);

        NavUtils.navigateUpFromSameTask(this);
    }

    private void createNewTask(String description, int interval, int amount, long currentTimeMillis) {
        Log.d("VP", "createNewTask");

        Date date = new Date(currentTimeMillis);
        Calendar calendar = new GregorianCalendar(getResources().getConfiguration().locale);
        calendar.setTime(date);
        switch (interval) {
            case 0:
                calendar.add(Calendar.MINUTE, amount);
                break;
            case 1:
                calendar.add(Calendar.HOUR, amount);
                break;
            case 2:
                calendar.add(Calendar.DAY_OF_YEAR, amount);
                break;
            case 3:
                calendar.add(Calendar.WEEK_OF_YEAR, amount);
                break;
            case 4:
                calendar.add(Calendar.MONTH, amount);
                break;
        }

        date = calendar.getTime();
        final long nextAlarm = date.getTime() / 1000L;
        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        try {
            try (SQLiteDatabase db = openHelper.getWritableDatabase()){
                ContentValues contentValues = new ContentValues();
                contentValues.put("description", description);
                contentValues.put("interval", interval);
                contentValues.put("amount", amount);
                contentValues.put("next_alarm", nextAlarm);
                db.insert("tasks", null, contentValues);
            }
        }finally {
            openHelper.close();
        }
    }
}
