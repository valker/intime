package com.vpe_soft.intime.intime.activity;

import android.content.Intent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.notifications.NotificationHelper;
import com.vpe_soft.intime.intime.ui.UiVisibility;

/**
 * Base class for v2 screens. Legacy v1 activities do not extend this type.
 */
public abstract class V2Activity extends AppCompatActivity {

    protected void setupMainScreenBackButton() {
        View backButton = findViewById(R.id.btn_back_to_main);
        if (backButton == null) {
            return;
        }
        backButton.setOnClickListener(v -> navigateToMainScreen());
    }

    protected void navigateToMainScreen() {
        Intent intent = new Intent(this, MainActivityV2.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        UiVisibility.onV2ActivityStarted();
        NotificationHelper.dismissAllAppNotifications(getApplicationContext());
    }

    @Override
    protected void onStop() {
        UiVisibility.onV2ActivityStopped();
        super.onStop();
    }
}
