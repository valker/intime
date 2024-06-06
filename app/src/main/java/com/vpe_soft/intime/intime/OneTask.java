package com.vpe_soft.intime.intime;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import com.vpe_soft.intime.intime.database.DatabaseUtil;
import com.vpe_soft.intime.intime.database.InTimeOpenHelper;
import com.vpe_soft.intime.intime.database.TaskState;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;

public class OneTask {
    private static final String TAG = "OneTask";

    public static TaskState acknowledge(long taskId, Context context) {
        Log.d(TAG, "Acknowledge");
        try (InTimeOpenHelper openHelper = new InTimeOpenHelper(context)) {
            return acknowledge(taskId, context, openHelper);
        }
    }

    public static TaskState acknowledge(long taskId, Context context, InTimeOpenHelper openHelper) {
        Log.d(TAG, "Acknowledge..");
        final long currentTimeMillis = System.currentTimeMillis();
        TaskState prevTaskState = DatabaseUtil.acknowledgeTask(taskId, currentTimeMillis, context, openHelper);
        if(prevTaskState == null) {
            Log.w(TAG, "Prev state of task is undefined");
            return null;
        }

        createAlarm(context, openHelper);

        Log.d(TAG, "task acknowledged OK");

        return prevTaskState;
    }

    public static void createAlarm(Context context, InTimeOpenHelper openHelper) {
        Log.d(TAG, "createAlarm");
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(AlarmUtil.NOTIFICATION_TAG, 1);
        AlarmUtil.setupAlarmIfRequired(context, openHelper);
    }
}
