package intime.smoke;

import com.android.uiautomator.core.Configurator;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;
import java.io.File;
import android.os.Environment;
import android.os.SystemClock;

/** Отдельный shell-драйвер: не включается в APK приложения и не меняет его базу. */
@SuppressWarnings("deprecation")
public class ReleaseHierarchyProbe extends UiAutomatorTestCase {
    /**
     * На проектном AVD открыто окно, которое может обновляться каждую секунду.
     * Удаляем прошлый XML, отключаем ожидание idle только для этого shell-прогона
     * и снимаем актуальную accessibility-иерархию штатным UiDevice.
     * Во время смены Activity root может временно отсутствовать: до пяти секунд
     * повторяем получение нового файла, не принимая XML прошлого окна.
     * Проверяем существование непустого нового файла; смысл элементов проверяет
     * PowerShell smoke по resource-id/text. Этот probe не проверяет CRUD сам по себе,
     * не вызывает код приложения и не считается отдельной release smoke-фазой.
     * Старый timeout восстанавливаем, чтобы не повлиять на другие прогоны драйвера.
     */
    public void testDumpActiveWindow() {
        File output = new File("/data/local/tmp/intime-release-ui.xml");
        if (output.exists()) assertTrue("Cannot delete stale hierarchy", output.delete());
        // Legacy shell-runner задаёт ANDROID_DATA=/data/local/tmp. UiDevice
        // добавляет к этому local/tmp; вычисляем его фактический путь и переносим
        // только свежий файл в постоянный путь, читаемый PowerShell.
        File generated = new File(Environment.getDataDirectory(), "local/tmp/intime-release-probe-output.xml");
        if (!generated.getParentFile().isDirectory()) assertTrue(generated.getParentFile().mkdirs());
        if (generated.exists()) assertTrue("Cannot delete stale probe output", generated.delete());
        long previous = Configurator.getInstance().getWaitForIdleTimeout();
        try {
            Configurator.getInstance().setWaitForIdleTimeout(0);
            long deadline = SystemClock.uptimeMillis() + 5000;
            do {
                getUiDevice().dumpWindowHierarchy("intime-release-probe-output.xml");
                if (generated.isFile() && generated.length() > 0) break;
                SystemClock.sleep(100);
            } while (SystemClock.uptimeMillis() < deadline);
            assertTrue("Probe did not create hierarchy", generated.isFile() && generated.length() > 0);
            assertTrue("Cannot move fresh hierarchy", generated.renameTo(output));
            assertTrue("Fresh hierarchy missing", output.isFile() && output.length() > 0);
            System.out.println("INTIME_FRESH_HIERARCHY_READY");
        } finally {
            Configurator.getInstance().setWaitForIdleTimeout(previous);
        }
    }
}
