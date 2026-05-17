package com.vpe_soft.intime.intime.import_export;

import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import java.util.List;

/**
 * Replaces all tasks from validated backup JSON. Parsing runs before any delete.
 */
public final class ImportReplacement {

    private ImportReplacement() {
    }

    public static void replaceAll(AppDatabase database, String jsonContent) throws Exception {
        List<TaskEntity> tasks = BackupImport.parseTasks(jsonContent);
        TaskDao taskDao = database.taskDao();
        database.runInTransaction(() -> {
            taskDao.deleteAll();
            if (!tasks.isEmpty()) {
                taskDao.insertAll(tasks);
            }
        });
    }
}
