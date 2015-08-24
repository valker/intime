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
        int amout = np.getValue();
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
        long timestamp = System.currentTimeMillis();

        createNewTask(description, interval, amout, timestamp);

        NavUtils.navigateUpFromSameTask(this);
    }

    private void createNewTask(String description, int interval, int amout, long timestamp) {
        Log.d("VP", "createNewTask");
        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        try {
            try (SQLiteDatabase db = openHelper.getWritableDatabase()){
                ContentValues contentValues = new ContentValues();
                contentValues.put("description", description);
                contentValues.put("interval", interval);
                contentValues.put("amount", amout);
                db.insert("tasks", null, contentValues);
            }
        }finally {
            openHelper.close();
        }
    }
}
