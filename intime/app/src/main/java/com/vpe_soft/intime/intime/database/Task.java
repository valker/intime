package com.vpe_soft.intime.intime.database;

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
    private final int quant;

    public Task(String description, int interval, int amount, long nextAlarm, long nextCaution, long lastAck, int quant) {
        this.description = description;
        this.interval = interval;
        this.amount = amount;
        this.nextAlarm = nextAlarm;
        this.nextCaution = nextCaution;
        this.lastAck = lastAck;
        this.quant = quant;
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

    public int getQuant() {
        return quant;
    }
}