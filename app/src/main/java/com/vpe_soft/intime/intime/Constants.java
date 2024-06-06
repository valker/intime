package com.vpe_soft.intime.intime;

public class Constants {
    /**
     * Inform that task has been overdue right now
     */
    public static final String TASK_OVERDUE_ACTION = "com.vpe_soft.intime.intime.TaskOverdue";

    /**
     * Acknowledge overdue task
     */
    public static final String ACTION_ACKNOWLEDGE = "com.vpe_soft.intime.intime.Acknowledge";

    /**
     * ID of notification channel for overdue tasks
     */
    public static final String TASK_OVERDUE_CHANNEL_ID = "DefaultChannelId";

    /**
     * Extra parameter of Intent to pass ID of task
     */
    public static final String EXTRA_TASK_ID = "task_id";

    /**
     * Extra parameter of Intent to pass Description of task
     */
    public static final String EXTRA_TASK_DESCRIPTION = "task_description";

    /**
     * Factor to calculate time interval for cautions
     */
    public static final double CAUTION_FACTOR = 0.95;

    /**
     * Key in SharedPreferences that points to last usage timestamp
     */
    public static final String LAST_USAGE_TIMESTAMP_KEY = "LastUsageTimestamp";

    /**
     * Name of SharedPreferences section
     */
    public static final String SESSION_INFO_SP_NAME = "SessionInfo";

    /**
     * 
     */
    public static final String ACTION_EXTRA_NAME = "action";

    /**
     *
     */
    public static final String ID_EXTRA_NAME = "id";

    /**
     *
     */
    public static final String EDIT_EXTRA_VALUE = "edit";

    /**
     *
     */
    public static final String CREATE_EXTRA_VALUE = "create";
}
