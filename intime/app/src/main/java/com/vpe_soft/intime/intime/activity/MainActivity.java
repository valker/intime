package com.vpe_soft.intime.intime.activity;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;

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

    private TaskRecyclerViewAdapter adapter;

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
        Cursor cursor = Util.createCursor(this);
        //TODO: create empty view after deleting old empty view
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
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
        adapter.swapCursor(Util.createCursor(this));
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
        if (Util.acknowledgeTask(id, currentTimeMillis, this)) return;
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
        adapter.notifyDataSetChanged();
    }

    private void deleteTask(long id) {
        Log.d(TAG, "deleteTask");
        try {
            Util.deleteTask(id, this);
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