package com.vpe_soft.intime.intime.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.database.entities.TaskEntity;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class TaskAdapter extends ListAdapter<TaskEntity, TaskAdapter.TaskViewHolder> {

    private final OnTaskClickListener listener;

    public TaskAdapter(@NonNull OnTaskClickListener listener) {
        super(TaskEntity.DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        TaskEntity task = getItem(position);
        holder.bind(task);
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private final TextView textDescription;
        private final TextView textNextAlarm;

        TaskViewHolder(View itemView) {
            super(itemView);
            textDescription = itemView.findViewById(R.id.textDescription);
            textNextAlarm = itemView.findViewById(R.id.textNextAlarm);

            itemView.setOnClickListener(v -> listener.onTaskClick(getItem(getAdapterPosition())));
        }

        void bind(TaskEntity task) {
            textDescription.setText(task.getDescription());

            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
            textNextAlarm.setText(dateFormat.format(task.getNextAlarm()));
        }
    }

    public interface OnTaskClickListener {
        void onTaskClick(TaskEntity task);
    }
}