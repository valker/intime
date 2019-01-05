package com.vpe_soft.intime.intime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by Valentin on 26.08.2015.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            // Set the alarm here.
            Log.d("VP", "onReceive: BOOT_COMPLETED");
            //1. get list of tasks that have next alarm between last-run and current time
            //2. if this list is not empty, generate notification
            //3. create alarm (if required for future task)
        }
    }
}
