package com.vpe_soft.intime.intime.import_export;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlarmManager;
import android.content.Context;
import android.database.SQLException;
import android.os.Looper;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.concurrent.AppExecutors;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
import com.vpe_soft.intime.intime.scheduling.SchedulingCoordinator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ImportTransactionStateTest {
    private Context context;
    private AppDatabase database;
    private TaskRepository repository;
    private AlarmManager manager;
    private TaskEntity oldFirst, oldSecond;
    private ScheduledAlarm original;
    private long future;

    @Before public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        AppDatabase.setTestInstance(database);
        repository = new TaskRepository(context);
        manager = context.getSystemService(AlarmManager.class);
        future = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(4);
        oldFirst = task(7, "Старая первая", 1, 2, future, future - 1000, 1000, 2);
        oldFirst.setWasNotified(true);
        oldSecond = task(22, "Старая вторая", 2, 3, future + 3600000, future, 2000, 3);
        database.taskDao().insertAll(Arrays.asList(oldFirst, oldSecond));
        AppExecutors.executeTask("initial import alarm", () -> SchedulingCoordinator.reschedule(context));
        drain();
        assertEquals(1, shadowOf(manager).getScheduledAlarms().size());
        original = shadowOf(manager).getScheduledAlarms().get(0);
    }

    @After public void tearDown() throws Exception {
        drain();
        database.close();
        AppDatabase.setTestInstance(null);
    }

    /**
     * В Room в памяти находятся две старые задачи и установлен alarm. Корректный backup содержит
     * две новые строки, но AFTER INSERT-триггер прерывает вторую вставку через RAISE(ABORT).
     * Асинхронный импорт должен вызвать ровно один callback ошибки на main: сообщение подтверждает
     * срабатывание триггера, обе старые записи со всеми полями и wasNotified восстанавливаются,
     * новых строк нет, объект и срок alarm прежние. После удаления триггера тот же backup успешно
     * импортируется в той же очереди. Проверяет откат после начала записи и восстановление работы,
     * без сбоя процесса/диска, ошибки commit или настоящей доставки системного alarm.
     */
    @Test public void secondInsertFailureRollsBackTasksAndAlarmThenQueueRecovers() throws Exception {
        List<TaskEntity> imported = replacement();
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_import AFTER INSERT ON tasks WHEN NEW.id = 9 " +
                "BEGIN SELECT RAISE(ABORT, 'forced second insert failure'); END");
        AtomicInteger errors = new AtomicInteger();
        repository.replaceAllWithImportFromJson(context, BackupExport.toJson(imported),
                () -> fail("Write failure reported success"), error -> {
                    assertSame(Looper.getMainLooper(), Looper.myLooper());
                    assertTrue(error.toString(), error instanceof SQLException);
                    assertTrue(error.toString(), error.getMessage().contains("forced second insert failure"));
                    assertOriginalState();
                    errors.incrementAndGet();
                });
        drain();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, errors.get());
        assertOriginalState();
        database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER reject_import");
        importSuccessfully(imported);
        assertImportedAlarm(imported.get(1));
    }

    /**
     * Заменяет две старые записи двумя новыми, одна из которых повторно использует ID 7,
     * а ближайшая будущая строка с ID 9 стоит второй в JSON. После callback успеха на main
     * проверяет все восемь полей каждой строки, точное число задач, удаление ID 22 и сброс
     * wasNotified даже для переиспользованного ID. Единственный alarm должен иметь срок,
     * ID и описание ближайшей новой задачи; старый объект заменён. Проверяется репозиторий,
     * Room и модель AlarmManager Robolectric, без реального ожидания уведомления.
     */
    @Test public void validBackupFullyReplacesFieldsAndSchedulesNearestImportedTask() throws Exception {
        List<TaskEntity> imported = replacement();
        importSuccessfully(imported);
        assertNull(database.taskDao().getRawTaskById(22));
        assertImportedAlarm(imported.get(1));
    }

    /**
     * При существующих задачах и будущем alarm импортирует корректный пустой массив rows.
     * В callback успеха и после него ожидает ноль записей Room и отсутствие alarm.
     * Это намеренная полная очистка через штатный асинхронный импорт, без удаления файлов
     * базы, пользовательских данных или проверки доставки уже показанного уведомления.
     */
    @Test public void emptyBackupClearsAllTasksAndCancelsAlarm() throws Exception {
        importSuccessfully(Collections.emptyList());
        assertTrue(shadowOf(manager).getScheduledAlarms().isEmpty());
    }

    /**
     * Импортирует вместо старых будущих задач две записи с прошлыми nextAlarm, включая нулевой
     * срок. Их поля должны сохраниться без нормализации, wasNotified остаться false, callback
     * успеха выполниться один раз. Поскольку будущих задач нет, прежний alarm отменяется.
     * Повторные уведомления worker и отношения nextCaution/lastAck здесь не проверяются.
     */
    @Test public void pastOnlyBackupPreservesStoredTimesAndCancelsFutureAlarm() throws Exception {
        List<TaskEntity> imported = Arrays.asList(
                task(7, "Прошлая", 1, 1, 2000, 1900, 1000, 1),
                task(9, "Нулевой срок", 0, 5, 0, 0, 0, 2));
        importSuccessfully(imported);
        assertTrue(shadowOf(manager).getScheduledAlarms().isEmpty());
    }

    private List<TaskEntity> replacement() {
        return Arrays.asList(task(7, "Новая с прежним ID", 4, 2, future + 7200000, future, 3000, 4),
                task(9, "Новая ближайшая", 0, 5, future - 3600000, future - 3605000, 4000, 2));
    }

    private void importSuccessfully(List<TaskEntity> imported) throws Exception {
        AtomicInteger successes = new AtomicInteger();
        repository.replaceAllWithImportFromJson(context, BackupExport.toJson(imported), () -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            assertImportedState(imported);
            if (imported.isEmpty() || imported.get(0).nextAlarm < System.currentTimeMillis()) {
                assertTrue(shadowOf(manager).getScheduledAlarms().isEmpty());
            } else {
                assertImportedAlarm(imported.get(1));
            }
            successes.incrementAndGet();
        }, error -> fail(error.toString()));
        drain();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, successes.get());
        assertImportedState(imported);
    }

    private void assertImportedState(List<TaskEntity> imported) {
        assertEquals(imported.size(), database.taskDao().getTaskCount());
        for (TaskEntity expected : imported) {
            TaskEntity actual = database.taskDao().getRawTaskById(expected.id);
            assertEquals(expected, actual);
            assertFalse(actual.isWasNotified());
        }
    }

    private void assertOriginalState() {
        assertEquals(2, database.taskDao().getTaskCount());
        assertEquals(oldFirst, database.taskDao().getRawTaskById(7));
        assertEquals(oldSecond, database.taskDao().getRawTaskById(22));
        assertTrue(database.taskDao().getRawTaskById(7).isWasNotified());
        assertFalse(database.taskDao().getRawTaskById(22).isWasNotified());
        assertNull(database.taskDao().getRawTaskById(9));
        assertEquals(1, shadowOf(manager).getScheduledAlarms().size());
        assertSame(original, shadowOf(manager).getScheduledAlarms().get(0));
        assertEquals(future, original.triggerAtTime);
    }

    private void assertImportedAlarm(TaskEntity nearest) {
        assertEquals(1, shadowOf(manager).getScheduledAlarms().size());
        ScheduledAlarm alarm = shadowOf(manager).getScheduledAlarms().get(0);
        assertNotSame(original, alarm);
        assertEquals(nearest.nextAlarm.longValue(), alarm.triggerAtTime);
        assertEquals(nearest.id, shadowOf(alarm.operation).getSavedIntent().getLongExtra(Constants.EXTRA_TASK_ID, -1));
        assertEquals(nearest.description, shadowOf(alarm.operation).getSavedIntent().getStringExtra(Constants.EXTRA_TASK_DESCRIPTION));
    }

    private static TaskEntity task(long id, String description, int interval, int amount,
                                   long alarm, long caution, long ack, int quant) {
        TaskEntity task = new TaskEntity(description, interval, amount, alarm, caution, ack, quant);
        task.setId(id);
        return task;
    }

    private static void drain() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AppExecutors.executeTask("import transaction barrier", completed::countDown);
        assertTrue(completed.await(10, TimeUnit.SECONDS));
    }
}
