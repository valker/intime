package com.vpe_soft.intime.intime.import_export;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.AlarmManager;
import android.content.Context;
import android.os.Looper;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.vpe_soft.intime.intime.concurrent.AppExecutors;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
import com.vpe_soft.intime.intime.scheduling.SchedulingCoordinator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ImportValidationStateTest {
    /**
     * Создаёт две задачи в Room в памяти, включая wasNotified = true, и устанавливает будущий alarm.
     * Через асинхронный репозиторий последовательно импортирует неверные параметры, переполнение
     * календарного интервала, нулевой результат деления, слишком большую дату, ID/дубликаты,
     * типы, версию и порядок columns. В части файлов первая строка корректна, ошибка находится
     * позже: нельзя применить частичный импорт. После каждого callback ошибки проверяет все поля
     * обеих задач, wasNotified, число строк и тот же объект alarm с неизменным сроком; успех не
     * вызывается. Проверяется сохранение состояния при валидации, без аварии во время commit,
     * системного Android alarm и пользовательских файлов базы.
     */
    @Test public void invalidBackupKeepsAllTaskFieldsAndExistingAlarm() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        AppDatabase.setTestInstance(database);
        try {
            long future = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
            TaskEntity first = new TaskEntity("Сохранить первую", 1, 1, future, future - 60000, 1000, 1);
            first.setId(11);
            first.setWasNotified(true);
            TaskEntity second = new TaskEntity("Сохранить вторую", 2, 3, future + 3600000, future, 2000, 2);
            second.setId(22);
            database.taskDao().insert(first);
            database.taskDao().insert(second);
            AppExecutors.executeTask("initial schedule", () -> SchedulingCoordinator.reschedule(context));
            drain();
            AlarmManager manager = context.getSystemService(AlarmManager.class);
            ScheduledAlarm original = shadowOf(manager).getScheduledAlarms().get(0);
            TaskRepository repository = new TaskRepository(context);
            String valid = "[7,\"Valid prefix\",1,1," + future + ",0,0,1]";
            String[] invalid = {
                    rows(valid + ",[8,\"Bad interval\",6,1,2000,1000,0,1]"),
                    rows(valid + ",[8,\"Bad amount\",1,0,2000,1000,0,1]"),
                    rows(valid + ",[8,\"Bad quant\",1,1,2000,1000,0,0]"),
                    rows(valid + "," + valid), rows("[0,\"Bad ID\",1,1,2000,1000,0,1]"),
                    rows(valid + ",[8,\"Bad type\",1,1.5,2000,1000,0,1]"),
                    rows(valid + ",[8,null,1,1,2000,1000,0,1]"),
                    "{\"meta\":{\"version\":2},\"tables\":{\"tasks\":{\"rows\":[]}}}",
                    "{\"tables\":{\"tasks\":{\"columns\":[],\"rows\":[]}}}",
                    rows(valid + ",[8,\"Years overflow\",5,2147483647,2000,1900,0,1]"),
                    rows(valid + ",[8,\"Zero interval\",0,1,2000,1900,0,60001]"),
                    rows(valid + ",[8,\"Date overflow\",0,1,253402300800000,1900,0,1]")
            };
            AtomicInteger errors = new AtomicInteger();
            for (int i = 0; i < invalid.length; i++) {
                repository.replaceAllWithImportFromJson(context, invalid[i], () -> fail("Invalid backup accepted"), error -> {
                    assertSame(Looper.getMainLooper(), Looper.myLooper());
                    assertTrue(error instanceof IllegalArgumentException);
                    errors.incrementAndGet();
                });
                drain();
                shadowOf(Looper.getMainLooper()).idle();
                assertEquals(i + 1, errors.get());
                assertEquals(2, database.taskDao().getTaskCount());
                TaskEntity actualFirst = database.taskDao().getRawTaskById(11);
                TaskEntity actualSecond = database.taskDao().getRawTaskById(22);
                assertEquals(first, actualFirst);
                assertEquals(second, actualSecond);
                assertTrue(actualFirst.isWasNotified());
                assertFalse(actualSecond.isWasNotified());
                assertEquals(1, shadowOf(manager).getScheduledAlarms().size());
                assertSame(original, shadowOf(manager).getScheduledAlarms().get(0));
                assertEquals(future, original.triggerAtTime);
            }
        } finally {
            drain();
            database.close();
            AppDatabase.setTestInstance(null);
        }
    }

    private static String rows(String content) { return "{\"tables\":{\"tasks\":{\"rows\":[" + content + "]}}}"; }
    private static void drain() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AppExecutors.executeTask("import validation barrier", completed::countDown);
        assertTrue(completed.await(10, TimeUnit.SECONDS));
    }
}
