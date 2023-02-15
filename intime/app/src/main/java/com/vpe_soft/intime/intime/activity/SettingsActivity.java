package com.vpe_soft.intime.intime.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.vpe_soft.intime.intime.BuildConfig;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.databinding.ActivitySettingsBinding;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySettingsBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_settings);
        binding.setAppVersion(String.format(getString(R.string.version_format_string),
                                            BuildConfig.VERSION_NAME,
                                            BuildConfig.GitHash));
    }
}
