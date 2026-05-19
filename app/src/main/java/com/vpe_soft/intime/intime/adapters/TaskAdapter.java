package com.vpe_soft.intime.intime.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;
import com.vpe_soft.intime.intime.ui.RelativeTimeFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<TaskEntity> tasks = new ArrayList<>();
    private static final int ITEM_TYPE_TASK = 0;
    private static final int ITEM_TYPE_DIVIDER = 1;
    private final OnTaskClickListener listener;
    private int firstUpcomingTaskPosition = -1;
    private long currentTimeMillis = System.currentTimeMillis();

    public TaskAdapter(@NonNull OnTaskClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<TaskEntity> newTasks) {
        tasks.clear();
        if (newTasks != null) {
            tasks.addAll(newTasks);
        }
        notifyDataSetChanged();
    }

    public void setCurrentTimeMillis(long currentTimeMillis) {
        this.currentTimeMillis = currentTimeMillis;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return ITEM_TYPE_TASK;
        }
        if (firstUpcomingTaskPosition > 0 && position == firstUpcomingTaskPosition) {
            return ITEM_TYPE_DIVIDER;
        }
        return ITEM_TYPE_TASK;
    }

    public int findFirstUpcomingTask(List<TaskEntity> taskList) {
        long currentTime = currentTimeMillis;
        int index = Collections.binarySearch(
                taskList,
                TaskEntity.CreateWithNextAlarm(currentTime + 1),
                Comparator.comparingLong(TaskEntity::getNextAlarm)
        );
        if (index < 0) {
            index = -index - 1;
        }
        return (index < taskList.size()) ? index : -1;
    }

    private int toTaskIndex(int adapterPosition) {
        if (firstUpcomingTaskPosition > 0 && adapterPosition >= firstUpcomingTaskPosition) {
            return adapterPosition - 1;
        }
        return adapterPosition;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ITEM_TYPE_TASK) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
            return new TaskViewHolder(itemView);
        }
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_divider, parent, false);
        return new DividerViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof TaskViewHolder) {
            TaskEntity task = tasks.get(toTaskIndex(position));
            ((TaskViewHolder) holder).bind(task, currentTimeMillis);
        } else if (holder instanceof DividerViewHolder) {
            ((DividerViewHolder) holder).bind();
        }
    }

    @Override
    public int getItemCount() {
        firstUpcomingTaskPosition = findFirstUpcomingTask(tasks);
        if (firstUpcomingTaskPosition <= 0) {
            return tasks.size();
        }
        return tasks.size() + 1;
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private final TextView textDescription;
        private final TextView textNextAlarm;

        TaskViewHolder(View itemView) {
            super(itemView);
            textDescription = itemView.findViewById(R.id.textDescription);
            textNextAlarm = itemView.findViewById(R.id.textNextAlarm);

            itemView.setOnClickListener(v -> {
                int adapterPosition = getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION || listener == null) {
                    return;
                }
                listener.onTaskClick(tasks.get(toTaskIndex(adapterPosition)));
            });
        }

        void bind(TaskEntity task, long now) {
            textDescription.setText(task.getDescription());
            textNextAlarm.setText(RelativeTimeFormatter.formatNextAlarm(itemView.getContext(), task.getNextAlarm(), now));

            final Context context = itemView.getContext();
            RelativeTimeFormatter.TaskDisplayState state = RelativeTimeFormatter.getDisplayState(task, now);
            switch (state) {
                case OVERDUE:
                    textDescription.setTextColor(ContextCompat.getColor(context, R.color.red));
                    itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.light_red));
                    break;
                case CAUTION:
                    textDescription.setTextColor(ContextCompat.getColor(context, R.color.cardIndicatorAlmost));
                    itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.light_yellow));
                    break;
                default:
                    textDescription.setTextColor(ContextCompat.getColor(context, R.color.black));
                    itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.white));
                    break;
            }
        }
    }

    public static class DividerViewHolder extends RecyclerView.ViewHolder {
        private final ImageView dividerIcon;

        public DividerViewHolder(View itemView) {
            super(itemView);
            dividerIcon = itemView.findViewById(R.id.dividerIcon);
        }

        public void bind() {
            dividerIcon.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
        }
    }

    public interface OnTaskClickListener {
        void onTaskClick(TaskEntity task);
    }
}
