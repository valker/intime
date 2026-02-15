package com.vpe_soft.intime.intime.activity;

import android.content.Intent;
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
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
import com.vpe_soft.intime.intime.databinding.ActivitySettingsBinding;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    private TaskRepository taskRepository;
    private final ActivityResultLauncher<String[]> openDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onImportFilePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySettingsBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_settings);
        binding.setAppVersion(String.format(getString(R.string.version_format_string),
                                            BuildConfig.VERSION_NAME,
                                            BuildConfig.GIT_LAST_COMMIT_HASH));

        taskRepository = new TaskRepository(getApplication());

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

        View importBtn = findViewById(R.id.import_from_json_btn);
        importBtn.setOnClickListener(v -> openDocumentLauncher.launch(new String[]{"application/json", "text/plain", "*/*"}));
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
