package com.vpe_soft.intime.intime.database.entities;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Objects;

@Entity(tableName = "tasks")
public class TaskEntity {

    public TaskEntity(@NonNull String description, long nextAlarm) {
        this.description = description;
        this.nextAlarm = nextAlarm;
        this.interval = 0;
        this.amount = 1;
        this.nextCaution = 0;
        this.lastAck = 0;
        this.quant = 1;
    }

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
