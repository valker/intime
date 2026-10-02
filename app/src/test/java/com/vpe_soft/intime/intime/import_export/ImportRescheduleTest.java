package com.vpe_soft.intime.intime.import_export;

import static org.junit.Assert.assertEquals;

import static org.robolectric.Shadows.shadowOf;

import android.app.AlarmManager;
import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.scheduling.SchedulingCoordinator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ImportRescheduleTest {

    private AppDatabase database;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        // Inject test database so SchedulingCoordinator uses the same instance
        AppDatabase.setTestInstance(database);
    }

    @After
    public void tearDown() {
        database.close();
        AppDatabase.setTestInstance(null);
    }

    /**
     * Проверяет: Импортируется задача id = 7 со сроком через час, проверяются её наличие и описание.
     * После фонового вызова SchedulingCoordinator и ожидания его завершения ожидается ровно один
     * будильник Robolectric с точным сроком из JSON.
     */
    @Test
    public void importReplacementThenReschedule_schedulesAlarmForImportedTask() throws Exception {
        TaskDao taskDao = database.taskDao();

        long futureAlarm = System.currentTimeMillis() + 3600000;
        long cautionTime = futureAlarm - 60000;
        long lastAck = futureAlarm - 7200000;
        String json = "{"
                + "\"tables\":{"
                + "\"tasks\":{"
                + "\"rows\":[[7,\"Imported\",1,1," + futureAlarm + "," + cautionTime + "," + lastAck + ",1]]"
                + "}"
                + "}"
                + "}";
        ImportReplacement.replaceAll(database, json);

        assertEquals(1, taskDao.getTaskCount());
        assertEquals("Imported", taskDao.getRawTaskById(7L).description);

        // Run reschedule from a background thread so SchedulingCoordinator runs inline
        Thread rescheduleThread = new Thread(() -> SchedulingCoordinator.reschedule(context));
        rescheduleThread.start();
        rescheduleThread.join();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = shadowOf(alarmManager);
        List<ScheduledAlarm> alarms = shadowAlarmManager.getScheduledAlarms();

        assertEquals(1, alarms.size());
        assertEquals(futureAlarm, alarms.get(0).triggerAtTime);
    }

    /**
     * Проверяет: Импортируется задача со сроком на 100 секунд раньше now, затем выполняется фоновое
     * перепланирование. Ожидается пустой список будильников Robolectric. Предварительный будильник не
     * создаётся, поэтому отмена существующего расписания непосредственно не проверяется.
     */
    @Test
    public void importReplacementWithoutFutureTasks_cancelsAlarm() throws Exception {
        long now = System.currentTimeMillis();
        long pastAlarm = now - 100000;

        String json = "{"
                + "\"tables\":{"
                + "\"tasks\":{"
                + "\"rows\":[[7,\"Past task\",1,1," + pastAlarm + "," + (pastAlarm - 100) + ",1500,1]]"
                + "}"
                + "}"
                + "}";
        ImportReplacement.replaceAll(database, json);

        Thread rescheduleThread = new Thread(() -> SchedulingCoordinator.reschedule(context));
        rescheduleThread.start();
        rescheduleThread.join();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = shadowOf(alarmManager);
        List<ScheduledAlarm> alarms = shadowAlarmManager.getScheduledAlarms();

        assertEquals(0, alarms.size());
    }

    /**
     * Проверяет: Импортируется rows = [], после чего проверяется отсутствие задач в базе. После
     * фонового перепланирования ожидается также пустой список будильников. Ранее установленный
     * будильник не создаётся: проверяется итоговое отсутствие расписания.
     */
    @Test
    public void importReplacementWithoutTasks_cancelsAlarm() throws Exception {
        String json = "{"
                + "\"tables\":{"
                + "\"tasks\":{"
                + "\"rows\":[]"
                + "}"
                + "}"
                + "}";
        ImportReplacement.replaceAll(database, json);

        assertEquals(0, database.taskDao().getTaskCount());

        Thread rescheduleThread = new Thread(() -> SchedulingCoordinator.reschedule(context));
        rescheduleThread.start();
        rescheduleThread.join();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = shadowOf(alarmManager);
        List<ScheduledAlarm> alarms = shadowAlarmManager.getScheduledAlarms();

        assertEquals(0, alarms.size());
    }
}
