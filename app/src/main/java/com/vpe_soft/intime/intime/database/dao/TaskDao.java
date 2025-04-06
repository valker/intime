package com.vpe_soft.intime.intime.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import java.util.List;

@Dao
public interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TaskEntity task);

    @Query("UPDATE tasks SET last_ack = :ackTime, next_alarm = :nextAlarm, next_caution = :nextCaution, wasNotified = 0 WHERE id = :taskId")
    void acknowledgeTask(long taskId, long ackTime, long nextAlarm, long nextCaution);

    @Update
    void update(TaskEntity task);

    @Delete
    void delete(TaskEntity task);

    @Query("SELECT * FROM tasks ORDER BY next_alarm ASC")
    LiveData<List<TaskEntity>> getAllTasks();

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    LiveData<TaskEntity> getTaskById(long taskId);

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    TaskEntity getRawTaskById(long taskId);

    @Query("SELECT * FROM tasks WHERE next_alarm <= :now AND wasNotified = 0")
    List<TaskEntity> getTasksForNotification(long now);
}
