package com.vpe_soft.intime.intime.activity;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.DatabaseUtil;
import com.vpe_soft.intime.intime.database.InTimeOpenHelper;
import com.vpe_soft.intime.intime.database.Task;
import com.vpe_soft.intime.intime.database.TaskState;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;
import com.vpe_soft.intime.intime.recyclerview.TaskRecyclerViewAdapter;
import com.vpe_soft.intime.intime.view.CardViewStateHelper;
import com.vpe_soft.intime.intime.view.ManageDialogView;
import com.vpe_soft.intime.intime.view.SnackbarHelper;
import com.vpe_soft.intime.intime.view.ViewUtil;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static boolean isOnScreen;

    private RecyclerView recyclerView;

    private MyBroadcastReceiver receiver;

    private Colors colors;

    private CardViewStateHelper cardViewStateHelper = new CardViewStateHelper();
    public boolean isDefaultViewOutlineProviderSet = false;

    private TaskRecyclerViewAdapter adapter;
    private InTimeOpenHelper openHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        colors = new Colors(this);
        super.onCreate(savedInstanceState);

        openHelper = new InTimeOpenHelper(this);

        setContentView(R.layout.activity_main);
        TextView title = findViewById(R.id.title_text);
        title.setTypeface(ViewUtil.getTypeface(this), Typeface.NORMAL);
        View settings = findViewById(R.id.open_settings);
        View addTask = findViewById(R.id.add_task);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });
        addTask.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, NewTaskActivity.class);
                intent.putExtra("action", "create");
                startActivity(intent);
            }
        });
        Cursor cursor = DatabaseUtil.createCursor(openHelper);

        //TODO: create empty view after deleting old empty view

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setBackgroundColor(colors.cardSwipeBackground);
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
            public void onSelectedChanged (RecyclerView.ViewHolder viewHolder, int actionState) {
                if(actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    cardViewStateHelper.setOnSwipeState((TaskRecyclerViewAdapter.TaskRecyclerViewVH) viewHolder);
                }
                super.onSelectedChanged(viewHolder, actionState);
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                cardViewStateHelper.setDefaultState((TaskRecyclerViewAdapter.TaskRecyclerViewVH) viewHolder);
                super.clearView(recyclerView, viewHolder);
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
                    int imgMargin = (int) ViewUtil.toPx(24);
                    Drawable img = ContextCompat.getDrawable(getContext(), R.drawable.acknowledge);
                    int imgSize = (int) ViewUtil.toPx(24);
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
                    ColorDrawable background = new ColorDrawable(colors.cardSwipeBackground);
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
                acknowledgeTask(DatabaseUtil.getId(pos, openHelper), pos);
            }
        };
        new ItemTouchHelper(simpleItemTouchCallback).attachToRecyclerView(recyclerView);
    }

    @Override
    protected void onStart() {
        IntentFilter filter = new IntentFilter(AlarmUtil.TASK_OVERDUE_ACTION);
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
        adapter.swapCursor(DatabaseUtil.createCursor(openHelper));
        refreshRecyclerView();
        createAlarm();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        openHelper.close();
        super.onDestroy();
    }

    private void editTask(long id) {
        Log.d(TAG, "editTask");
        Intent intent = new Intent(this, NewTaskActivity.class);
        intent.putExtra("action", "edit");
        intent.putExtra("id", id);
        startActivity(intent);
    }

    /**
     * Acknowledges the task with given ID and position with ability to roll-back unwanted ack
     * @param id - ID of the task
     * @param pos - position of the task in the list
     */
    private void acknowledgeTask(final long id, final int pos) {
        Log.d(TAG,"id " + id);
        Log.d(TAG,"pos " + pos);
        final long currentTimeMillis = System.currentTimeMillis();
        final TaskState previousTaskState = DatabaseUtil.acknowledgeTask(id, currentTimeMillis, this, openHelper);
        if (previousTaskState == null) {
            return;
        }

        createAlarm();
        final Context context = getContext();
        adapter.swapCursor(DatabaseUtil.createCursor(openHelper));
        adapter.notifyItemChanged(pos);
        SnackbarHelper.showOnAcknowledged(context, recyclerView, new SnackbarHelper.Listener() {
            @Override
            public void onCancelled() {
                DatabaseUtil.rollBackState(id, previousTaskState, openHelper);
                createAlarm();
                adapter.swapCursor(DatabaseUtil.createCursor(openHelper));
            }
        });
    }

    private void createAlarm() {
        Log.d(TAG, "createAlarm");
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(AlarmUtil.NOTIFICATION_TAG, 1);
        AlarmUtil.setupAlarmIfRequired(this, openHelper);
    }

    private void refreshRecyclerView() {
        Log.d(TAG, "refreshListView");
        adapter.notifyItemRangeChanged(0, DatabaseUtil.getDatabaseLengthFromContext(openHelper));
    }

    private Task deleteTask(long id) {
        Log.d(TAG, "deleteTask");
        Task task = null;
        try {
            task = DatabaseUtil.findTaskById(id, openHelper);
            DatabaseUtil.deleteTask(id, openHelper);
        } catch (Exception ex) {
            Log.e(TAG, "deleteTask: cannot delete task", ex);
        }
        return task;
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

    public void setDefaultViewOutlineProvider(ViewOutlineProvider viewOutlineProvider) {
        cardViewStateHelper.setDefaultProvider(viewOutlineProvider);
    }

    public void onItemLongClicked(final long id, final int pos) {
        ManageDialogView dialog = new ManageDialogView(this, new ManageDialogView.Actions() {
            private final static String TAG = "ManageViewDialog";
            @Override
            public void acknowledge() {
                // re-route the call to activity's method
                acknowledgeTask(id, pos);
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
                final Task task = deleteTask(id);
                createAlarm();
                adapter.swapCursor(DatabaseUtil.createCursor(openHelper));
                adapter.notifyItemRemoved(pos);
                adapter.notifyItemRangeChanged(pos, DatabaseUtil.getDatabaseLengthFromContext(openHelper));
                SnackbarHelper.showOnDeleted(getContext(), recyclerView, new SnackbarHelper.Listener() {
                    @Override
                    public void onCancelled() {
                        DatabaseUtil.createNewTask(id, task, openHelper);
                        createAlarm();
                        adapter.swapCursor(DatabaseUtil.createCursor(openHelper));
                        adapter.notifyItemInserted(pos);
                    }
                });
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