package com.vpe_soft.intime.intime;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import androidx.appcompat.widget.Toolbar;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity{

    private static final String TAG = "MainActivity";

    private MyBroadcastReceiver receiver;
    private final Timer onScreenUpdate = new Timer();
    private TaskRecyclerViewAdapter adapter;
    public static boolean isOnScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //TODO: create empty view after deleting old empty view
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new TaskRecyclerViewAdapter(this, getResources().getConfiguration().locale);
        recyclerView.setAdapter(adapter);
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                return super.getSwipeDirs(recyclerView, viewHolder);
            }
            //TODO: delete and restore to change background color
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                acknowledgeTask(pos + 1);
                adapter.updateCard((TaskRecyclerViewAdapter.TaskRVViewHolder) viewHolder, Util.findTaskById(getContext(), pos + 1), pos, 0);
                adapter.notifyItemChanged(pos);
            }
        };
        new ItemTouchHelper(simpleItemTouchCallback).attachToRecyclerView(recyclerView);
    }

    @Override
    protected void onStart() {
        IntentFilter filter = new IntentFilter(Util.TASK_OVERDUE_ACTION);
        registerReceiver(getReceiver(), filter);
        super.onStart();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(getReceiver());
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        isOnScreen = false;
        SharedPreferences sharedPreferences = getSharedPreferences("SessionInfo", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("LastUsageTimestamp", System.currentTimeMillis());
        editor.apply();
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        isOnScreen = true;
        refreshRecyclerView();
        createAlarm();
        super.onResume();
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

    //TODO: rewrite onContextItemSelected because ContextMenu was deleted
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int menuItemIndex = item.getItemId();

        switch (menuItemIndex) {
            case 0:
                acknowledgeTask(info.id);
                refreshRecyclerView();
                break;
            case 1:
                editTask(info.id);
                refreshRecyclerView();
                break;
            case 2:
                deleteTask(info.id);
                refreshRecyclerView();
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

    //TODO: create timer for task status updating on screen
    private void acknowledgeTask(long id) {
        Log.d(TAG, "AcknowledgeTask id = " + id);
        final long currentTimeMillis = System.currentTimeMillis();
        SQLiteDatabase database = Util.getWritableDatabaseFromContext(this);
        Task task = Util.findTaskById(this, id);
        if (task == null) {
            Log.w("VP", "Can't find task with id = " + id);
            return;
        }
        final long nextAlarmMoment = task.getNextAlarm();
        final long cautionPeriod = (long) ((nextAlarmMoment - currentTimeMillis) * 0.95);
        //createTimer(cautionPeriod);
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
        database.close();
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
                        refreshRecyclerView();
                    }
                });
            }
        };
		onScreenUpdate.schedule(timertask, timeInterval);
	}

    private void refreshRecyclerView() {
        Log.d(TAG, "refreshListView");
        adapter.notifyDataSetChanged();
    }

    private void deleteTask(long id) {
        Log.d(TAG, "deleteTask");
        try {
			SQLiteDatabase database = Util.getWritableDatabaseFromContext(this);
            final String identifier = "" + id;
            int result = database.delete(Util.TASK_TABLE, "id=?", new String[]{identifier});
            if (result != 1) {
                throw new RuntimeException("wrong removing of the task");
            }
            database.close();
        } catch (Exception ex) {
            Log.e(TAG, "deleteTask: cannot delete task", ex);
        }

        createAlarm();
    }

    private Context getContext(){
        return this;
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
                refreshRecyclerView();
            }
        }
    }
}