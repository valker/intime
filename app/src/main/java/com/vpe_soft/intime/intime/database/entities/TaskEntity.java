package com.vpe_soft.intime.intime.database.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class TaskEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String description;

    public int interval;  // Тип интервала: минуты, часы и т. д.
    public int amount;    // Количество интервалов
    public long nextAlarm;
    public long nextCaution;
    public long lastAck;
    public int quant;
}
