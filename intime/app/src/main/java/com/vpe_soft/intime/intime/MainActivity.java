package com.vpe_soft.intime.intime;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.support.v7.widget.Toolbar;
import java.util.*;

public class MainActivity extends AppCompatActivity implements TaskFragment.OnFragmentInteractionListener {

    private static final String TAG = "MainActivity";

    private MyBroadcastReceiver _receiver;
    private Timer timer = new Timer();
    public static boolean _isOnScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ListView listView = (ListView) findViewById(android.R.id.list);
        registerForContextMenu(listView);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(Util.TASK_OVERDUE_ACTION);
        registerReceiver(getReceiver(), filter);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(getReceiver());
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        _isOnScreen = false;
        SharedPreferences sharedPreferences = getSharedPreferences("SessionInfo", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("LastUsageTimestamp", System.currentTimeMillis());
        editor.apply();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        _isOnScreen = true;
        refreshListView();
        createAlarm();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId() == android.R.id.list) {
            String[] menuItems = new String[]{
                    getString(R.string.context_menu_acknowledge),
                    getString(R.string.context_menu_edit),
                    getString(R.string.context_menu_delete)
            };
            for (int i = 0; i < menuItems.length; i++) {
                menu.add(Menu.NONE, i, i, menuItems[i]);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int menuItemIndex = item.getItemId();
        switch (menuItemIndex) {
            case 0:     // acknowledge
                acknowledgeTask(info.id);
                refreshListView();
                break;
            case 1:     // edit
                editTask(info.id);
                refreshListView();
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

    private void editTask(long id) {
        Log.d(TAG, "editTask");
        Intent intent = new Intent(this, NewTaskActivity.class);
        intent.putExtra("action", "edit");
        intent.putExtra("id", id);
        startActivity(intent);
    }

    private void acknowledgeTask(long id) {
        Log.d(TAG, "acknowledgeTask");
        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        final long currentTimeMillis = System.currentTimeMillis();
        try (SQLiteDatabase database = openHelper.getWritableDatabase()){
            TaskInfo taskInfo = Util.findTaskById(database, id);
            if (taskInfo == null) {
                Log.w("VP", "cannot find task with id=" + id);
                return;
            }

            final long nextAlarmMoment = Util.getNextAlarm(
                    taskInfo.getInterval(),
                    taskInfo.getAmount(),
                    currentTimeMillis,
                    getResources().getConfiguration().locale);

            final long cautionPeriod = (long) ((nextAlarmMoment - currentTimeMillis) * 0.95);
            // todo: uncomment when yellowing algorithm will work OK
//            createTimer(cautionPeriod);
            final long nextCautionMoment = currentTimeMillis + cautionPeriod;
            ContentValues values = new ContentValues();
            values.put("next_alarm", nextAlarmMoment);
            values.put("next_caution", nextCautionMoment);
            String whereClause = "id=" + id;
            final int result = database.update(Util.TASK_TABLE, values, whereClause, null);
            if (result != 1) {
                Log.w(TAG, "acknowledgeTask: Cannot update task with id=" + id);
                throw new RuntimeException("cannot update task with id=" + id);
            }
        }
        createAlarm();
    }

    private void createAlarm(){
        Log.d(TAG, "createAlarm");
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(Util.NOTIFICATION_TAG, 1);
        Util.setupAlarmIfRequired(this);
    }

    private void createTimer(final long timeInterval){
        Log.d(TAG, "createTimer");
        TimerTask timertask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        notifyTaskOverdue();
                    }
                });
            }
        };
		timer.schedule(timertask, timeInterval);
	}

    private void refreshListView() {
        Log.d(TAG, "refreshListView");
        TaskFragment fragment = (TaskFragment) getFragmentManager().findFragmentById(R.id.fragment);
        fragment.refreshListView();
    }

    private void deleteTask(long id) {
        Log.d(TAG, "deleteTask");
        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        try {
			SQLiteDatabase database = openHelper.getWritableDatabase();
            final String identifier = "" + id;
            int result = database.delete(Util.TASK_TABLE, "id=?", new String[]{identifier});
            if (result != 1) {
                throw new RuntimeException("wrong removing of the task");
            }
        } catch (Exception ex) {
            Log.e(TAG, "deleteTask: cannot delete task", ex);
        }

        createAlarm();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_newtask) {
            Log.d(TAG, "onOptionsItemSelected: 'new task' pressed");
            Intent intent = new Intent(this, NewTaskActivity.class);
            intent.putExtra("action", "create");
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFragmentInteraction(String id) {
    }

    public MyBroadcastReceiver getReceiver() {
        if (_receiver == null) {
            _receiver = new MyBroadcastReceiver();
        }

        return _receiver;
    }

    public void notifyTaskOverdue() {
        Log.d(TAG, "notifyTaskOverdue");
        TaskFragment fragment = (TaskFragment) getFragmentManager().findFragmentById(R.id.fragment);
        fragment.refreshListView();
    }

    class MyBroadcastReceiver extends android.content.BroadcastReceiver {

        private static final String TAG = "MyBroadcastReceiver";

        public MyBroadcastReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive");
            if (_isOnScreen) {
                notifyTaskOverdue();
            }
        }
    }
}
