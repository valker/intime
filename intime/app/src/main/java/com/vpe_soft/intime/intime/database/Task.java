package com.vpe_soft.intime.intime.database;

/**
 * Created by Valentin on 23.08.2015.
 */

public class Task {

    private final long id;
    private final String description;
    private final int interval;
    private final int amount;
    private final long nextAlarm;
    private final long nextCaution;
    private final long lastAck;

    public Task(long id, String description, int interval, int amount, long nextAlarm, long nextCaution, long lastAck) {
        this.id = id;
        this.description = description;
        this.interval = interval;
        this.amount = amount;
        this.nextAlarm = nextAlarm;
        this.nextCaution = nextCaution;
        this.lastAck = lastAck;
    }

    public long getId() { return id; }

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