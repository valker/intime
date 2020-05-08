package com.vpe_soft.intime.intime.activity;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.recyclerview.TaskRecyclerViewAdapter;
import com.vpe_soft.intime.intime.util.Util;
import com.vpe_soft.intime.intime.view.ManageDialogView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static boolean isOnScreen;

    private MyBroadcastReceiver receiver;

    private TaskRecyclerViewAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
        recyclerView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new TaskRecyclerViewAdapter(this, cursor, getResources().getConfiguration().locale);
        recyclerView.setAdapter(adapter);
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onChildDraw(Canvas canvas, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    int dx = (int) dX;
                    int cardLeft = viewHolder.itemView.getLeft();
                    int cardRight = viewHolder.itemView.getRight();
                    int cardTop = viewHolder.itemView.getTop();
                    int cardBottom = viewHolder.itemView.getBottom();
                    int newCardLeft = 0;
                    int newCardRight = 0;
                    int imgLeft = 0;
                    int imgRight = 0;
                    int imgMargin = (int) Util.toPx(getContext(), 24);
                    Drawable img = ContextCompat.getDrawable(getContext(), R.drawable.acknowledge);
                    int imgSize = (int) Util.toPx(getContext(), 24);
                    int imgTop = cardTop + ((cardBottom - cardTop) / 2) - (imgSize / 2);
                    if (dx > 0) {
                        //right
                        newCardRight = cardLeft + dx;
                        newCardLeft = cardLeft;
                        imgLeft = cardLeft + imgMargin;
                        imgRight = cardLeft + imgMargin + imgSize;
                    } else if (dx < 0) {
                        //left
                        newCardLeft = cardRight + dx;
                        newCardRight = cardRight;
                        imgLeft = cardRight - imgMargin - imgSize;
                        imgRight = cardRight - imgMargin;
                    }
                    ColorDrawable background = new ColorDrawable(Color.parseColor("#188038"));
                    background.setBounds(newCardLeft, cardTop, newCardRight, cardBottom);
                    background.draw(canvas);
                    img.setBounds(imgLeft, imgTop, imgRight, imgTop + imgSize);
                    img.draw(canvas);
                }
                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                Log.d(TAG, viewHolder.itemView.toString());
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

    private void refreshRecyclerView() {
        Log.d(TAG, "refreshListView");
        adapter.notifyItemRangeChanged(0, Util.getDatabaseLengthFromContext(this));
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
            private final static String TAG = "ManageViewDialog";
            @Override
            public void acknowledge() {
                Log.d(TAG,"id " + id);
                Log.d(TAG,"pos " + pos);
                acknowledgeTask(id);
                adapter.swapCursor(Util.createCursor(getContext()));
                adapter.notifyItemChanged(pos);
            }

            @Override
            public void edit() {
                Log.d(TAG,"id " + id);
                Log.d(TAG,"pos " + pos);
                editTask(id);
            }

            @Override
            public void delete() {
                Log.d(TAG,"id " + id);
                Log.d(TAG,"pos " + pos);
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