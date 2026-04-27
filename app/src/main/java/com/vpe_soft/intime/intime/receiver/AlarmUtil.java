package com.vpe_soft.intime.intime.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.Pair;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.DatabaseUtil;
import com.vpe_soft.intime.intime.database.InTimeOpenHelper;
import com.vpe_soft.intime.intime.domain.ReminderCalculator;

import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class AlarmUtil {

    private static final String TAG = "AlarmUtil";

    private static final String SKELETON = "jjmm ddMMyyyy";

    public static final String NOTIFICATION_TAG = "com.vpe_soft.intime.intime.NotificationTag";

    public static Pair<Long, Long> getNextAlarmAndCaution(int interval, int amount, long lastAck, int quant, Locale locale) {
        ReminderCalculator.ReminderTimes next = ReminderCalculator.getNextAlarmAndCaution(
                interval,
                amount,
                lastAck,
                quant,
                locale
        );
        return new Pair<>(next.nextAlarm, next.nextCaution);
    }

    public static long getNextAlarm(int interval, int amount, long lastAck, int quant,
                                    Locale locale) {
        Log.d(TAG, "getNextAlarm");
        return ReminderCalculator.getNextAlarm(interval, amount, lastAck, quant, locale);
    }

    public static String getDateFromNextAlarm(Locale locale, long nextAlarm){
        Date date = new Date(nextAlarm);
        String pattern = DateFormat.getBestDateTimePattern(locale, SKELETON);
        SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(date);
    }

    public static void setupAlarmIfRequired(Context context, InTimeOpenHelper openHelper) {
        Log.d(TAG, "setupAlarmIfRequired");
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SQLiteDatabase database = DatabaseUtil.getReadableDatabaseFromContext(openHelper);
        final long currentTimestamp = System.currentTimeMillis();
        try (Cursor next_alarm = database.query(DatabaseUtil.TASK_TABLE, new String[]{DatabaseUtil.ID_FIELD, DatabaseUtil.NEXT_ALARM_FIELD, DatabaseUtil.DESCRIPTION_FIELD}, "next_alarm>?", new String[]{Long.toString(currentTimestamp)}, null, null, DatabaseUtil.NEXT_ALARM_FIELD, "1")) {
            if (next_alarm.moveToNext()) {
                Log.d(TAG, "setupAlarmIfRequired: task was found. going to setup alarm");
                long nextAlarm = next_alarm.getLong(next_alarm.getColumnIndexOrThrow(DatabaseUtil.NEXT_ALARM_FIELD));
                final PendingIntent pendingIntent = createPendingIntent(
                        context,
                        next_alarm.getString(next_alarm.getColumnIndexOrThrow(DatabaseUtil.DESCRIPTION_FIELD)),
                        next_alarm.getLong(next_alarm.getColumnIndexOrThrow(DatabaseUtil.ID_FIELD))
                        );
                scheduleAlarm(alarmManager, nextAlarm, pendingIntent);
            } else {
                Log.d(TAG, "setupAlarmIfRequired: no task with alarm in future found");
                cancelScheduledAlarm(context, alarmManager);
            }
        }
    }

    private static void scheduleAlarm(AlarmManager alarmManager, long nextAlarm, PendingIntent pendingIntent) {
        if (canScheduleExactAlarms(alarmManager)) {
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

    public static boolean canScheduleExactAlarms(AlarmManager alarmManager) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms();
    }

    private static void cancelScheduledAlarm(Context context, AlarmManager alarmManager) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                199709,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE
        );
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    public static String getNotificationString(Context context, String taskDescription, long overdueTasksCount) {
        String formatString = context.getString(R.string.notification_format);
        Locale locale = context.getResources().getConfiguration().locale;
        MessageFormat format = new MessageFormat(formatString, locale);
        ChoiceFormat cfn = getTaskChoiceFormat(locale.getISO3Language());
        format.setFormatByArgumentIndex(2, cfn);
        Object[] args = {taskDescription, overdueTasksCount - 1, overdueTasksCount - 1};
        return format.format(args);
    }

    public static ChoiceFormat getTaskChoiceFormat(String iso3Language) {
        double[] limits;
        String[] texts;
        if(iso3Language.equals("rus")) {
            limits = new double[]{1, 2, 5, 21, 22, 25};
            texts = new String[]{"задача", "задачи", "задач", "задача", "задачи", "задач"};
        }
        else {
            // other language - english by default
            limits = new double[]{1, 2};
            texts = new String[]{"task", "tasks"};
        }
        return new ChoiceFormat(limits, texts);
    }

    private static PendingIntent createPendingIntent(Context context,
                                                     String taskDescription,
                                                     long taskId) {
        Log.d(TAG, "createPendingIntent");
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(Constants.EXTRA_TASK_DESCRIPTION, taskDescription);
        intent.putExtra(Constants.EXTRA_TASK_ID, taskId);
        return PendingIntent.getBroadcast(context, 199709, intent,
                                          PendingIntent.FLAG_IMMUTABLE |
                                          PendingIntent.FLAG_CANCEL_CURRENT);
    }
}
