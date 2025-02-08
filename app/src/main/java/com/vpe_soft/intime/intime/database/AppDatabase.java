package com.vpe_soft.intime.intime.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;

@Database(entities = {TaskEntity.class}, version = 5)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TaskDao taskDao();
}
