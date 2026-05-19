package com.vpe_soft.intime.intime.import_export;

import androidx.annotation.NonNull;

import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Serializes tasks to backup JSON compatible with {@link BackupImport}.
 */
public final class BackupExport {

    private static final String KEY_META = "meta";
    private static final String KEY_TABLES = "tables";
    private static final String KEY_TASKS = "tasks";
    private static final String KEY_ROWS = "rows";
    private static final String KEY_VERSION = "version";
    private static final String KEY_EXPORTED_AT = "exportedAt";

    private BackupExport() {
    }

    @NonNull
    public static String toJson(@NonNull List<TaskEntity> tasks) throws Exception {
        JSONArray rows = new JSONArray();
        for (TaskEntity task : tasks) {
            JSONArray row = new JSONArray();
            row.put(task.id);
            row.put(task.description);
            row.put(task.interval);
            row.put(task.amount);
            row.put(task.nextAlarm);
            row.put(task.nextCaution);
            row.put(task.lastAck);
            row.put(task.quant);
            rows.put(row);
        }

        JSONObject tasksTable = new JSONObject();
        tasksTable.put(KEY_ROWS, rows);

        JSONObject tables = new JSONObject();
        tables.put(KEY_TASKS, tasksTable);

        JSONObject meta = new JSONObject();
        meta.put(KEY_VERSION, 1);
        meta.put(KEY_EXPORTED_AT, System.currentTimeMillis());

        JSONObject root = new JSONObject();
        root.put(KEY_META, meta);
        root.put(KEY_TABLES, tables);
        return root.toString(2);
    }
}
