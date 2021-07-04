package com.vpe_soft.intime.intime.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "main.tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val description: String,
    val interval: Int,
    val amount: Int,
    @ColumnInfo(name = "next_alarm")
    val nextAlarm: Long,
    @ColumnInfo(name = "next_caution")
    val nextCaution: Long,
    @ColumnInfo(name = "last_ack")
    val lastAcknowledge: Long
)

fun Task.taskState(): TaskState = TaskState(nextAlarm, nextCaution, lastAcknowledge)

data class TaskState(
    val nextAlarm: Long,
    val nextCaution: Long,
    val lastAcknowledge: Long
)

//todo: create class TaskScheme