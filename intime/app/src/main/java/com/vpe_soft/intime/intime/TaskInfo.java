package com.vpe_soft.intime.intime;

/**
 * Created by Valentin on 23.08.2015.
 */
public class TaskInfo {
    private final String _description;
    private final int _interval;
    private final int _amount;
    private final long _nextAlarm;

    public TaskInfo(String description, int interval, int amount, long nextAlarm) {
        _description = description;
        _interval = interval;
        _amount = amount;
        _nextAlarm = nextAlarm;
    }

    public int getInterval() {
        return _interval;
    }

    public int getAmount() {
        return _amount;
    }
}
