package com.vpe_soft.intime.intime.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<TaskEntity> tasks = new ArrayList<>();
    private static final int ITEM_TYPE_TASK = 0;
    private static final int ITEM_TYPE_DIVIDER = 1;
    private final OnTaskClickListener listener;
    private int firstUpcomingTaskPosition;

    public TaskAdapter(@NonNull OnTaskClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<TaskEntity> newTasks) {
        tasks.clear();
        tasks.addAll(newTasks);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        // Вставляем разделитель, если задача просрочена

        if(position == 0) return ITEM_TYPE_TASK;

        int firstUpcomingTaskPosition = findFirstUpcomingTask(tasks);

        if (position == firstUpcomingTaskPosition) {
            return ITEM_TYPE_DIVIDER; // Разделитель между просроченными и актуальными задачами
        } else {
            return ITEM_TYPE_TASK; // Обычная задача
        }
    }

    public int findFirstUpcomingTask(List<TaskEntity> tasks) {
        long currentTime = System.currentTimeMillis();

        // Используем binarySearch для поиска позиции
        int index = Collections.binarySearch(tasks, TaskEntity.CreateWithNextAlarm(currentTime+1),
                Comparator.comparingLong(TaskEntity::getNextAlarm));

        // Если элемент найден, index будет >= 0, иначе нужно вернуть первое подходящее
        if (index < 0) {
            index = -index - 1;
        }

        // Если индекс меньше длины списка, возвращаем его, иначе -1
        return (index < tasks.size()) ? index : -1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        if (viewType == ITEM_TYPE_TASK) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
            return new TaskViewHolder(itemView);
        } else {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_divider, parent, false);
            return new DividerViewHolder(itemView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof TaskViewHolder) {
            if(firstUpcomingTaskPosition > 0 && position >= firstUpcomingTaskPosition) {
                position--;
            }
            TaskEntity task = tasks.get(position);
            ((TaskViewHolder) holder).bind(task);
        } else if (holder instanceof DividerViewHolder) {
            // Вставляем иконку разделителя
            ((DividerViewHolder) holder).bind();
        }
    }

    @Override
    public int getItemCount() {
        firstUpcomingTaskPosition = findFirstUpcomingTask(tasks);
        if(firstUpcomingTaskPosition <= 0) return tasks.size();
        else return tasks.size() + 1;
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private final TextView textDescription;
        private final TextView textNextAlarm;

        TaskViewHolder(View itemView) {
            super(itemView);
            textDescription = itemView.findViewById(R.id.textDescription);
            textNextAlarm = itemView.findViewById(R.id.textNextAlarm);

            itemView.setOnClickListener(v -> {
                if(listener != null) {
                    listener.onTaskClick(tasks.get(getAdapterPosition()));
                }
            });
        }

        void bind(TaskEntity task) {
            textDescription.setText(task.getDescription());

            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
            textNextAlarm.setText(dateFormat.format(task.getNextAlarm()));

            // Проверяем, просрочена ли задача
            boolean isOverdue = task.getNextAlarm() < System.currentTimeMillis();

            // Меняем стиль в зависимости от просроченности
            final Context context = itemView.getContext();
            if (isOverdue) {
                textDescription.setTextColor(ContextCompat.getColor(context, R.color.red));
                itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.light_red));
            } else {
                textDescription.setTextColor(ContextCompat.getColor(context, R.color.black));
                itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.white));
            }
        }
    }

    // ViewHolder для разделителя
    public class DividerViewHolder extends RecyclerView.ViewHolder {
        ImageView dividerIcon;

        public DividerViewHolder(View itemView) {
            super(itemView);
            dividerIcon = itemView.findViewById(R.id.dividerIcon);
        }

        public void bind() {
            // Вставляем иконку стрелки
            dividerIcon.setImageResource(R.drawable.baseline_keyboard_arrow_down_24); // Указываем свой ресурс
        }
    }

    public interface OnTaskClickListener {
        void onTaskClick(TaskEntity task);
    }
}