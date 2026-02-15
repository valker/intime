package com.vpe_soft.intime.intime.import_export;

import androidx.annotation.NonNull;

import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses backup JSON and produces list of TaskEntity.
 * Expected structure: { "meta": { ... }, "tables": { "tasks": { "columns": [...], "rows": [[id, description, interval, amount, next_alarm, next_caution, last_ack, quant], ...] } } }
 */
public final class BackupImport {

    private static final String KEY_META = "meta";
    private static final String KEY_TABLES = "tables";
    private static final String KEY_TASKS = "tasks";
    private static final String KEY_ROWS = "rows";

    /** Column order in backup: id, description, interval, amount, next_alarm, next_caution, last_ack, quant */
    private static final int IDX_ID = 0;
    private static final int IDX_DESCRIPTION = 1;
    private static final int IDX_INTERVAL = 2;
    private static final int IDX_AMOUNT = 3;
    private static final int IDX_NEXT_ALARM = 4;
    private static final int IDX_NEXT_CAUTION = 5;
    private static final int IDX_LAST_ACK = 6;
    private static final int IDX_QUANT = 7;

    /**
     * @param jsonContent full JSON string
     * @return list of TaskEntity from tables.tasks.rows (ids preserved from backup)
     * @throws Exception on parse/format errors
     */
    @NonNull
    public static List<TaskEntity> parseTasks(@NonNull String jsonContent) throws Exception {
        JSONObject root = new JSONObject(jsonContent);
        JSONObject tables = root.optJSONObject(KEY_TABLES);
        if (tables == null) {
            throw new IllegalArgumentException("Missing 'tables' in backup JSON");
        }
        JSONObject tasksTable = tables.optJSONObject(KEY_TASKS);
        if (tasksTable == null) {
            throw new IllegalArgumentException("Missing 'tables.tasks' in backup JSON");
        }
        JSONArray rows = tasksTable.optJSONArray(KEY_ROWS);
        if (rows == null) {
            throw new IllegalArgumentException("Missing 'tables.tasks.rows' in backup JSON");
        }

        List<TaskEntity> result = new ArrayList<>(rows.length());
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = rows.getJSONArray(i);
            if (row.length() < 8) {
                throw new IllegalArgumentException("Row " + i + " has fewer than 8 columns");
            }
            long id = row.getLong(IDX_ID);
            String description = row.getString(IDX_DESCRIPTION);
            int interval = row.getInt(IDX_INTERVAL);
            int amount = row.getInt(IDX_AMOUNT);
            long nextAlarm = row.getLong(IDX_NEXT_ALARM);
            long nextCaution = row.getLong(IDX_NEXT_CAUTION);
            long lastAck = row.getLong(IDX_LAST_ACK);
            int quant = row.getInt(IDX_QUANT);

            TaskEntity entity = new TaskEntity(description, interval, amount, nextAlarm, nextCaution, lastAck, quant);
            entity.setId(id);
            result.add(entity);
        }
        return result;
    }
}
