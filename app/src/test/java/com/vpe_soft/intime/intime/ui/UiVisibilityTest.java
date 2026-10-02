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

    /**
     * Проверяет: При отсутствии зарегистрированных запусков Activity запрашивается видимость
     * интерфейса. Ожидается false, то есть отсутствие видимости без активных Activity.
     */
    @Test
    public void isV2UiVisible_falseWhenNoActivityStarted() {
        assertFalse(UiVisibility.isV2UiVisible());
    }

    /**
     * Проверяет: Регистрируется запуск одной Activity через onV2ActivityStarted. Последующий
     * isV2UiVisible должен вернуть true. Проверяется счётчик видимости, без запуска настоящей Android
     * Activity.
     */
    @Test
    public void isV2UiVisible_trueWhileActivityStarted() {
        UiVisibility.onV2ActivityStarted();
        assertTrue(UiVisibility.isV2UiVisible());
    }

    /**
     * Проверяет: Регистрируются два запуска Activity и одна остановка: видимость должна остаться true.
     * После второй остановки ожидается false, поскольку остановлена последняя учтённая Activity.
     */
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
