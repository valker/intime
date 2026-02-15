package com.vpe_soft.intime.intime.activity;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.vpe_soft.intime.intime.BuildConfig;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.DatabaseUtil;
import com.vpe_soft.intime.intime.database.InTimeOpenHelper;
import com.vpe_soft.intime.intime.databinding.ActivitySettingsBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final int BACKUP_VERSION = 1;

    private InTimeOpenHelper openHelper;

    private final ActivityResultLauncher<String> createDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                    uri -> {
                        if (uri != null) {
                            exportToUri(uri);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openHelper = new InTimeOpenHelper(this);
        ActivitySettingsBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_settings);
        binding.setAppVersion(String.format(getString(R.string.version_format_string),
                                            BuildConfig.VERSION_NAME,
                                            BuildConfig.GIT_LAST_COMMIT_HASH));

        // taken from here https://stackoverflow.com/a/11753070
        View gotoGoogleBtn = findViewById(R.id.goto_google_play_btn);
        gotoGoogleBtn.setOnClickListener(view1 -> {
            final String appPackageName = getPackageName();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
            } catch (android.content.ActivityNotFoundException anfe) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
            }
        });

        View exportBtn = findViewById(R.id.export_btn);
        exportBtn.setOnClickListener(v -> {
            String suggestedName = "intime_backup_" + System.currentTimeMillis() + ".json";
            createDocumentLauncher.launch(suggestedName);
        });
    }

    @Override
    protected void onDestroy() {
        if (openHelper != null) {
            openHelper.close();
            openHelper = null;
        }
        super.onDestroy();
    }

    private void exportToUri(Uri uri) {
        try {
            JSONObject root = buildExportJson();
            byte[] bytes = root.toString(2).getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    out.write(bytes);
                }
            }
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "exportToUri failed", e);
            Toast.makeText(this, R.string.export_error, Toast.LENGTH_SHORT).show();
        }
    }

    private JSONObject buildExportJson() throws Exception {
        int dbVersion = openHelper.getReadableDatabase().getVersion();
        long createdAt = System.currentTimeMillis();

        JSONObject meta = new JSONObject();
        meta.put("app", getPackageName());
        meta.put("backup_version", BACKUP_VERSION);
        meta.put("db_version", dbVersion);
        meta.put("created_at", createdAt);

        JSONArray columns = new JSONArray();
        for (String col : DatabaseUtil.TASK_EXPORT_COLUMNS) {
            columns.put(col);
        }

        JSONArray rows = new JSONArray();
        try (Cursor cursor = DatabaseUtil.createExportCursor(openHelper)) {
            int idIdx = cursor.getColumnIndexOrThrow(DatabaseUtil.ID_FIELD);
            int descIdx = cursor.getColumnIndexOrThrow(DatabaseUtil.DESCRIPTION_FIELD);
            int intervalIdx = cursor.getColumnIndexOrThrow(DatabaseUtil.INTERVAL_FIELD);
            int amountIdx = cursor.getColumnIndexOrThrow(DatabaseUtil.AMOUNT_FIELD);
            int nextAlarmIdx = cursor.getColumnIndexOrThrow(DatabaseUtil.NEXT_ALARM_FIELD);
            int nextCautionIdx = cursor.getColumnIndexOrThrow(DatabaseUtil.NEXT_CAUTION_FIELD);
            int lastAckIdx = cursor.getColumnIndexOrThrow(DatabaseUtil.LAST_ACK_FIELD);
            int quantIdx = cursor.getColumnIndexOrThrow(DatabaseUtil.QUANT_FIELD);

            while (cursor.moveToNext()) {
                JSONArray row = new JSONArray();
                row.put(cursor.getLong(idIdx));
                row.put(cursor.getString(descIdx));
                row.put(cursor.getInt(intervalIdx));
                row.put(cursor.getInt(amountIdx));
                row.put(cursor.getLong(nextAlarmIdx));
                row.put(cursor.getLong(nextCautionIdx));
                row.put(cursor.getLong(lastAckIdx));
                row.put(cursor.getInt(quantIdx));
                rows.put(row);
            }
        }

        JSONObject tasksTable = new JSONObject();
        tasksTable.put("columns", columns);
        tasksTable.put("rows", rows);

        JSONObject tables = new JSONObject();
        tables.put("tasks", tasksTable);

        JSONObject root = new JSONObject();
        root.put("meta", meta);
        root.put("tables", tables);
        return root;
    }
}
