package com.vpe_soft.intime.intime.import_export;

import androidx.annotation.NonNull;

import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.domain.ReminderCalculator;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;

/**
 * Parses backup JSON and produces list of TaskEntity.
 * Expected structure: { "meta": { ... }, "tables": { "tasks": { "columns": [...], "rows": [[id, description, interval, amount, next_alarm, next_caution, last_ack, quant], ...] } } }
 */
public final class BackupImport {
    static final int FORMAT_VERSION = 1;
    static final String[] COLUMNS = {"id", "description", "interval", "amount",
            "next_alarm", "next_caution", "last_ack", "quant"};

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
        JSONTokener parser = new JSONTokener(jsonContent);
        Object value = parser.nextValue();
        if (!(value instanceof JSONObject) || parser.nextClean() != 0) {
            throw new IllegalArgumentException("Backup must contain one JSON object");
        }
        JSONObject root = (JSONObject) value;
        // Старые v1-файлы без meta остаются совместимыми. Явная версия обязательна,
        // если meta присутствует: неизвестный формат нельзя трактовать как v1.
        if (root.has(KEY_META)) {
            JSONObject meta = root.optJSONObject(KEY_META);
            if (meta == null || !meta.has("version")) {
                throw new IllegalArgumentException("Unsupported backup metadata/version");
            }
            integer(meta.get("version"), "meta.version", FORMAT_VERSION, FORMAT_VERSION);
            if (meta.has("exportedAt")) integer(meta.get("exportedAt"), "meta.exportedAt", 0, Long.MAX_VALUE);
        }
        JSONObject tables = root.optJSONObject(KEY_TABLES);
        if (tables == null) {
            throw new IllegalArgumentException("Missing 'tables' in backup JSON");
        }
        JSONObject tasksTable = tables.optJSONObject(KEY_TASKS);
        if (tasksTable == null) {
            throw new IllegalArgumentException("Missing 'tables.tasks' in backup JSON");
        }
        if (tasksTable.has("columns")) {
            JSONArray columns = tasksTable.optJSONArray("columns");
            if (columns == null || columns.length() != COLUMNS.length) {
                throw new IllegalArgumentException("Expected exactly 8 columns");
            }
            for (int i = 0; i < COLUMNS.length; i++) {
                if (!COLUMNS[i].equals(columns.get(i))) {
                    throw new IllegalArgumentException("Unexpected column at index " + i);
                }
            }
        }
        JSONArray rows = tasksTable.optJSONArray(KEY_ROWS);
        if (rows == null) {
            throw new IllegalArgumentException("Missing 'tables.tasks.rows' in backup JSON");
        }

        List<TaskEntity> result = new ArrayList<>(rows.length());
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = rows.optJSONArray(i);
            if (row == null || row.length() != COLUMNS.length) {
                throw new IllegalArgumentException("Row " + i + " must have exactly 8 columns");
            }
            String path = "Row " + i + ": ";
            // 0 заставляет Room назначить новый ID; Long.MAX_VALUE исчерпывает AUTOINCREMENT.
            long id = integer(row.get(IDX_ID), path + "id", 1, Long.MAX_VALUE - 1);
            if (!ids.add(id)) throw new IllegalArgumentException(path + "duplicate id " + id);
            Object descriptionValue = row.get(IDX_DESCRIPTION);
            if (!(descriptionValue instanceof String)) throw new IllegalArgumentException(path + "description must be a string");
            String description = (String) descriptionValue;
            int interval = (int) integer(row.get(IDX_INTERVAL), path + "interval", 0, 5);
            int amount = (int) integer(row.get(IDX_AMOUNT), path + "amount", 1, Integer.MAX_VALUE);
            long nextAlarm = integer(row.get(IDX_NEXT_ALARM), path + "next_alarm", 0, ReminderCalculator.MAX_SUPPORTED_TIME);
            long nextCaution = integer(row.get(IDX_NEXT_CAUTION), path + "next_caution", 0, ReminderCalculator.MAX_SUPPORTED_TIME);
            long lastAck = integer(row.get(IDX_LAST_ACK), path + "last_ack", 0, ReminderCalculator.MAX_SUPPORTED_TIME);
            int quant = (int) integer(row.get(IDX_QUANT), path + "quant", 1, Integer.MAX_VALUE);
            long now = System.currentTimeMillis();
            try {
                // Проверка не меняет сохранённые сроки: исключаем невозможный следующий ACK.
                ReminderCalculator.getNextAlarm(interval, amount, now, quant, Locale.getDefault());
                if (lastAck > 0) ReminderCalculator.getNextAlarm(interval, amount, lastAck, quant, Locale.getDefault());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(path + "uncomputable schedule: " + exception.getMessage(), exception);
            }

            TaskEntity entity = new TaskEntity(description, interval, amount, nextAlarm, nextCaution, lastAck, quant);
            entity.setId(id);
            result.add(entity);
        }
        return result;
    }

    private static long integer(Object value, String field, long minimum, long maximum) {
        // getInt/getLong допускают строки, дроби и усечение; backup требует целые JSON-числа.
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw new IllegalArgumentException(field + " must be an integer JSON number");
        }
        long number = ((Number) value).longValue();
        if (number < minimum || number > maximum) {
            throw new IllegalArgumentException(field + " is out of range [" + minimum + ", " + maximum + "]");
        }
        return number;
    }
}
