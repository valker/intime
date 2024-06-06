package com.vpe_soft.intime.intime.database;

/**
 * Define task's state that could be used to roll-back the state after unwanted acknowledge
 */
public class TaskState {
    private final long nextAlarm;
    private final long nextCaution;
    private final long lastAck;

    public TaskState(Task task) {
        nextAlarm = task.getNextAlarm();
        nextCaution = task.getNextCaution();
        lastAck = task.getLastAcknowledge();
    }

    public long getNextAlarm() {
        return nextAlarm;
    }

    public long getNextCaution() {
        return nextCaution;
    }

    public long getLastAck() {
        return lastAck;
    }
}
