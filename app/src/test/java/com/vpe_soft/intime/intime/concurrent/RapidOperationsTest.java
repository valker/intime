package com.vpe_soft.intime.intime.concurrent;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.AlarmManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.Looper;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RapidOperationsTest {
    private Context context;
    private AppDatabase database;
    private TaskRepository repository;
    private long future;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        AppDatabase.setTestInstance(database);
        repository = new TaskRepository(context);
        future = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2);
    }

    @After public void tearDown() throws Exception {
        drainTasks();
        database.close();
        AppDatabase.setTestInstance(null);
    }

    /**
     * Без промежуточного ожидания ставит в очередь 30 наборов insert/edit/ACK/delete с разными ID.
     * После общего барьера ожидается пустая база и отсутствие alarm: поздний ACK не должен
     * восстановить удалённую запись. Проверяются 120 операций настоящего репозитория с Room
     * в памяти; нажатия UI и системные сроки доставки broadcast не моделируются.
     */
    @Test public void rapidInsertEditAckDeleteLeavesNoTasksOrAlarm() throws Exception {
        for (int i = 1; i <= 30; i++) {
            repository.insert(task(i, "Initial"));
            repository.update(task(i, "Edited"));
            repository.acknowledgeTaskAsync(i);
            repository.deleteTaskById(i);
        }
        drainTasks();
        assertEquals(0, database.taskDao().getTaskCount());
        assertTrue(shadowOf(manager()).getScheduledAlarms().isEmpty());
    }

    /**
     * В очередь подряд отправляются edit, ACK, полная замена через JSON и удаление старого ID.
     * После завершения должна остаться только импортированная задача с исходными lastAck и сроком,
     * а единственный alarm должен соответствовать ей. Callback успеха должен один раз выполниться
     * на основном потоке, callback ошибки не вызывается. JSON и база полностью синтетические.
     */
    @Test public void importAfterEditAndAckReplacesOldStateAndKeepsImportedAlarm() throws Exception {
        database.taskDao().insert(task(1, "Old"));
        AtomicInteger success = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        repository.update(task(1, "Edited"));
        repository.acknowledgeTaskAsync(1);
        repository.replaceAllWithImportFromJson(context, json(7), () -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            success.incrementAndGet();
        }, error -> errors.incrementAndGet());
        repository.deleteTaskById(1);
        drainTasks();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, success.get());
        assertEquals(0, errors.get());
        assertImportedState(7);
    }

    /**
     * Передаёт заведомо некорректный JSON, сразу за ним — правильную замену и ACK отсутствующего ID.
     * Проверяет один callback ошибки на основном потоке и один callback успеха, продолжение очереди
     * после ошибки и итоговую импортированную задачу с её alarm. Это проверка порядка и обработки
     * ошибки; полный набор правил валидации backup остаётся отдельным шагом R3.
     */
    @Test public void invalidImportDoesNotStopFollowingImportAndMissingAck() throws Exception {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        repository.replaceAllWithImportFromJson(context, "not json", () -> fail("Invalid import succeeded"), error -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            errors.incrementAndGet();
        });
        repository.replaceAllWithImportFromJson(context, json(9), success::incrementAndGet, error -> fail(error.toString()));
        repository.acknowledgeTaskAsync(404);
        drainTasks();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, errors.get());
        assertEquals(1, success.get());
        assertImportedState(9);
    }

    /**
     * ACK из отдельного потока приостанавливается после чтения старой задачи, перед расчётом срока.
     * Одновременно репозиторий импортирует другую задачу с тем же ID. Импорт должен ждать транзакцию
     * ACK; после её завершения полностью заменить запись. Проверяются описание, lastAck = 0,
     * точный импортированный срок и alarm: ACK не должен смешать старые параметры с новой записью.
     * Пауза управляется защёлками; реальный broadcast здесь не запускается.
     */
    @Test public void concurrentAckAndImportWithSameIdDoNotMixTaskVersions() throws Exception {
        database.taskDao().insert(task(7, "Before import"));
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Context paused = new ContextWrapper(context) {
            @Override public Context getApplicationContext() { return this; }
            @Override public Resources getResources() {
                read.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Pause timeout");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                return super.getResources();
            }
        };
        TaskRepository ackRepository = new TaskRepository(paused);
        FutureTask<Void> ack = new FutureTask<>(() -> { ackRepository.acknowledgeTaskById(7); return null; });
        new Thread(ack, "ack-import-test").start();
        CountDownLatch importStarted = new CountDownLatch(1);
        CountDownLatch importDone = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        try {
            assertTrue(read.await(10, TimeUnit.SECONDS));
            AppExecutors.executeTask("import started", importStarted::countDown);
            repository.replaceAllWithImportFromJson(context, json(7), success::incrementAndGet, error -> fail(error.toString()));
            AppExecutors.executeTask("import done", importDone::countDown);
            assertTrue(importStarted.await(10, TimeUnit.SECONDS));
            assertFalse("Import must wait for ACK transaction", importDone.await(200, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
            ack.get(10, TimeUnit.SECONDS);
            drainTasks();
        }
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, success.get());
        assertImportedState(7);
    }

    /**
     * Имитирует форму редактирования, открытую до ACK: её lastAck = 0 и старый срок сохраняются
     * в объекте. Ставит ACK и сразу сохранение формы с интервалом два часа. После очереди ожидает
     * сохранённую свежую отметку ACK и срок ровно через два часа от неё, новое описание и такой же
     * alarm. Проверяет, что устаревшая форма не откатывает ACK; настоящий экран не запускается.
     */
    @Test public void staleEditAfterAckPreservesAcknowledgementAndUsesNewInterval() throws Exception {
        database.taskDao().insert(task(1, "Before"));
        TaskEntity stale = task(1, "Edited after ACK");
        stale.amount = 2;
        repository.acknowledgeTaskAsync(1);
        repository.update(stale);
        drainTasks();
        TaskEntity actual = database.taskDao().getRawTaskById(1);
        assertTrue(actual.lastAck > 0);
        assertEquals("Edited after ACK", actual.description);
        assertEquals(2, actual.amount.intValue());
        assertEquals(actual.lastAck + TimeUnit.HOURS.toMillis(2), actual.nextAlarm.longValue());
        assertEquals(actual.nextAlarm.longValue(), shadowOf(manager()).getScheduledAlarms().get(0).triggerAtTime);
    }

    /**
     * Удерживает очередь задач защёлкой, отправляет объект редактирования, затем меняет его описание
     * до выполнения update. После освобождения очереди в Room должно оказаться описание,
     * существовавшее при отправке, а не позднейшее изменение объекта. Проверяет снимок данных формы
     * при постановке в очередь; вставка задач с автоматически назначаемым ID здесь не проверяется.
     */
    @Test public void queuedEditUsesSnapshotFromSubmission() throws Exception {
        database.taskDao().insert(task(1, "Before"));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AppExecutors.executeTask("hold edits", () -> {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Pause timeout");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        });
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            TaskEntity edit = task(1, "Submitted");
            repository.update(edit);
            edit.description = "Changed later";
        } finally {
            release.countDown();
            drainTasks();
        }
        assertEquals("Submitted", database.taskDao().getRawTaskById(1).description);
    }

    private void assertImportedState(long id) {
        assertEquals(1, database.taskDao().getTaskCount());
        TaskEntity imported = database.taskDao().getRawTaskById(id);
        assertNotNull(imported);
        assertEquals("Imported", imported.description);
        assertEquals(0L, imported.lastAck.longValue());
        assertEquals(future, imported.nextAlarm.longValue());
        assertEquals(1, shadowOf(manager()).getScheduledAlarms().size());
        assertEquals(future, shadowOf(manager()).getScheduledAlarms().get(0).triggerAtTime);
    }

    private TaskEntity task(long id, String description) {
        TaskEntity task = new TaskEntity(description, 1, 1, future, future - 60000, 0, 1);
        task.setId(id);
        return task;
    }
    private String json(long id) {
        return "{\"tables\":{\"tasks\":{\"rows\":[[" + id + ",\"Imported\",1,2," + future + "," + (future - 60000) + ",0,1]]}}}";
    }
    private AlarmManager manager() { return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE); }
    private void drainTasks() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AppExecutors.executeTask("rapid operations barrier", done::countDown);
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }
}
