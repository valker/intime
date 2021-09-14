package com.vpe_soft.intime.intime.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.vpe_soft.intime.intime.BuildConfig;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.databinding.ActivitySettingsBinding;
import com.vpe_soft.intime.intime.view.ViewUtil;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Typeface typeface = ViewUtil.getTypeface(this);
        TextView settingsTitle = findViewById(R.id.settings_title);
        TextView group1title = findViewById(R.id.group1title);
        TextView group1item1title = findViewById(R.id.group1item1title);
        TextView group1item1description = findViewById(R.id.group1item1description);
        TextView group1item2title = findViewById(R.id.group1item2title);
        TextView group1item2description = findViewById(R.id.group1item2description);

        ActivitySettingsBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_settings);
        binding.setAppVersion(String.format(getString(R.string.version_format_string), BuildConfig.VERSION_NAME, BuildConfig.GitHash));

        View actionExport = findViewById(R.id.action_export);
        View actionImport = findViewById(R.id.action_import);
        actionExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Export");
                //TODO: action - export
            }
        });
        actionImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Import");
                //TODO: action - import
            }
        });
        settingsTitle.setTypeface(typeface);
        group1title.setTypeface(typeface, Typeface.BOLD);
        group1item1title.setTypeface(typeface);
        group1item1description.setTypeface(typeface);
        group1item2title.setTypeface(typeface);
        group1item2description.setTypeface(typeface);
    }
}
