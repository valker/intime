package com.vpe_soft.intime.intime.import_export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ImportReplacementTest {

    private AppDatabase database;
    private TaskDao taskDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        taskDao = database.taskDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void replaceAll_invalidJson_keepsExistingTasks() {
        taskDao.insert(sampleTask("Keep me", 1000L));
        assertEquals(1, taskDao.getTaskCount());

        assertThrows(IllegalArgumentException.class,
                () -> ImportReplacement.replaceAll(database, "{}"));

        assertEquals(1, taskDao.getTaskCount());
    }

    @Test
    public void replaceAll_validJson_replacesTasks() throws Exception {
        taskDao.insert(sampleTask("Old task", 1000L));

        String json = "{"
                + "\"tables\":{"
                + "\"tasks\":{"
                + "\"rows\":[[7,\"Imported\",1,1,2000,1900,1500,1]]"
                + "}"
                + "}"
                + "}";

        ImportReplacement.replaceAll(database, json);

        assertEquals(1, taskDao.getTaskCount());
        TaskEntity imported = taskDao.getRawTaskById(7L);
        assertEquals("Imported", imported.description);
    }

    private static TaskEntity sampleTask(String description, long nextAlarm) {
        long now = System.currentTimeMillis();
        return new TaskEntity(description, 1, 1, nextAlarm, nextAlarm - 100, now, 1);
    }
}
