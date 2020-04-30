package com.vpe_soft.intime.intime.activity;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.Task;
import com.vpe_soft.intime.intime.recyclerview.TaskRecyclerViewAdapter;
import com.vpe_soft.intime.intime.util.Util;
import com.vpe_soft.intime.intime.view.ManageDialogView;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static boolean isOnScreen;

    private MyBroadcastReceiver receiver;
    private final Timer onScreenUpdate = new Timer();

    private RecyclerView recyclerView;
    private TaskRecyclerViewAdapter adapter;

    private Cursor cursor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.parseColor("#F7F7F7"));
        TextView title = findViewById(R.id.title_text);
        title.setTypeface(Util.getTypeface(this), Typeface.NORMAL);
        ImageView addTask = findViewById(R.id.add_task);
        addTask.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, NewTaskActivity.class);
                intent.putExtra("action", "create");
                startActivity(intent);
            }
        });
        cursor = Util.createCursor(this);
        //TODO: create empty view after deleting old empty view
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setBackgroundColor(Color.parseColor("#F7F7F7"));
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new TaskRecyclerViewAdapter(this, cursor, getResources().getConfiguration().locale);
        recyclerView.setAdapter(adapter);
        final ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }
            @Override
            public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                return super.getSwipeDirs(recyclerView, viewHolder);
            }
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                acknowledgeTask(Util.getId(getContext(), pos));
                adapter.swapCursor(Util.createCursor(getContext()));
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

    private void editTask(long id) {
        Log.d(TAG, "editTask");
        Intent intent = new Intent(this, NewTaskActivity.class);
        intent.putExtra("action", "edit");
        intent.putExtra("id", id);
        startActivity(intent);
    }

    private void acknowledgeTask(long id) {
        final long currentTimeMillis = System.currentTimeMillis();
        SQLiteDatabase database = Util.getWritableDatabaseFromContext(this);
        Task task = Util.findTaskById(this, id);
        if (task == null) {
            Log.w("VP", "Can't find task with id = " + id);
            return;
        }
        Log.d("tag", "id " + id);
        Log.d("tag", "task_desc " + task.getDescription());
        Log.d("tag", "millis " + currentTimeMillis);
        final long nextAlarmMoment = Util.getNextAlarm(task.getInterval(), task.getAmount(), currentTimeMillis, getResources().getConfiguration().locale);
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

    private void createAlarm() {
        Log.d(TAG, "createAlarm");
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(Util.NOTIFICATION_TAG, 1);
        Util.setupAlarmIfRequired(this);
    }

    private void createTimer(final long timeInterval) {
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
        recyclerView.setAdapter(null);
        recyclerView.setLayoutManager(null);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter.notifyDataSetChanged();
    }

    private void deleteTask(long id) {
        Log.d(TAG, "deleteTask");
        try {
            SQLiteDatabase database = Util.getWritableDatabaseFromContext(this);
            int result = database.delete(Util.TASK_TABLE, "id=" + id, null);
            Log.d("tag", String.valueOf(result));
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

    private MyBroadcastReceiver getReceiver() {
        if (receiver == null) {
            receiver = new MyBroadcastReceiver();
        }
        return receiver;
    }

    public void onItemLongClicked(final long id, final int pos) {
        ManageDialogView dialog = new ManageDialogView(this, new ManageDialogView.Actions() {
            @Override
            public void acknowledge() {
                Log.d("tag","id " + id);
                Log.d("tag","pos " + pos);
                acknowledgeTask(id);
                adapter.swapCursor(Util.createCursor(getContext()));
                adapter.notifyItemChanged(pos);
            }

            @Override
            public void edit() {
                Log.d("tag","id " + id);
                Log.d("tag","pos " + pos);
                editTask(id);
            }

            @Override
            public void delete() {
                Log.d("tag","id " + id);
                Log.d("tag","pos " + pos);
                deleteTask(id);
                adapter.swapCursor(Util.createCursor(getContext()));
                adapter.notifyItemRemoved(pos);
                adapter.notifyItemRangeChanged(pos, Util.getDatabaseLengthFromContext(getContext()));
            }
        });
        dialog.show();
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