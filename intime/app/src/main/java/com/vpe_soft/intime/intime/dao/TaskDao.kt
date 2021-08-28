package com.vpe_soft.intime.intime.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.vpe_soft.intime.intime.data.Task

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("SELECT * FROM `main.tasks` ORDER BY id ASC")
    fun getTasks(): LiveData<List<Task>>
}