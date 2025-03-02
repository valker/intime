package com.vpe_soft.intime.intime.database.entities;

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

    private TaskEntity() {
    }

    static public TaskEntity CreateWithNextAlarm(long nextAlarm) {
        TaskEntity item = new TaskEntity();
        item.nextAlarm = nextAlarm;
        return item;
    }

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String description;

    @NotNull
    public Integer interval;  // Тип интервала: минуты, часы и т. д.

    @NotNull
    public Integer amount;    // Количество интервалов

    @ColumnInfo(name="next_alarm", defaultValue = "0")
    @NotNull
    public Long nextAlarm;

    @ColumnInfo(name="next_caution", defaultValue = "0")
    @NotNull
    public Long nextCaution;

    @ColumnInfo(name="last_ack", defaultValue = "0")
    @NotNull
    public Long lastAck;

    @ColumnInfo(defaultValue = "1")
    @NotNull
    public Integer quant;

    @NotNull
    private Boolean wasNotified = false;

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
                interval.equals(task.interval) &&
                amount.equals(task.amount) &&
                nextAlarm.equals(task.nextAlarm) &&
                nextCaution.equals(task.nextCaution) &&
                lastAck.equals(task.lastAck) &&
                quant.equals(task.quant) &&
                description.equals(task.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, description, interval, amount, nextAlarm, nextCaution, lastAck, quant);
    }

    // Геттеры
    public Long getId() {
        return id;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    @NonNull
    public Long getNextAlarm() {
        return nextAlarm;
    }

    public void setId(long newId) {
        id = newId;
    }

    // Геттеры и сеттеры
    public Boolean isWasNotified() { return wasNotified; }

    public void setWasNotified(Boolean wasNotified) { this.wasNotified = wasNotified; }
}
