package com.vpe_soft.intime.intime.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
import com.vpe_soft.intime.intime.notifications.NotificationHelper;

import java.util.concurrent.Executors;

public class AckReceiver extends BroadcastReceiver {
    private static final String TAG = "AckReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        long taskId = intent.getLongExtra(Constants.EXTRA_TASK_ID, -1);
        if (taskId < 0) {
            Log.w(TAG, "Unexpected task ID");
            return;
        }

        final PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                NotificationHelper.dismissAllAppNotifications(appContext);
                new TaskRepository(appContext).acknowledgeTaskById(taskId);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
