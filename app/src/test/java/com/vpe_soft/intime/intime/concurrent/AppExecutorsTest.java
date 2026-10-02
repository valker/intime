package com.vpe_soft.intime.intime.concurrent;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppExecutorsTest {
    /**
     * Ставит подряд сто операций с задачами, затем барьер завершения. Проверяет, что все номера
     * записаны в исходном порядке и все операции выполнены одним и тем же фоновым потоком.
     * Проверяется повторное использование общей очереди, а не число внутренних потоков Room.
     */
    @Test
    public void tasksReuseOneThreadAndPreserveOrder() throws Exception {
        List<Integer> order = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        for (int i = 0; i < 100; i++) {
            int number = i;
            AppExecutors.executeTask("ordered task", () -> {
                order.add(number);
                threads.add(Thread.currentThread());
            });
        }
        AppExecutors.executeTask("barrier", done::countDown);
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(100, order.size());
        for (int i = 0; i < 100; i++) {
            assertEquals(i, order.get(i).intValue());
            assertSame(threads.get(0), threads.get(i));
        }
        assertNotSame(Thread.currentThread(), threads.get(0));
    }

    /**
     * Первая операция receiver выбрасывает исключение, вторая успешно выполняется. Проверяет
     * вызов обеих функций завершения ровно один раз, продолжение очереди и запись имени упавшей
     * операции в журнал с исходным исключением. Это тест обёртки завершения; реальный Android
     * PendingResult и системный таймаут broadcast здесь не моделируются.
     */
    @Test
    public void receiverFailureFinishesAndDoesNotStopQueue() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<String> events = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("fixture failure");
        AppExecutors.executeReceiver("failed receiver", () -> { throw failure; },
                () -> events.add("failed finished"));
        AppExecutors.executeReceiver("next receiver", () -> events.add("next action"), () -> {
            events.add("next finished");
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(java.util.Arrays.asList("failed finished", "next action", "next finished"), events);
        assertTrue(ShadowLog.getLogsForTag("AppExecutors").stream().anyMatch(
                log -> log.msg.contains("failed receiver") && log.throwable == failure));
    }

    /**
     * Удерживает очередь задач защёлкой, имитируя длительный импорт. Пока она занята, отправляет
     * короткую операцию receiver и ждёт её завершения. Доказывает независимость двух очередей;
     * затем освобождает очередь задач в finally и дожидается барьера, чтобы не мешать другим тестам.
     * Длительность настоящего импорта и блокировки SQLite данным тестом не проверяются.
     */
    @Test
    public void receiverDoesNotWaitForTaskQueue() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch drained = new CountDownLatch(1);
        CountDownLatch receiverDone = new CountDownLatch(1);
        AppExecutors.executeTask("blocked import", () -> {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        });
        AppExecutors.executeTask("barrier", drained::countDown);
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            AppExecutors.executeReceiver("independent receiver", () -> { }, receiverDone::countDown);
            assertTrue(receiverDone.await(5, TimeUnit.SECONDS));
            assertEquals(1, drained.getCount());
        } finally {
            release.countDown();
            assertTrue(drained.await(10, TimeUnit.SECONDS));
        }
    }
}
