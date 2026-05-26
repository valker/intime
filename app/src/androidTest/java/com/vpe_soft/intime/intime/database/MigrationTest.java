package com.vpe_soft.intime.intime.database;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class MigrationTest {
    private static final String TEST_DB = "test_migration";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class
    );

    @Test
    public void migration_5_to_6_addsWasNotifiedColumn() throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 5);
        db.execSQL("INSERT INTO tasks (id, description, interval, amount, next_alarm, next_caution, last_ack, quant) VALUES (1, 'Test task', 2, 1, 1700000000000, 1699999000000, 1699500000000, 1)");
        db.close();

        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6);

        android.database.Cursor cursor = db.query("PRAGMA table_info(tasks)");
        boolean hasWasNotified = false;
        try {
            while (cursor.moveToNext()) {
                String columnName = cursor.getString(1);
                if ("wasNotified".equals(columnName)) {
                    hasWasNotified = true;
                    break;
                }
            }
        } finally {
            cursor.close();
        }

        assertTrue("Column wasNotified should exist after migration", hasWasNotified);
        db.close();
    }

    @Test
    public void migration_5_to_6_preservesExistingData() throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 5);
        db.execSQL("INSERT INTO tasks (id, description, interval, amount, next_alarm, next_caution, last_ack, quant) VALUES (1, 'Test task', 2, 1, 1700000000000, 1699999000000, 1699500000000, 1)");
        db.close();

        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6);

        android.database.Cursor cursor = db.query("SELECT COUNT(*) as count FROM tasks");
        int taskCount = 0;
        try {
            if (cursor.moveToFirst()) {
                taskCount = cursor.getInt(0);
            }
        } finally {
            cursor.close();
        }

        assertEquals("Existing task should be preserved", 1, taskCount);
        db.close();
    }

    @Test
    public void migration_5_to_6_initializeWasNotifiedToZero() throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 5);
        db.execSQL("INSERT INTO tasks (id, description, interval, amount, next_alarm, next_caution, last_ack, quant) VALUES (1, 'Test task', 2, 1, 1700000000000, 1699999000000, 1699500000000, 1)");
        db.close();

        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6);

        android.database.Cursor cursor = db.query("SELECT wasNotified FROM tasks WHERE id = 1");
        int wasNotified = -1;
        try {
            if (cursor.moveToFirst()) {
                wasNotified = cursor.getInt(0);
            }
        } finally {
            cursor.close();
        }

        assertEquals("wasNotified should be initialized to 0", 0, wasNotified);
        db.close();
    }

    @Test
    public void migration_5_to_6_preservesDataIntegrity() throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 5);
        // Insert multiple tasks with various data
        db.execSQL("INSERT INTO tasks (id, description, interval, amount, next_alarm, next_caution, last_ack, quant) VALUES (1, 'Daily task', 2, 1, 1700000000000, 1699999000000, 1699500000000, 1)");
        db.execSQL("INSERT INTO tasks (id, description, interval, amount, next_alarm, next_caution, last_ack, quant) VALUES (2, 'Weekly task', 3, 2, 1700500000000, 1700400000000, 1700000000000, 1)");
        db.execSQL("INSERT INTO tasks (id, description, interval, amount, next_alarm, next_caution, last_ack, quant) VALUES (3, 'Monthly task', 4, 1, 1701000000000, 1700900000000, 1700500000000, 2)");
        db.close();

        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6);

        // Verify all data is preserved
        android.database.Cursor cursor = db.query("SELECT id, description, interval, amount, next_alarm FROM tasks ORDER BY id");
        int rowCount = 0;
        try {
            while (cursor.moveToNext()) {
                rowCount++;
                int id = cursor.getInt(0);
                String description = cursor.getString(1);
                int interval = cursor.getInt(2);
                int amount = cursor.getInt(3);
                long nextAlarm = cursor.getLong(4);

                assertTrue("Data should be preserved", id > 0 && !description.isEmpty() && interval > 0);
            }
        } finally {
            cursor.close();
        }

        assertEquals("All three tasks should be preserved", 3, rowCount);
        db.close();
    }
}
