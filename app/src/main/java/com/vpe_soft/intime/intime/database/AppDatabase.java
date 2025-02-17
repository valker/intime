package com.vpe_soft.intime.intime.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;

@Database(entities = {TaskEntity.class}, version = 5)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TaskDao taskDao();

    private static volatile AppDatabase INSTANCE; // Синглтон для базы данных

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class, Constants.dbName
                            )
//                            .fallbackToDestructiveMigration() // Удалять данные при смене версии
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
