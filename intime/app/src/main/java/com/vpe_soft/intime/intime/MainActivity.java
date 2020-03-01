package com.vpe_soft.intime.intime;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Modified by kylichist on 9.12.2019.
 */

public class MainActivity extends AppCompatActivity{

    private static final String TAG = "MainActivity";

    private MyBroadcastReceiver receiver;
    private final Timer onScreenUpdate = new Timer();
    public static boolean isOnScreen;

    private ListView listView;

    private ListAdapter listAdapter;
    private Cursor tasksCursor;
    private static final String SKELETON = "jjmm ddMMyyyy";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = findViewById(R.id.list);
        TextView emptyView = findViewById(R.id.empty);
        listView.setEmptyView(emptyView);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        /**
         * Init of database, cursor
         * Must be changed because of RecyclerView and AsyncTask(MainActivity)
         */
        /**
         * Need to be changed
         * Start
         */
        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        SQLiteDatabase database = openHelper.getReadableDatabase();
        tasksCursor = database.query(
                Util.TASK_TABLE,
                new String[]{
                        "description",
                        "id AS _id",
                        "next_alarm",
                        "next_caution"},
                null,
                null,
                null,
                null,
                "next_alarm");
        listAdapter = new CursorAdapter(this, tasksCursor) {
            @Override
            public View newView(Context context, Cursor cursor, ViewGroup parent) {
                return LayoutInflater.from(context).inflate(R.layout.task_item, parent, false);
            }

            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                // Extract properties from cursor
                String description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
                long next_alarm = cursor.getLong(cursor.getColumnIndexOrThrow("next_alarm"));
                long next_caution = cursor.getLong(cursor.getColumnIndexOrThrow("next_caution"));

                final long currentTimeMillis = System.currentTimeMillis();
                final String nextAlarm = getNextAlarmLocalString(next_alarm);
                final int type = getType(currentTimeMillis, next_alarm, next_caution);
                populateItemViewFields(view, description, nextAlarm,type);
            }

            private String getNextAlarmLocalString(long next_alarm) {
                // get current system properties (locale & timestamp)
                final Locale locale = getResources().getConfiguration().locale;
                final String pattern = DateFormat.getBestDateTimePattern(locale, SKELETON);
                SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
                format.setTimeZone(TimeZone.getDefault());
                Date date = new Date(next_alarm);
                return format.format(date);
            }

            private int getType(long currentTimeMillis, long next_alarm, long next_caution) {
                return currentTimeMillis > next_caution ? currentTimeMillis > next_alarm ? 2 : 1 : 0;
            }
        };
        listView.setAdapter(listAdapter);
    }

    private static void populateItemViewFields(View view, String description, String nextAlarm, int type) {
        Log.d(TAG, "populateItemViewFields");
        TextView tvBody = view.findViewById(R.id.tvBody);
        TextView tvPriority = view.findViewById(R.id.tvPriority);
        tvBody.setText(description);
        tvPriority.setText(nextAlarm);
        switch(type){
            case 0://white
                view.setBackgroundColor(Color.parseColor("#FFFFFF"));
                break;
            case 1://yellow
                view.setBackgroundColor(Color.parseColor("#FFEB3B"));
                break;
            case 2://red
                view.setBackgroundColor(Color.parseColor("#F44336"));
                break;
        }
    }
    /**
     * End
     */
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
        isOnScreen = false;
        SharedPreferences sharedPreferences = getSharedPreferences("SessionInfo", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("LastUsageTimestamp", System.currentTimeMillis());
        editor.apply();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        isOnScreen = true;
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
            case 0:
                acknowledgeTask(info.id);
                refreshListView();
                break;
            case 1:
                editTask(info.id);
                refreshListView();
                break;
            case 2:
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
        Log.d(TAG, "AcknowledgeTask id = " + id);
        InTimeOpenHelper openHelper = new InTimeOpenHelper(this);
        final long currentTimeMillis = System.currentTimeMillis();
        try (SQLiteDatabase database = openHelper.getWritableDatabase()){
            TaskInfo taskInfo = Util.findTaskById(database, id);
            if (taskInfo == null) {
                Log.w("VP", "Can't find task with id = " + id);
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
            values.put("last_ack", currentTimeMillis);
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
                        refreshListView();
                    }
                });
            }
        };
		onScreenUpdate.schedule(timertask, timeInterval);
	}

    private void refreshListView() {
        Log.d(TAG, "refreshListView");
        tasksCursor.requery();
        listView.refreshDrawableState();
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

/*    private void notifyAboutAppStandby(boolean ifRequired){
        if(ifRequired){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle()
        }

    }*/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        int id = item.getItemId();
        if (id == R.id.action_new_task) {
            Log.d(TAG, "onOptionsItemSelected: 'new task' pressed");
            Intent intent = new Intent(this, NewTaskActivity.class);
            intent.putExtra("action", "create");
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private MyBroadcastReceiver getReceiver() {
        if (receiver == null) {
            receiver = new MyBroadcastReceiver();
        }
        return receiver;
    }

    class MyBroadcastReceiver extends android.content.BroadcastReceiver {
        private static final String TAG = "MyBroadcastReceiver";
        public MyBroadcastReceiver(){}
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive");
            if (isOnScreen) {
                refreshListView();
            }
        }
    }
}
