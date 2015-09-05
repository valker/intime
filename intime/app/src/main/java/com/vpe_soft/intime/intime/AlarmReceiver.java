package com.vpe_soft.intime.intime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by Valentin on 26.08.2015.
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("VP", "AlarmReceiver.onReceive");

        Intent broadcastIntent = new Intent(Util.TASK_OVERDUE_ACTION);
        context.sendBroadcast(broadcastIntent);
    }
}
