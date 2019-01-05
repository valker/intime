package com.vpe_soft.intime.intime;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.support.v7.widget.Toolbar;
import java.util.*;

public class MainActivity extends AppCompatActivity implements TaskFragment.OnFragmentInteractionListener {
    private MyBroadcastReceiver _receiver;
    private PendingIntent _alarmIntent;
    private Toolbar toolbar;
	private Timer timer = new Timer();
	private TimerTask timertask;
	private long nextAlarm;
    public static boolean _isOnScreen;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("VP", "onCreate MainActivity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ListView listView = (ListView) findViewById(android.R.id.list);
        registerForContextMenu(listView);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        IntentFilter filter = new IntentFilter(Util.TASK_OVERDUE_ACTION);
        registerReceiver(getReceiver(), filter);
    }

    @Override
    protected void onPause() {
        Log.d("VP", "onPause MainActivity");
        super.onPause();
        _isOnScreen = false;
    }

    @Override
    protected void onResume() {
        Log.d("VP", "onResume MainActivity");
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
        Intent intent = new Intent(this, NewTaskActivity.class);
        intent.putExtra("action", "edit");
        intent.putExtra("id", id);
        startActivity(intent);
    }
    private void acknowledgeTask(long id) {
        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        final long currentTimeMillis = System.currentTimeMillis();
        try (SQLiteDatabase database = openHelper.getWritableDatabase()){
            TaskInfo taskInfo = Util.findTaskById(database, id);
            if (taskInfo == null) {
                Log.d("VP", "cannot find task with id=" + id);
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
                Log.d("VP", "Cannot update task with id=" + id);
                throw new RuntimeException("cannot update task with id=" + id);
            }
        }
        createAlarm();
    }
    private void createAlarm(){
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(Util.NOTIFICATION_TAG, 1);

        AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Log.d("VP", "mgr setup");
        if (_alarmIntent != null) {
            Log.d("VP", "true");
            mgr.cancel(_alarmIntent);
            _alarmIntent = null;
        }

        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        final SQLiteDatabase database = openHelper.getReadableDatabase();
        final long currentTimestamp = System.currentTimeMillis();
        final Cursor next_alarm = database.query(Util.TASK_TABLE, new String[]{"id", "next_alarm", "description"}, "next_alarm>" + currentTimestamp, null, null, null, "next_alarm", "1");
        if (next_alarm.moveToNext()) {
            Log.d("VP", "Moved to next");
            nextAlarm = next_alarm.getLong(next_alarm.getColumnIndexOrThrow("next_alarm"));
            final Context context = getApplicationContext();
            final Intent intent = new Intent(context, AlarmReceiver.class);
            intent.putExtra("task_description", next_alarm.getString(next_alarm.getColumnIndexOrThrow("description")));
            PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 199709, intent, 0);
            mgr.set(AlarmManager.RTC_WAKEUP, nextAlarm, alarmIntent);
            Log.d("VP", "MainActivity.onResume - create alarm");
            _alarmIntent = alarmIntent;
        }

        database.close();
        openHelper.close();
    }
	private void createTimer(final long timeInterval){
		Log.d("VP","create timer");
		timertask = new TimerTask() {
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
//		timer.schedule(timertask,seconds * 1000);
		timer.schedule(timertask, timeInterval);
	}
    private void refreshListView() {
        Log.d("VP", "MainActivity.refreshListView");
        TaskFragment fragment = (TaskFragment) getFragmentManager().findFragmentById(R.id.fragment);
        fragment.refreshListView();
    }

    private void deleteTask(long id) {
        Log.d("VP", "MainActivity.deleteTask");
        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        try {
			SQLiteDatabase database = openHelper.getWritableDatabase();
            final String identifier = "" + id;
            int result = database.delete(Util.TASK_TABLE, "id=?", new String[]{identifier});
            if (result != 1) {
                throw new RuntimeException("wrong removing of the task");
            }
        } catch (Exception ex) {
            Log.e("VP", "cannot delete", ex);
        }
        createAlarm();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d("VP", "MainActivity.onCreateOptionsMenu");
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

        if (id == R.id.action_newtask) {
            Log.d("VP", "new task pressed");
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
        Log.d("VP", "MainActivity.notifyTaskOverdue");
        TaskFragment fragment = (TaskFragment) getFragmentManager().findFragmentById(R.id.fragment);
        fragment.refreshListView();
    }

    class MyBroadcastReceiver extends android.content.BroadcastReceiver {
        public MyBroadcastReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("VP", "MyBroadcastReceiver.onReceive");
            if (_isOnScreen) {
                notifyTaskOverdue();
            }
        }
    }
}
