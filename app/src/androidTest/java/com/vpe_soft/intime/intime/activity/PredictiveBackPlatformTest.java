package com.vpe_soft.intime.intime.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 33)
public class PredictiveBackPlatformTest {
    private Context context;
    private AppDatabase database;
    private long taskId;
    private long deadline;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        AppDatabase.setTestInstance(database);
        deadline = System.currentTimeMillis() + 7200000;
        taskId = database.taskDao().insert(new TaskEntity("Gesture fixture", 1, 1,
                deadline, deadline, 0, 1));
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) activity.finish();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        CountDownLatch drained = new CountDownLatch(1);
        AppExecutors.executeTask("gesture-test-drain", drained::countDown);
        assertTrue(drained.await(10, TimeUnit.SECONDS));
        database.taskDao().deleteAll();
        SchedulingCoordinator.reschedule(context);
        AppDatabase.setTestInstance(null);
        database.close();
    }

    /**
     * На выделенном проектном AVD API 33+ создаётся in-memory Room с одной будущей
     * задачей; уведомления разрешены, чтобы permission dialog не перекрыл жест.
     * Запоминаются исходные navigation overlay/mode и developer flag predictive
     * back. Временно включается gestural navigation, на Android 13/14 также
     * enable_back_animation=1; обе настройки восстанавливаются в finally.
     * До ввода касаний проверяется активный и подключённый EdgeBackGestureHandler;
     * AVD запускается с экранной навигацией через -EmulatorSoftwareNavigation.
     * Из списка открываются настройки. Реальные touchscreen MotionEvent от левого
     * края сначала идут вправо и возвращаются к краю перед отпусканием: отменённый
     * жест обязан оставить тот же SettingsActivity открытым. Затем полный свайп
     * вправо обязан вернуть тот же исходный MainActivityV2 в RESUMED с фокусом,
     * закрыть настройки и сохранить строку/ID/next_alarm/last_ack задачи.
     * KEYCODE_BACK и прямой вызов callback не используются. Это функциональная
     * проверка отмены/завершения жеста в портретном AVD с включённой системной
     * predictive-back конфигурацией; визуальные кадры/плавность preview-анимации,
     * правый край, back-to-home, другие экраны, release/R8 и OEM не проверяются.
     */
    @Test
    public void edgeGesture_cancelKeepsSettings_commitReturnsToSameList() throws Exception {
        String avd = shell("getprop ro.boot.qemu.avd_name").trim();
        if (avd.isEmpty()) avd = shell("getprop ro.kernel.qemu.avd_name").trim();
        assertEquals("Only dedicated project AVD may change navigation", "Intime_Test_API"
                + Build.VERSION.SDK_INT, avd);
        String oldMode = shell("settings get secure navigation_mode").trim();
        assertTrue("Unknown navigation mode: " + oldMode, oldMode.matches("[012]"));
        String oldFlag = shell("settings get global enable_back_animation").trim();
        assertTrue("Unexpected back animation flag", oldFlag.matches("null|[01]"));
        Matcher active = Pattern.compile("\\[x\\]\\s+(com\\.android\\.internal\\.systemui\\.navbar\\.[A-Za-z0-9_.]+)")
                .matcher(shell("cmd overlay list --user 0"));
        String oldOverlay = active.find() ? active.group(1) : null;
        assertFalse("Ambiguous active navigation overlays", active.find());
        try {
            shell("cmd overlay enable-exclusive --user 0 --category com.android.internal.systemui.navbar.gestural");
            awaitMode("2");
            awaitEdgeHandler();
            if (Build.VERSION.SDK_INT <= 34) {
                shell("settings put global enable_back_animation 1");
                assertEquals("1", shell("settings get global enable_back_animation").trim());
            }
            try (ActivityScenario<MainActivityV2> scenario = ActivityScenario.launch(MainActivityV2.class)) {
                AtomicReference<Activity> original = new AtomicReference<>();
                scenario.onActivity(original::set);
                awaitResumed(MainActivityV2.class, original.get());
                scenario.onActivity(activity -> activity.findViewById(R.id.btn_open_settings).performClick());
                Activity settings = awaitResumed(SettingsActivity.class, null);
                AtomicReference<Rect> bounds = new AtomicReference<>();
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                        bounds.set(settings.getWindowManager().getCurrentWindowMetrics().getBounds()));
                assertTrue("Portrait test window required", bounds.get().height() > bounds.get().width());
                swipe(bounds.get(), false);
                assertSame(settings, awaitResumed(SettingsActivity.class, settings));
                swipe(bounds.get(), true);
                assertSame(original.get(), awaitResumed(MainActivityV2.class, original.get()));
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    assertTrue("Settings not finished by committed gesture", settings.isFinishing() || settings.isDestroyed());
                });
                assertEquals(1, database.taskDao().getTaskCount());
                TaskEntity task = database.taskDao().getRawTaskById(taskId);
                assertNotNull(task);
                assertEquals("Gesture fixture", task.getDescription());
                assertEquals(Long.valueOf(deadline), task.getNextAlarm());
                assertEquals(Long.valueOf(0), task.lastAck);
            }
        } finally {
            try {
                if (oldOverlay == null) shell("cmd overlay disable --user 0 com.android.internal.systemui.navbar.gestural");
                else shell("cmd overlay enable-exclusive --user 0 --category " + oldOverlay);
                awaitMode(oldMode);
            } finally {
                // Ошибка восстановления overlay не должна оставлять developer flag изменённым.
                if (Build.VERSION.SDK_INT <= 34) {
                    shell(oldFlag.equals("null") ? "settings delete global enable_back_animation"
                            : "settings put global enable_back_animation " + oldFlag);
                    assertEquals(oldFlag, shell("settings get global enable_back_animation").trim());
                }
            }
        }
    }

    private void swipe(Rect bounds, boolean commit) {
        long down = SystemClock.uptimeMillis();
        float start = bounds.left + 1;
        float y = bounds.centerY();
        inject(down, MotionEvent.ACTION_DOWN, start, y);
        float far = bounds.left + bounds.width() * 0.65f;
        for (int i = 1; i <= 20; i++) {
            SystemClock.sleep(10);
            inject(down, MotionEvent.ACTION_MOVE, start + (far - start) * i / 20f, y);
        }
        if (!commit) {
            for (int i = 1; i <= 20; i++) {
                SystemClock.sleep(10);
                inject(down, MotionEvent.ACTION_MOVE, far + (start - far) * i / 20f, y);
            }
        }
        SystemClock.sleep(50);
        inject(down, MotionEvent.ACTION_UP, commit ? far : start, y);
        // RESUMED/focus могут сохраниться во время анимации отмены: ждём её завершения.
        SystemClock.sleep(1000);
    }

    private void inject(long down, int action, float x, float y) {
        MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
        pointer.id = 0;
        pointer.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.pressure = action == MotionEvent.ACTION_UP ? 0 : 1;
        coords.size = 1;
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
                new MotionEvent.PointerProperties[]{pointer}, new MotionEvent.PointerCoords[]{coords},
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try {
            assertTrue("Touch injection failed", InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().injectInputEvent(event, true));
        } finally { event.recycle(); }
    }

    private Activity awaitResumed(Class<? extends Activity> type, Activity expected) {
        long limit = SystemClock.uptimeMillis() + 15000;
        AtomicReference<Activity> found = new AtomicReference<>();
        do {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (type.isInstance(activity) && activity.hasWindowFocus()
                            && (expected == null || activity == expected)) found.set(activity);
                }
            });
            if (found.get() != null) return found.get();
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < limit);
        fail("Expected focused activity: " + type.getSimpleName());
        return null;
    }

    private void awaitMode(String expected) throws Exception {
        long limit = SystemClock.uptimeMillis() + 15000;
        do {
            if (shell("settings get secure navigation_mode").trim().equals(expected)) return;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < limit);
        fail("Navigation mode not restored/enabled: " + expected);
    }

    private void awaitEdgeHandler() throws Exception {
        // Одного navigation_mode=2 недостаточно: AVD с аппаратными кнопками
        // может не иметь системной панели и input monitor для бокового жеста.
        long limit = SystemClock.uptimeMillis() + 15000;
        Pattern sections = Pattern.compile("EdgeBackGestureHandler:([^\\r\\n]*(?:\\r?\\n[^\\r\\n]*\\S[^\\r\\n]*)*)");
        String lastState = "no handler section";
        do {
            Matcher handler = sections.matcher(shell("dumpsys activity service com.android.systemui/.SystemUIService"));
            while (handler.find()) {
                String state = handler.group(1);
                lastState = state.substring(0, Math.min(state.length(), 1800));
                // В новых SystemUI поле переименовано: mode=2 уже проверен отдельно.
                boolean gestureHandling = state.contains("mIsGesturalModeEnabled=true")
                        || state.contains("mIsGestureHandlingEnabled=true");
                if (state.contains("mIsEnabled=true") && state.contains("mIsAttached=true")
                        && gestureHandling) return;
            }
            SystemClock.sleep(250);
        } while (SystemClock.uptimeMillis() < limit);
        fail("SystemUI edge handler unavailable; start dedicated AVD with -EmulatorSoftwareNavigation. " + lastState);
    }

    private String shell(String command) throws Exception {
        ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
