package com.vpe_soft.intime.intime.database;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;

/**
 * Драйвер только для upgradeSmoke APK. Старый release обфусцирует Kotlin, поэтому подготовка
 * использует framework Instrumentation и JUnit без AndroidX runner. После обновления доступны
 * библиотеки нового APK, и регистрируется AndroidX InstrumentationRegistry для проверок Room.
 * В обычной сборке используется стандартный AndroidJUnitRunner.
 */
public class UpgradeSmokeInstrumentation extends Instrumentation {
    private static UpgradeSmokeInstrumentation instance;
    private static Bundle arguments;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        instance = this;
        arguments = args;
        start();
    }

    static boolean isPrepare() { return arguments != null && "prepare".equals(arguments.getString("upgradePhase")); }
    static Context targetContext() { return instance.getTargetContext(); }

    @Override public void onStart() {
        Bundle output = new Bundle();
        try {
            String phase = arguments.getString("upgradePhase");
            String name;
            String method;
            if ("prepare".equals(phase)) {
                name = "com.vpe_soft.intime.intime.database.UpgradePrepareTest";
                method = "prepareOldApplication";
            } else {
                // Реестр загружается только с новым APK; у старого нет требуемого Kotlin runtime.
                Class<?> registry = Class.forName("androidx.test.platform.app.InstrumentationRegistry");
                registry.getMethod("registerInstance", Instrumentation.class, Bundle.class).invoke(null, this, arguments);
                name = "com.vpe_soft.intime.intime.database.UpgradeVerificationTest";
                if ("verify".equals(phase)) method = "verifyAfterUpdate";
                else if ("restart".equals(phase)) method = "verifyAfterRestart";
                else throw new IllegalArgumentException("Unknown upgrade phase");
            }
            Result result = new JUnitCore().run(Request.method(Class.forName(name), method));
            if (!result.wasSuccessful() || result.getRunCount() != 1 || result.getAssumptionFailureCount() != 0) {
                StringBuilder details = new StringBuilder("FAILURES!!!\n");
                for (org.junit.runner.notification.Failure failure : result.getFailures()) details.append(failure.getTrace());
                throw new AssertionError(details.toString());
            }
            output.putString("stream", "\nOK (1 test)\n");
            finish(Activity.RESULT_OK, output);
        } catch (Throwable error) {
            output.putString("stream", "FAILURES!!!\n" + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, output);
        }
    }
}
