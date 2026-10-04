package com.vpe_soft.intime.intime.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
import com.vpe_soft.intime.intime.workers.TaskNotificationWorker;

import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 24)
public class ScreenPlatformTest {
    private Context context;
    private AppDatabase database;
    private long taskId;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Runtime-разрешение появилось на API 33; более ранняя ОС не принимает его выдачу.
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        }
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
     * разрешение уведомлений на API 33+, чтобы системный диалог не перекрывал экраны;
     * на API 24–32 runtime-разрешение отсутствует. Последовательно
     * открываются список, добавление, настройки и детали этой задачи. После layout проверяются
     * полные экранные границы кнопок и описания: они должны находиться внутри области окна
     * за вычетом system bars/display cutout. Проверка использует реальные WindowInsets ОС
     * через AndroidX compat; на API 24 нет display cutout. Размер экрана читается через
     * WindowMetrics на API 30+, а на API 24–29 через Display.getRealSize.
     * Это позволяет выявить перекрытие при обязательном edge-to-edge на Android 16/target 36
     * и проверить выбранные элементы в обычном окне Android 7.
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

    /**
     * В in-memory Room есть одна будущая задача, уведомления разрешены на API 33+.
     * Для изоляции отменяется прежняя уникальная periodic work тестового приложения.
     * Открывается настоящий MainActivityV2: именно его onCreate, а не тестовая
     * постановка PeriodicWorkRequest, должен зарегистрировать TaskNotificationWorker.
     * Через настоящий WorkManager ожидается одна незавершённая работа ENQUEUED
     * с периодом 15 минут, BatteryNotLow и без требования сети, с будущим сроком
     * после первого исполнения. Её UUID/срок запоминаются. Затем Activity пересоздаётся
     * через recreate и открывается новым ActivityScenario после закрытия прежнего:
     * в обоих случаях должна остаться ровно одна активная работа с тем же UUID и
     * прежним сроком (ExistingPeriodicWorkPolicy.KEEP не сбрасывает расписание).
     * Отменённые исторические записи не считаются активными. В finally отменяется
     * созданный UUID. Тест не ждёт 15-минутной доставки, не убивает процесс, не
     * проверяет notification/ACK, Doze, release/R8 или поведение после force-stop.
     */
    @Test
    public void mainStartup_keepsOnePeriodicReminderAcrossRecreationAndReopening() throws Exception {
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(TaskNotificationWorker.class.getName()).getResult().get(10, TimeUnit.SECONDS);
        UUID createdId = null;
        try {
            long originalDeadline;
            try (ActivityScenario<MainActivityV2> scenario = ActivityScenario.launch(MainActivityV2.class)) {
                WorkInfo initial = awaitSinglePeriodicReminder(workManager);
                createdId = initial.getId();
                originalDeadline = initial.getNextScheduleTimeMillis();
                assertNotNull(initial.getPeriodicityInfo());
                assertEquals(TimeUnit.MINUTES.toMillis(15), initial.getPeriodicityInfo().getRepeatIntervalMillis());
                assertTrue(initial.getConstraints().requiresBatteryNotLow());
                assertEquals(NetworkType.NOT_REQUIRED, initial.getConstraints().getRequiredNetworkType());
                scenario.recreate();
                WorkInfo recreated = awaitSinglePeriodicReminder(workManager);
                assertEquals(createdId, recreated.getId());
                assertEquals(originalDeadline, recreated.getNextScheduleTimeMillis());
            }
            try (ActivityScenario<MainActivityV2> reopened = ActivityScenario.launch(MainActivityV2.class)) {
                WorkInfo retained = awaitSinglePeriodicReminder(workManager);
                assertEquals(createdId, retained.getId());
                assertEquals(originalDeadline, retained.getNextScheduleTimeMillis());
            }
        } finally {
            if (createdId != null) {
                workManager.cancelWorkById(createdId).getResult().get(10, TimeUnit.SECONDS);
            }
        }
    }

    private WorkInfo awaitSinglePeriodicReminder(WorkManager workManager) throws Exception {
        long limit = SystemClock.elapsedRealtime() + 15000;
        List<WorkInfo> active = new ArrayList<>();
        do {
            active.clear();
            for (WorkInfo info : workManager.getWorkInfosForUniqueWork(TaskNotificationWorker.class.getName())
                    .get(5, TimeUnit.SECONDS)) {
                if (!info.getState().isFinished()) active.add(info);
            }
            assertTrue("Duplicate active periodic reminders", active.size() <= 1);
            if (active.size() == 1 && active.get(0).getState() == WorkInfo.State.ENQUEUED
                    && active.get(0).getNextScheduleTimeMillis() > System.currentTimeMillis()) return active.get(0);
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < limit);
        fail("MainActivity did not register an enqueued periodic reminder: " + active);
        return null;
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
                WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
                assertNotNull(windowInsets);
                Insets insets = windowInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                Rect bounds;
                if (Build.VERSION.SDK_INT >= 30) {
                    bounds = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
                } else {
                    Point size = new Point();
                    activity.getWindowManager().getDefaultDisplay().getRealSize(size);
                    bounds = new Rect(0, 0, size.x, size.y);
                }
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
