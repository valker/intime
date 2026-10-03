package com.vpe_soft.intime.intime.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;

@Database(entities = {TaskEntity.class}, version = 6)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TaskDao taskDao();

    private static volatile AppDatabase INSTANCE;

    /**
     * Replaces the singleton instance with a test database. Caller must close and reset after test.
     */
    public static void setTestInstance(AppDatabase testDb) {
        INSTANCE = testDb;
    }

    // v1.1.2 хранит семь полей в SQLite версии 4. Новый quant сохраняет прежний
    // полный интервал: делитель по умолчанию равен 1.
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN quant INTEGER NOT NULL DEFAULT 1");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN wasNotified INTEGER NOT NULL DEFAULT 0");
        }
    };

    // Один набор миграций для production singleton и проверок открытия исторических баз.
    static RoomDatabase.Builder<AppDatabase> createBuilder(Context context, String databaseName) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, databaseName)
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6);
    }

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = createBuilder(context, Constants.dbName).build();
                }
            }
        }
        return INSTANCE;
    }
}
