package com.vpe_soft.intime.intime.database;

import android.content.Context;

import com.vpe_soft.intime.intime.util.Util;

/**
 * Created by Valentin on 23.08.2015.
 */

public class Task {

    private final String description;
    private final int interval;
    private final int amount;
    private final long nextAlarm;
    private final long nextCaution;
    private final long lastAck;

    public Task(String description, int interval, int amount, long nextAlarm, long nextCaution, long lastAck) {
        this.description = description;
        this.interval = interval;
        this.amount = amount;
        this.nextAlarm = nextAlarm;
        this.nextCaution = nextCaution;
        this.lastAck = lastAck;
    }

    public Task(Context context, String description, int interval, int amount, long lastAck) {
        this.description = description;
        this.interval = interval;
        this.amount = amount;
        this.lastAck = lastAck;
        long currentTimeMillis = System.currentTimeMillis();
        nextAlarm = Util.getNextAlarm(interval, amount, currentTimeMillis, context.getResources().getConfiguration().locale);
        long cautionPeriod = (long) ((nextAlarm - currentTimeMillis) * 0.95);
        nextCaution  = currentTimeMillis + cautionPeriod;
    }

    public int getInterval() {
        return interval;
    }

    public int getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public long getNextAlarm() {
        return nextAlarm;
    }

    public long getNextCaution() {
        return nextCaution;
    }

    public long getLastAcknowledge() {
        return lastAck;
    }
}