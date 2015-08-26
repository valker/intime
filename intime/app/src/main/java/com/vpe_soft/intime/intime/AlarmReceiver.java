package com.vpe_soft.intime.intime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by Valentin on 26.08.2015.
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("VP", "AlarmReceiver.onReceive");
        Toast.makeText(context, "Alarm went off", Toast.LENGTH_SHORT).show();
    }
}
