package com.vpe_soft.intime.intime.repository

import com.vpe_soft.intime.intime.dao.TaskDao
import com.vpe_soft.intime.intime.data.Task
import kotlinx.coroutines.flow.StateFlow

class TaskRepository(private val taskDao: TaskDao) {
    val tasks: StateFlow<List<Task>> = taskDao.getTasks()

    suspend fun addTask(task: Task){
        taskDao.addTask(task)
    }

    suspend fun updateTask(task: Task){
        taskDao.updateTask(task)
    }

    suspend fun deleteTask(task: Task){
        taskDao.deleteTask(task)
    }
}