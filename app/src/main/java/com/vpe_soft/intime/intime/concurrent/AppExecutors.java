package com.vpe_soft.intime.intime.concurrent;

import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Общие очереди живут столько же, сколько процесс приложения; Activity их не закрывает. */
public final class AppExecutors {
    private static final String TAG = "AppExecutors";
    private static final ExecutorService TASKS = createQueue("intime-tasks");
    private static final ExecutorService RECEIVERS = createQueue("intime-receivers");

    private AppExecutors() { }

    private static ExecutorService createQueue(String name) {
        return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, name));
    }

    /** Изменения задач и экспорт выполняются в порядке постановки в общую очередь. */
    public static void executeTask(String operation, Runnable action) {
        TASKS.execute(() -> runLogged(operation, action));
    }

    /** Отдельная очередь не заставляет receiver ждать обработки большого backup. */
    public static void executeReceiver(String operation, Runnable action, Runnable finish) {
        RECEIVERS.execute(() -> {
            try {
                runLogged(operation, action);
            } finally {
                finish.run();
            }
        });
    }

    private static void runLogged(String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Background operation failed: " + operation, exception);
        }
    }
}
