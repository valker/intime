package com.vpe_soft.intime.intime.concurrent;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlarmManager;
import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.database.repositories.TaskRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RepositoryQueueTest {
    /**
     * Создаёт пустую Room-базу в памяти и два экземпляра репозитория, имитируя разные экраны.
     * Без ожидания между вызовами ставит вставку задачи с известным id и её удаление через второй
     * репозиторий. После барьера общей очереди проверяет, что задача удалена и будильник отменён.
     * Доказывает порядок операций между экземплярами; конкуренция с ACK и импортом проверяется
     * отдельно в RapidOperationsTest. Пользовательская база не открывается.
     */
    @Test
    public void insertThenDeleteAcrossRepositoriesLeavesNoTaskOrAlarm() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        AppDatabase.setTestInstance(database);
        CountDownLatch done = new CountDownLatch(1);
        try {
            TaskRepository first = new TaskRepository(context);
            TaskRepository second = new TaskRepository(context);
            long future = System.currentTimeMillis() + 3600000;
            TaskEntity task = new TaskEntity("Queued task", 1, 1, future, future - 60000, 0, 1);
            task.setId(42);
            first.insert(task);
            second.deleteTaskById(42);
            AppExecutors.executeTask("repository barrier", done::countDown);
            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertEquals(0, database.taskDao().getTaskCount());
            assertTrue(shadowOf((AlarmManager) context.getSystemService(Context.ALARM_SERVICE))
                    .getScheduledAlarms().isEmpty());
        } finally {
            CountDownLatch cleanup = new CountDownLatch(1);
            AppExecutors.executeTask("cleanup barrier", cleanup::countDown);
            assertTrue(cleanup.await(10, TimeUnit.SECONDS));
            database.close();
            AppDatabase.setTestInstance(null);
        }
    }
}
