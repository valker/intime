package com.vpe_soft.intime.intime.activity;

import android.app.AlarmManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.vpe_soft.intime.intime.BuildConfig;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
import com.vpe_soft.intime.intime.databinding.ActivitySettingsBinding;

import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class SettingsActivity extends V2Activity {

    private static final String TAG = "SettingsActivity";
    private static final String EXPORT_FILE_NAME = "intime-tasks-backup.json";

    private TaskRepository taskRepository;
    private String pendingExportJson;

    private final ActivityResultLauncher<String[]> openDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onImportFilePicked);
    private final ActivityResultLauncher<String> createDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                    this::onExportFileCreated);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySettingsBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_settings);
        binding.setAppVersion(String.format(getString(R.string.version_format_string),
                BuildConfig.VERSION_NAME,
                BuildConfig.GIT_LAST_COMMIT_HASH));

        taskRepository = new TaskRepository(getApplication());

        View gotoGoogleBtn = findViewById(R.id.goto_google_play_btn);
        gotoGoogleBtn.setOnClickListener(view1 -> {
            final String appPackageName = getPackageName();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
            } catch (android.content.ActivityNotFoundException anfe) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
            }
        });

        findViewById(R.id.export_to_json_btn).setOnClickListener(v -> startExport());
        findViewById(R.id.import_from_json_btn).setOnClickListener(v ->
                openDocumentLauncher.launch(new String[]{"application/json", "text/plain", "*/*"}));

        updatePermissionUI();
        setupPermissionButtons();
    }

    private void updatePermissionUI() {
        updateNotificationPermissionStatus();
        updateExactAlarmStatus();
    }

    private void updateNotificationPermissionStatus() {
        TextView statusText = findViewById(R.id.notification_permission_status);
        boolean hasPermission = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (hasPermission) {
            statusText.setText(R.string.notification_permission_granted);
        } else {
            statusText.setText(R.string.notification_permission_denied);
        }
    }

    private void updateExactAlarmStatus() {
        TextView statusText = findViewById(R.id.exact_alarm_status);
        TextView titleText = findViewById(R.id.exact_alarm_title);
        TextView descriptionText = findViewById(R.id.exact_alarm_description);
        View buttonView = findViewById(R.id.exact_alarm_btn);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            titleText.setVisibility(View.GONE);
            statusText.setVisibility(View.GONE);
            descriptionText.setVisibility(View.GONE);
            buttonView.setVisibility(View.GONE);
            return;
        }

        titleText.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);
        descriptionText.setVisibility(View.VISIBLE);
        buttonView.setVisibility(View.VISIBLE);

        boolean canScheduleExact = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = getSystemService(AlarmManager.class);
            if (alarmManager != null) {
                canScheduleExact = alarmManager.canScheduleExactAlarms();
            }
        }

        if (canScheduleExact) {
            statusText.setText(R.string.exact_alarm_available);
        } else {
            statusText.setText(R.string.exact_alarm_unavailable);
        }
    }

    private void setupPermissionButtons() {
        findViewById(R.id.notification_permission_btn).setOnClickListener(v ->
                openNotificationSettings());
        findViewById(R.id.exact_alarm_btn).setOnClickListener(v ->
                openExactAlarmSettings());
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Could not open notification settings", e);
            Toast.makeText(this, "Could not open notification settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            } catch (Exception e) {
                Log.w(TAG, "Could not open exact alarm settings", e);
                Toast.makeText(this, "Could not open alarm settings", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionUI();
    }

    private void startExport() {
        taskRepository.exportAllTasksToJson(
                json -> {
                    pendingExportJson = json;
                    createDocumentLauncher.launch(EXPORT_FILE_NAME);
                },
                e -> {
                    Log.e(TAG, "Export failed", e);
                    Toast.makeText(this, getString(R.string.export_tasks_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }

    private void onExportFileCreated(Uri uri) {
        if (uri == null) {
            pendingExportJson = null;
            return;
        }
        if (pendingExportJson == null) {
            Toast.makeText(this, getString(R.string.export_tasks_error, "No data"), Toast.LENGTH_LONG).show();
            return;
        }
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                Toast.makeText(this, getString(R.string.export_tasks_error, "Could not open file"), Toast.LENGTH_LONG).show();
                return;
            }
            outputStream.write(pendingExportJson.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, R.string.export_tasks_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Write export file", e);
            Toast.makeText(this, getString(R.string.export_tasks_error, e.getMessage()), Toast.LENGTH_LONG).show();
        } finally {
            pendingExportJson = null;
        }
    }

    private void onImportFilePicked(Uri uri) {
        if (uri == null) return;
        String jsonContent;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) {
                Toast.makeText(this, getString(R.string.import_tasks_error, "Could not open file"), Toast.LENGTH_LONG).show();
                return;
            }
            jsonContent = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
        } catch (Exception e) {
            Log.e(TAG, "Read import file", e);
            Toast.makeText(this, getString(R.string.import_tasks_error, e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }
        taskRepository.replaceAllWithImportFromJson(
                this,
                jsonContent,
                () -> Toast.makeText(this, R.string.import_tasks_success, Toast.LENGTH_SHORT).show(),
                e -> {
                    Log.e(TAG, "Import failed", e);
                    Toast.makeText(this, getString(R.string.import_tasks_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }
}
