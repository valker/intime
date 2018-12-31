package com.vpe_soft.intime.intime;

import android.support.v4.app.*;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by Valentin on 05.09.2015.
 */
public class MyBroadcastReceiver extends android.content.BroadcastReceiver {
    MainActivity _parent;
    private boolean _isForeground;

    public MyBroadcastReceiver(MainActivity parent) {
        this._parent = parent;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("VP", "MyBroadcastReceiver.onReceive");
        if(_isForeground) {
            _parent.notifyTaskOverdue();
        } else {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
            builder.setTicker("Ticker");
            builder.setContentTitle("Title");
            builder.setContentText("Text");
            builder.setSmallIcon(R.mipmap.ic_launcher);
            Intent mainActIntent = new Intent(context, MainActivity.class);
            PendingIntent mainActivityIntent = PendingIntent.getActivity(context, 0, mainActIntent, 0);
            builder.setContentIntent(mainActivityIntent);
            Notification notification = builder.build();
            notificationManager.notify(Util.NOTIFICATION_TAG, 1, notification);
        }
    }

    public void setResume() {
        _isForeground = true;
    }

    public void setPause() {
        _isForeground = false;
    }
}
