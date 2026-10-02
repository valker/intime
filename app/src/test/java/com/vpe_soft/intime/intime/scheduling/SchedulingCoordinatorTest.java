package com.vpe_soft.intime.intime.scheduling;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlarmManager;
import android.content.Context;
import android.content.ContextWrapper;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.vpe_soft.intime.intime.concurrent.AppExecutors;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SchedulingCoordinatorTest {
    private AppDatabase database;
    private PausedContext context;
    private AlarmManager manager;
    private TaskEntity task;

    @Before
    public void setUp() {
        Context application = ApplicationProvider.getApplicationContext();
        manager = (AlarmManager) application.getSystemService(Context.ALARM_SERVICE);
        context = new PausedContext(application);
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase.class)
                .allowMainThreadQueries().build();
        AppDatabase.setTestInstance(database);
        long future = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
        task = new TaskEntity("Original", 1, 1, future, future - 60000, 0, 1);
        task.setId(database.taskDao().insert(task));
    }

    @After
    public void tearDown() {
        database.close();
        AppDatabase.setTestInstance(null);
    }

    /**
     * Создаёт задачу через час и приостанавливает первое фоновое перепланирование после чтения
     * старого срока из Room, перед обращением к AlarmManager. Переносит срок ещё на час и запускает
     * второй вызов. Проверяет, что второй ждёт первого, затем остаётся один будильник с новым сроком.
     * Управляемая пауза воспроизводит опасный порядок старой и новой записи; системная доставка
     * уведомления здесь не проверяется, используется AlarmManager Robolectric и база в памяти.
     */
    @Test
    public void backgroundReschedulesCannotOverwriteNewDeadlineWithOldResult() throws Exception {
        verifyCompetition(false, false);
    }

    /**
     * Приостанавливает фоновое перепланирование уже после чтения будущей задачи, удаляет её из Room
     * и запускает второй вызов. Второй должен ждать завершения первого, после чего прочитать пустую
     * базу и отменить установленный первым будильник. Итоговый список alarm должен быть пустым:
     * старый результат не должен воскресить будильник удалённой задачи. Проверяется конкуренция
     * перепланирований, а не весь асинхронный путь удаления через UI или репозиторий.
     */
    @Test
    public void deletionCancelsAlarmAfterOlderRescheduleCompletes() throws Exception {
        verifyCompetition(false, true);
    }

    /**
     * Вызывает перепланирование на основном потоке, как делает UI: запрос уходит в общую очередь.
     * Приостанавливает его после чтения старой задачи, меняет срок в Room и запускает конкурирующий
     * фоновый вызов, как из receiver. Проверяет общую блокировку обоих путей и итоговый новый срок.
     * После освобождения паузы дожидается барьера очереди. Настоящая Activity и PendingResult
     * этим тестом не запускаются; проверяются входы координатора из UI и фонового потока.
     */
    @Test
    public void mainThreadRequestSharesSerializationWithBackgroundCaller() throws Exception {
        verifyCompetition(true, false);
    }

    private void verifyCompetition(boolean fromMain, boolean delete) throws Exception {
        FutureTask<Void> first = null;
        FutureTask<Void> second = null;
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            if (fromMain) {
                SchedulingCoordinator.reschedule(context);
            } else {
                first = startReschedule(null);
            }
            assertTrue("First call must reach the pause", context.readCompleted.await(10, TimeUnit.SECONDS));
            if (delete) {
                database.taskDao().delete(task);
            } else {
                task.nextAlarm += TimeUnit.HOURS.toMillis(1);
                database.taskDao().update(task);
            }
            second = startReschedule(secondStarted);
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
            FutureTask<Void> competing = second;
            assertThrows(TimeoutException.class, () -> competing.get(200, TimeUnit.MILLISECONDS));
        } finally {
            context.release.countDown();
            if (first != null) first.get(10, TimeUnit.SECONDS);
            if (second != null) second.get(10, TimeUnit.SECONDS);
            CountDownLatch drained = new CountDownLatch(1);
            AppExecutors.executeTask("scheduling test barrier", drained::countDown);
            assertTrue(drained.await(10, TimeUnit.SECONDS));
        }
        if (delete) {
            assertTrue(shadowOf(manager).getScheduledAlarms().isEmpty());
        } else {
            assertEquals(1, shadowOf(manager).getScheduledAlarms().size());
            assertEquals(task.nextAlarm.longValue(), shadowOf(manager).getScheduledAlarms().get(0).triggerAtTime);
        }
    }

    private FutureTask<Void> startReschedule(CountDownLatch started) {
        FutureTask<Void> work = new FutureTask<>(() -> {
            if (started != null) started.countDown();
            SchedulingCoordinator.reschedule(context);
            return null;
        });
        new Thread(work, "competing-reschedule-test").start();
        return work;
    }

    /** Пауза стоит после чтения Room, чтобы первый вызов уже имел устаревающий снимок задачи. */
    private static class PausedContext extends ContextWrapper {
        final CountDownLatch readCompleted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean first = new AtomicBoolean(true);

        PausedContext(Context base) { super(base); }

        @Override
        public Context getApplicationContext() { return this; }

        @Override
        public Object getSystemService(String name) {
            if (Context.ALARM_SERVICE.equals(name) && first.compareAndSet(true, false)) {
                readCompleted.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Pause timeout");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            return super.getSystemService(name);
        }
    }
}
