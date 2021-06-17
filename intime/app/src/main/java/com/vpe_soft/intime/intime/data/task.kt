package com.vpe_soft.intime.intime.data

data class Task(
    val description: String,
    val interval: Int,
    val amount: Int,
    val nextAlarm: Long,
    val nextCaution: Long,
    val lastAcknowledge: Long
)

data class TaskState(
    val nextAlarm: Long,
    val nextCaution: Long,
    val lastAcknowledge: Long
)

//todo: create class TaskScheme