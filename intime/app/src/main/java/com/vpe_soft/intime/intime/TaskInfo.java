package com.vpe_soft.intime.intime;

/**
 * Created by Valentin on 23.08.2015.
 */
public class TaskInfo {
    private final long _id;
    private final String _description;
    private final int _interval;
    private final int _amount;
    private final long _nextAlarm;
    private final long _nextCaution;
    private final long _lastAck;

    TaskInfo(long id,  String description, int interval, int amount, long nextAlarm, long nextCaution, long lastAck) {
        _id = id;
        _description = description;
        _interval = interval;
        _amount = amount;
        _nextAlarm = nextAlarm;
        _nextCaution = nextCaution;
        _lastAck = lastAck;
    }

    long getId() { return _id; }

    int getInterval() {
        return _interval;
    }

    int getAmount() {
        return _amount;
    }

    String getDescription() {
        return _description;
    }

    long getNextAlarm() {
        return _nextAlarm;
    }

    public long getNextCaution() {
        return _nextCaution;
    }

    public long getLastAcknowledge() {
        return _lastAck;
    }
}
