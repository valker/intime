package com.vpe_soft.intime.intime.activity;

import androidx.appcompat.app.AppCompatActivity;

import com.vpe_soft.intime.intime.ui.UiVisibility;

/**
 * Base class for v2 screens. Legacy v1 activities do not extend this type.
 */
public abstract class V2Activity extends AppCompatActivity {

    @Override
    protected void onStart() {
        super.onStart();
        UiVisibility.onV2ActivityStarted();
    }

    @Override
    protected void onStop() {
        UiVisibility.onV2ActivityStopped();
        super.onStop();
    }
}
