package com.vpe_soft.intime.intime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

public class MainActivity extends AppCompatActivity implements TaskFragment.OnFragmentInteractionListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ListView listView = (ListView) findViewById(android.R.id.list);
        registerForContextMenu(listView);

        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        final SQLiteDatabase database = openHelper.getReadableDatabase();
        final long currentTimestamp = System.currentTimeMillis() / 1000L;
        final Cursor next_alarm = database.query("main.tasks", new String[]{"id", "next_alarm"}, "next_alarm>" + currentTimestamp, null, null, null, "next_alarm", "1");
        if(next_alarm.moveToNext()) {
            final int id = next_alarm.getInt(next_alarm.getColumnIndexOrThrow("id"));
            final long nextAlarm = next_alarm.getLong(next_alarm.getColumnIndexOrThrow("next_alarm")) * 1000L;
            final Context context = getApplicationContext();
            AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            final Intent intent = new Intent(context, AlarmReceiver.class);
            PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
            mgr.set(AlarmManager.RTC_WAKEUP, nextAlarm, alarmIntent);
        }

        database.close();
        openHelper.close();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId()==android.R.id.list) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
            String[] menuItems = new String[]{
                    getString(R.string.context_menu_acknowledge),
                    getString(R.string.context_menu_edit),
                    getString(R.string.context_menu_delete)
            };
            for (int i = 0; i<menuItems.length; i++) {
                menu.add(Menu.NONE, i, i, menuItems[i]);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        int menuItemIndex = item.getItemId();
        switch(menuItemIndex) {
            case 0:     // acknowledge
                break;
            case 1:     // edit
                break;
            case 2:     // delete
                deleteTask(info.id);
                refreshListView();
                break;
            default:
                throw new RuntimeException("wrong menu item");
        }

        return true;
    }

    private void refreshListView() {
        TaskFragment fragment = (TaskFragment) getFragmentManager().findFragmentById(R.id.fragment);
        fragment.refreshListView();
    }

    private void deleteTask(long id) {
        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        try(SQLiteDatabase database = openHelper.getWritableDatabase()) {
            final String identifier = "" + id;
            int result = database.delete("tasks", "id=?", new String[]{identifier});
            if(result != 1) {
                throw new RuntimeException("wrong removing of the task");
            }
        } catch (Exception ex) {
            Log.e("VP", "cannot delete", ex);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
        else if(id == R.id.action_newtask) {
            Log.d("VP","new task pressed");
            Intent intent = new Intent(this, NewTaskActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFragmentInteraction(String id) {
    }

}
