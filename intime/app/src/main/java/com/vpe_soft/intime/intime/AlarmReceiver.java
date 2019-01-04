package com.vpe_soft.intime.intime;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

/** 
 * Created by Valentin on 26.08.2015.
 * Receives notifications from AlarmManager about next alarm and pass it to MainActivity
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("VP", "AlarmReceiver.onReceive");
        String s = null;
        try {
            s = intent.getStringExtra("task_description");
        } catch( Exception e) {
            Log.d("VP", "exception1!!!");
            Log.d("VP", e.getLocalizedMessage());
        }

        s = s == null?"unknown" : s;

        Intent broadcastIntent = new Intent(Util.TASK_OVERDUE_ACTION);
        broadcastIntent.putExtra("task_description", s);
        context.sendOrderedBroadcast(broadcastIntent, null);

//        ShowNotification(context, "AlarmReceiver"+s);
        ShowNotification(context, s);
    }

    public static void ShowNotification(Context context, String s) {
        Log.d("VP", "Notification sending");

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentTitle(context.getResources().getString(R.string.app_name));
        builder.setContentText(s);
        builder.setSmallIcon(R.drawable.notification_icon);
        Intent mainActIntent = new Intent(context, MainActivity.class);
        PendingIntent mainActivityIntent = PendingIntent.getActivity(context, 0, mainActIntent, 0);
        builder.setContentIntent(mainActivityIntent);
        Notification notification = builder.build();
        notificationManager.notify(Util.NOTIFICATION_TAG, 1, notification);
    }
}
