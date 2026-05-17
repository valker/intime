package com.vpe_soft.intime.intime.receiver;

import android.app.AlarmManager;
import android.content.Context;
import android.os.Build;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.Pair;

import com.vpe_soft.intime.intime.R;
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

    public static boolean canScheduleExactAlarms(AlarmManager alarmManager) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms();
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
}
