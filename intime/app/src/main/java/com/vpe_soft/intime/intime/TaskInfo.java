package com.vpe_soft.intime.intime;

/**
 * Created by Valentin on 23.08.2015.
 */
public class TaskInfo {
    private final String _description;
    private final int _interval;
    private final int _amount;
    private final long _timestamp;

    public TaskInfo(String description, int interval, int amount, long timestamp) {
        _description = description;
        _interval = interval;
        _amount = amount;
        _timestamp = timestamp;
    }
}
