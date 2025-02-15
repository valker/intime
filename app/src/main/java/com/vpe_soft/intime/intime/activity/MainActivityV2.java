package com.vpe_soft.intime.intime.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.room.Room;

import com.vpe_soft.intime.intime.Constants;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.AppDatabase;
import com.vpe_soft.intime.intime.database.dao.TaskDao;
import com.vpe_soft.intime.intime.view_models.TaskViewModel;

public class MainActivityV2 extends AppCompatActivity {
    private TaskViewModel taskViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Получаем экземпляр БД и DAO
        AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, Constants.dbName).build();
        TaskDao taskDao = db.taskDao();

        // Создаём ViewModel
        taskViewModel = new ViewModelProvider(this, new TaskViewModel.Factory(taskDao)).get(TaskViewModel.class);

        // Подписываемся на изменения в списке задач
        taskViewModel.getTasks().observe(this, tasks -> {
            // TODO: Обновить RecyclerView, когда список задач изменится
        });
    }
}
