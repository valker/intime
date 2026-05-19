package com.vpe_soft.intime.intime.import_export;

import static org.junit.Assert.assertEquals;

import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class BackupExportTest {

    @Test
    public void roundTrip_preservesTaskFields() throws Exception {
        TaskEntity task = new TaskEntity("Water plants", 2, 3, 1000L, 900L, 100L, 1);
        task.setId(42L);
        List<TaskEntity> exported = Collections.singletonList(task);

        String json = BackupExport.toJson(exported);
        List<TaskEntity> imported = BackupImport.parseTasks(json);

        assertEquals(1, imported.size());
        assertEquals(42L, imported.get(0).id);
        assertEquals("Water plants", imported.get(0).description);
        assertEquals(Long.valueOf(1000), imported.get(0).nextAlarm);
    }
}
