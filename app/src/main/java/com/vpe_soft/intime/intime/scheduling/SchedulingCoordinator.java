package com.vpe_soft.intime.intime.scheduling;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.util.Log;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.receiver.AlarmReceiver;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Schedules the nearest future task reminder via AlarmManager using Room as source of truth.
 */
public final class SchedulingCoordinator {

    private static final String TAG = "SchedulingCoordinator";
    private static final int ALARM_REQUEST_CODE = 199709;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private SchedulingCoordinator() {
    }

    /**
     * Reschedules the platform alarm for the nearest task with {@code next_alarm} in the future.
     * Safe to call from any thread; Room access always runs off the main thread.
     */
    public static void reschedule(Context context) {
        Context appContext = context.getApplicationContext();
        if (Looper.myLooper() == Looper.getMainLooper()) {
            EXECUTOR.execute(() -> rescheduleInternal(appContext));
            return;
        }
        rescheduleInternal(appContext);
    }

    private static void rescheduleInternal(Context appContext) {
        TaskDao taskDao = AppDatabase.getInstance(appContext).taskDao();
        long now = System.currentTimeMillis();
        TaskEntity nearest = taskDao.getNearestFutureTask(now);

        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "reschedule: AlarmManager is not available");
            return;
        }

        if (nearest == null) {
            Log.d(TAG, "reschedule: no future task found, cancelling alarm");
            cancelScheduledAlarm(appContext, alarmManager);
            return;
        }

        Log.d(TAG, "reschedule: next alarm at " + nearest.nextAlarm + " for task " + nearest.id);
        PendingIntent pendingIntent = createPendingIntent(
                appContext,
                nearest.description,
                nearest.id
        );
        scheduleAlarm(alarmManager, nearest.nextAlarm, pendingIntent);
    }

    private static void scheduleAlarm(AlarmManager alarmManager, long nextAlarm, PendingIntent pendingIntent) {
        if (AlarmUtil.canScheduleExactAlarms(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarm,
                    pendingIntent);
        } else {
            Log.w(TAG, "scheduleAlarm: exact alarms are not available, using inexact alarm");
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarm,
                    pendingIntent);
        }
    }

    private static void cancelScheduledAlarm(Context context, AlarmManager alarmManager) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE
        );
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    private static PendingIntent createPendingIntent(Context context, String taskDescription, long taskId) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(Constants.EXTRA_TASK_DESCRIPTION, taskDescription);
        intent.putExtra(Constants.EXTRA_TASK_ID, taskId);
        return PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
    }
}
