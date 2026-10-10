package intime.smoke;

import com.android.uiautomator.core.Configurator;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;
import java.io.File;
import android.os.Environment;
import android.os.SystemClock;
import android.graphics.Point;

/** Отдельный shell-драйвер: не включается в APK приложения и не меняет его базу. */
@SuppressWarnings("deprecation")
public class ReleaseHierarchyProbe extends UiAutomatorTestCase {
    /**
     * На выделенном AVD с включённой жестовой навигацией уже открыты настройки
     * release-приложения. PowerShell предварительно проверяет EdgeBackGestureHandler.
     * Параметр edgeMode выбирает непрерывный свайп от левого края до 65% ширины:
     * cancel возвращает палец к краю перед отпусканием, commit отпускает вдали.
     * Проверяем портретную ориентацию, допустимый режим и успешный ввод UiDevice;
     * после анимации печатаем маркер. Отсутствие перехода при cancel, возврат
     * к прежнему ActivityRecord при commit и сохранность alarm проверяет host.
     * Этот драйвер не измеряет кадры predictive-анимации, звук или поля Room.
     */
    public void testEdgeBackGesture() {
        String mode = getParams().getString("edgeMode");
        assertTrue("Unknown edge mode", "cancel".equals(mode) || "commit".equals(mode));
        int width = getUiDevice().getDisplayWidth();
        int height = getUiDevice().getDisplayHeight();
        assertTrue("Portrait display required", height > width && width > 100);
        Point start = new Point(1, height / 2);
        Point far = new Point((int) (width * 0.65), height / 2);
        Point[] path = "cancel".equals(mode) ? new Point[]{start, far, start}
                : new Point[]{start, far};
        assertTrue("Edge input failed", getUiDevice().swipe(path, 20));
        SystemClock.sleep(1500);
        System.out.println("INTIME_EDGE_GESTURE_" + mode.toUpperCase(java.util.Locale.ROOT));
    }

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
