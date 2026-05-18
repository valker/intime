package com.vpe_soft.intime.intime.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class UiVisibilityTest {

    @After
    public void tearDown() {
        while (UiVisibility.isV2UiVisible()) {
            UiVisibility.onV2ActivityStopped();
        }
    }

    @Test
    public void isV2UiVisible_falseWhenNoActivityStarted() {
        assertFalse(UiVisibility.isV2UiVisible());
    }

    @Test
    public void isV2UiVisible_trueWhileActivityStarted() {
        UiVisibility.onV2ActivityStarted();
        assertTrue(UiVisibility.isV2UiVisible());
    }

    @Test
    public void isV2UiVisible_tracksNestedActivities() {
        UiVisibility.onV2ActivityStarted();
        UiVisibility.onV2ActivityStarted();
        UiVisibility.onV2ActivityStopped();
        assertTrue(UiVisibility.isV2UiVisible());
        UiVisibility.onV2ActivityStopped();
        assertFalse(UiVisibility.isV2UiVisible());
    }
}
