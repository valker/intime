package com.vpe_soft.intime.intime.import_export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import org.junit.Test;

import java.util.List;

public class BackupImportTest {
    /**
     * Проверяет: Разбираются две строки JSON с идентификаторами 42 и 43. Проверяются число задач и оба
     * описания и id; для первой также точно сверяются interval, amount, nextAlarm, nextCaution,
     * lastAck и quant. Остальные поля второй задачи не сравниваются.
     */
    @Test
    public void parseTasks_readsTasksAndPreservesIds() throws Exception {
        String json = "{"
                + "\"tables\":{"
                + "\"tasks\":{"
                + "\"rows\":["
                + "[42,\"Water plants\",2,3,1000,900,100,1],"
                + "[43,\"Take a break\",1,1,2000,1900,1500,2]"
                + "]"
                + "}"
                + "}"
                + "}";

        List<TaskEntity> tasks = BackupImport.parseTasks(json);

        assertEquals(2, tasks.size());
        assertEquals(42L, tasks.get(0).id);
        assertEquals("Water plants", tasks.get(0).description);
        assertEquals(Integer.valueOf(2), tasks.get(0).interval);
        assertEquals(Integer.valueOf(3), tasks.get(0).amount);
        assertEquals(Long.valueOf(1000), tasks.get(0).nextAlarm);
        assertEquals(Long.valueOf(900), tasks.get(0).nextCaution);
        assertEquals(Long.valueOf(100), tasks.get(0).lastAck);
        assertEquals(Integer.valueOf(1), tasks.get(0).quant);

        assertEquals(43L, tasks.get(1).id);
        assertEquals("Take a break", tasks.get(1).description);
    }

    /**
     * Проверяет: В parseTasks передаётся пустой JSON-объект {} без обязательной структуры tables.
     * Ожидается IllegalArgumentException, исключающий успешный разбор такой резервной копии.
     */
    @Test
    public void parseTasks_rejectsMissingTables() {
        assertThrows(IllegalArgumentException.class, () -> BackupImport.parseTasks("{}"));
    }

    /**
     * Проверяет: В структуре tables/tasks/rows передаётся строка только с id и описанием Broken. Из-за
     * отсутствия остальных обязательных значений ожидается IllegalArgumentException.
     */
    @Test
    public void parseTasks_rejectsShortRows() {
        String json = "{"
                + "\"tables\":{"
                + "\"tasks\":{"
                + "\"rows\":[[1,\"Broken\"]]"
                + "}"
                + "}"
                + "}";

        assertThrows(IllegalArgumentException.class, () -> BackupImport.parseTasks(json));
    }
}
