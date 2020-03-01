package com.vpe_soft.intime.intime;

/**
 * Created by Valentin on 23.08.2015.
 */

public class TaskInfo {

    private final long id;
    private final String description;
    private final int interval;
    private final int amount;
    private final long nextAlarm;
    private final long nextCaution;
    private final long lastAck;

    TaskInfo(long id,  String description, int interval, int amount, long nextAlarm, long nextCaution, long lastAck) {
        this.id = id;
        this.description = description;
        this.interval = interval;
        this.amount = amount;
        this.nextAlarm = nextAlarm;
        this.nextCaution = nextCaution;
        this.lastAck = lastAck;
    }

    long getId() { return id; }

    int getInterval() {
        return interval;
    }

    int getAmount() {
        return amount;
    }

    String getDescription() {
        return description;
    }

    long getNextAlarm() {
        return nextAlarm;
    }

    long getNextCaution() {
        return nextCaution;
    }

    long getLastAcknowledge() {
        return lastAck;
    }
}