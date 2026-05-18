package com.vpe_soft.intime.intime.ui;

/**
 * Tracks whether any v2 screen is in the foreground. Used to avoid showing
 * duplicate notifications while the user is already in the app.
 */
public final class UiVisibility {

    private static int activeV2Activities;

    private UiVisibility() {
    }

    public static synchronized void onV2ActivityStarted() {
        activeV2Activities++;
    }

    public static synchronized void onV2ActivityStopped() {
        if (activeV2Activities > 0) {
            activeV2Activities--;
        }
    }

    public static boolean isV2UiVisible() {
        return activeV2Activities > 0;
    }
}
