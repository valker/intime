package com.vpe_soft.intime.intime.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.format.DateFormat;
import android.util.Log;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.DatabaseUtil;
import com.vpe_soft.intime.intime.database.InTimeOpenHelper;

import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class AlarmUtil {

    private static final String TAG = "AlarmUtil";

    private static final String SKELETON = "jjmm ddMMyyyy";

    public static final String NOTIFICATION_TAG = "com.vpe_soft.intime.intime.NotificationTag";

    private static final int[] fields= new int[]{
            Calendar.MINUTE,
            Calendar.HOUR,
            Calendar.DAY_OF_YEAR,
            Calendar.WEEK_OF_YEAR,
            Calendar.MONTH,
            Calendar.FIELD_COUNT //substitute for YEAR
    };

    public static long getNextAlarm(int interval, int amount, long lastAck, int quant,
                                    Locale locale) {
        Log.d(TAG, "getNextAlarm");
        Date date = new Date(lastAck);
        Calendar calendar = new GregorianCalendar(locale);
        calendar.setTime(date); // время последнего подтверждения
        int field = fields[interval];
        if(field == Calendar.FIELD_COUNT) {
            // YEAR is not supported by calendar.add, so emulate it as 12 months
            field = Calendar.MONTH;
            amount = amount * 12;
        }
        //noinspection ResourceType
        calendar.add(field, amount);
        date = calendar.getTime(); // время срабатывания без учёта квантов
        long time = date.getTime();
        long delta = (time - lastAck) / quant;
        return lastAck + delta;
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
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextAlarm,
                        pendingIntent);
            } else {
                Log.d(TAG, "setupAlarmIfRequired: no task with alarm in future found");
            }
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
