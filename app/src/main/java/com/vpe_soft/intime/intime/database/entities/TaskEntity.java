package com.vpe_soft.intime.intime.database.entities;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Entity(tableName = "tasks")
public class TaskEntity {

    public TaskEntity(@NonNull String description, int interval, int amount, long nextAlarm, long nextCaution, long lastAck
    ,int quant) {
        this.description = description;
        this.interval = interval;
        this.amount = amount;
        this.nextAlarm = nextAlarm;
        this.nextCaution = nextCaution;
        this.lastAck = lastAck;
        this.quant = quant;
    }

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String description;

    @NotNull
    public int interval;  // Тип интервала: минуты, часы и т. д.

    @NotNull
    public int amount;    // Количество интервалов

    @ColumnInfo(name="next_alarm", defaultValue = "0")
    @NotNull
    public long nextAlarm;

    @ColumnInfo(name="next_caution", defaultValue = "0")
    @NotNull
    public long nextCaution;

    @ColumnInfo(name="last_ack", defaultValue = "0")
    @NotNull
    public long lastAck;

    @ColumnInfo(defaultValue = "0")
    @NotNull
    public int quant;

    // Добавляем DiffUtil для ListAdapter
    public static final DiffUtil.ItemCallback<TaskEntity> DIFF_CALLBACK = new DiffUtil.ItemCallback<TaskEntity>() {
        @Override
        public boolean areItemsTheSame(@NonNull TaskEntity oldItem, @NonNull TaskEntity newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull TaskEntity oldItem, @NonNull TaskEntity newItem) {
            return oldItem.equals(newItem);
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskEntity task = (TaskEntity) o;
        return id == task.id &&
                interval == task.interval &&
                amount == task.amount &&
                nextAlarm == task.nextAlarm &&
                nextCaution == task.nextCaution &&
                lastAck == task.lastAck &&
                quant == task.quant &&
                description.equals(task.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, description, interval, amount, nextAlarm, nextCaution, lastAck, quant);
    }

    // Геттеры
    public int getId() {
        return id;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    public long getNextAlarm() {
        return nextAlarm;
    }
}
