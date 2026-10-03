package com.vpe_soft.intime.intime.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;

import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.SdkSuppress;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.concurrent.AppExecutors;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.scheduling.SchedulingCoordinator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 33)
public class ScreenPlatformTest {
    private Context context;
    private AppDatabase database;
    private long taskId;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        AppDatabase.setTestInstance(database);
        long future = System.currentTimeMillis() + 7200000;
        taskId = database.taskDao().insert(new TaskEntity("Screen fixture", 1, 1, future, future, 0, 1));
    }

    @After
    public void tearDown() throws Exception {
        // Закрываем оставшиеся экраны, затем ждём ранее поставленные операции перед закрытием Room.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        CountDownLatch drained = new CountDownLatch(1);
        AppExecutors.executeTask("screen-test-drain", drained::countDown);
        assertTrue(drained.await(10, TimeUnit.SECONDS));
        database.taskDao().deleteAll();
        SchedulingCoordinator.reschedule(context);
        AppDatabase.setTestInstance(null);
        database.close();
    }

    /**
     * На тестовом устройстве создаётся in-memory Room с одной будущей задачей и выдаётся
     * разрешение уведомлений, чтобы системный диалог не перекрывал экраны. Последовательно
     * открываются список, добавление, настройки и детали этой задачи. После layout проверяются
     * полные экранные границы кнопок и описания: они должны находиться внутри области окна
     * за вычетом system bars/display cutout. Проверка использует реальные WindowInsets ОС,
     * поэтому выявляет перекрытие при обязательном edge-to-edge на Android 16/target 36.
     * Проверяются выбранные видимые элементы в портретной конфигурации AVD; клавиатура,
     * прокрутка, landscape, увеличенный шрифт и внешний вид остальных элементов не проверяются.
     */
    @Test
    public void screenControls_stayInsideSystemBarInsets() {
        checkScreen(MainActivityV2.class, R.id.btn_open_settings, R.id.fab_add_task);
        checkScreen(AddTaskActivity.class, R.id.btn_back_to_main, R.id.btn_save_task);
        checkScreen(SettingsActivity.class, R.id.btn_back_to_main, R.id.export_to_json_btn);
        checkScreen(TaskDetailsActivity.class, R.id.btn_back_to_main, R.id.task_description, R.id.btnAckTask);
    }

    /**
     * Список открыт с одной синтетической задачей. Нажатие его кнопки настроек должно открыть
     * SettingsActivity; отправка системного KEYCODE_BACK через Instrumentation должна вернуть
     * тот же экземпляр списка в RESUMED. Перед вводом ожидается также фокус окна:
     * RESUMED может наступить раньше его получения. Затем снова открываются настройки
     * и нажимается кнопка «на главный экран»: она тоже должна вернуть исходный список,
     * без второго экземпляра.
     * Проверяется настоящая смена Activity и интеграция системного Back с AppCompat, включая
     * Android 16/target 36. Анимация predictive back и свайп пальцем не воспроизводятся;
     * CRUD и сохранение задачи этим тестом не проверяются.
     */
    @Test
    public void settings_systemBackAndMainButton_returnToExistingList() {
        try (ActivityScenario<MainActivityV2> scenario = ActivityScenario.launch(MainActivityV2.class)) {
            Activity[] original = new Activity[1];
            scenario.onActivity(activity -> original[0] = activity);
            assertNotNull(original[0]);
            scenario.onActivity(activity -> activity.findViewById(R.id.btn_open_settings).performClick());
            awaitResumed(SettingsActivity.class, null);
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            awaitResumed(MainActivityV2.class, original[0]);
            scenario.onActivity(activity -> activity.findViewById(R.id.btn_open_settings).performClick());
            awaitResumed(SettingsActivity.class, null);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (activity instanceof SettingsActivity) activity.findViewById(R.id.btn_back_to_main).performClick();
                }
            });
            awaitResumed(MainActivityV2.class, original[0]);
        }
    }

    private void checkScreen(Class<? extends Activity> type, int... ids) {
        Intent intent = new Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (type == TaskDetailsActivity.class) intent.putExtra("task_id", taskId);
        try (ActivityScenario<?> scenario = ActivityScenario.launch(intent)) {
            long limit = SystemClock.uptimeMillis() + 5000;
            AtomicBoolean laidOut = new AtomicBoolean();
            do {
                scenario.onActivity(activity -> laidOut.set(activity.getWindow().getDecorView().isLaidOut()));
                if (laidOut.get()) break;
                SystemClock.sleep(50);
            } while (SystemClock.uptimeMillis() < limit);
            assertTrue("Window was not laid out: " + type.getSimpleName(), laidOut.get());
            scenario.onActivity(activity -> {
                WindowInsets windowInsets = activity.getWindow().getDecorView().getRootWindowInsets();
                assertNotNull(windowInsets);
                Insets insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Rect bounds = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
                Rect safe = new Rect(bounds.left + insets.left, bounds.top + insets.top,
                        bounds.right - insets.right, bounds.bottom - insets.bottom);
                for (int id : ids) {
                    View view = activity.findViewById(id);
                    assertNotNull(view);
                    assertTrue(view.isShown());
                    assertTrue(view.getWidth() > 0 && view.getHeight() > 0);
                    int[] location = new int[2];
                    view.getLocationOnScreen(location);
                    Rect actual = new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
                    assertTrue(type.getSimpleName() + ": " + activity.getResources().getResourceEntryName(id)
                            + "=" + actual + ", safe=" + safe, safe.contains(actual));
                }
            });
        }
    }

    private void awaitResumed(Class<? extends Activity> type, Activity expected) {
        long limit = SystemClock.uptimeMillis() + 10000;
        AtomicBoolean found = new AtomicBoolean();
        StringBuilder observed = new StringBuilder();
        do {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                found.set(false);
                observed.setLength(0);
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    observed.append(activity.getClass().getSimpleName()).append(" focused=")
                            .append(activity.hasWindowFocus()).append(" expectedInstance=")
                            .append(activity == expected).append(';');
                    if (type.isInstance(activity) && activity.hasWindowFocus()
                            && (expected == null || activity == expected)) found.set(true);
                }
            });
            if (found.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < limit);
        fail("Expected resumed focused activity: " + type.getSimpleName() + "; observed=" + observed);
    }
}
