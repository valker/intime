package com.vpe_soft.intime.intime;

import android.content.Context;
import android.graphics.Color;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Locale;
import androidx.cardview.widget.CardView;
import android.graphics.Typeface;
import android.widget.LinearLayout;

public class TaskRecyclerViewAdapter extends RecyclerView.Adapter<TaskRecyclerViewAdapter.TaskRVViewHolder> {

    private Context context;

    private Locale locale;

    private Task[] tasks;

    public TaskRecyclerViewAdapter(Context context, Locale locale){
        this.context = context;
        this.locale = locale;
        this.tasks = Util.getTasksFromDatabase(context);
    }
    @Override
    public TaskRVViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View itemView = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.task_item,viewGroup,false);
        return new TaskRVViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(TaskRVViewHolder viewHolder, int i) {
        Task task = tasks[i];
        long currentTimeMillis = System.currentTimeMillis();
        // 0 - not ready (white), 1 - almost (yellow), 2 - ready (red)
        int phase = currentTimeMillis > task.getNextCaution() ? currentTimeMillis > task.getNextAlarm() ? 2 : 1 : 0;
        updateCard(viewHolder, task, phase);
    }

    @Override
    public int getItemCount() {
        return Util.getDatabaseLengthFromContext(context);
    }

    public void updateCard(TaskRVViewHolder viewHolder, Task task, int phase){
        viewHolder.card.setCardElevation(12f);
        viewHolder.card.setRadius(40f);
        switch(phase){
            case 0:
                viewHolder.linear.setBackgroundColor(Color.parseColor("#FFFFFF"));
                viewHolder.title.setTextColor(Color.parseColor("#000000"));
                viewHolder.date.setTextColor(Color.parseColor("#757575"));
                break;
            case 1:
                viewHolder.linear.setBackgroundColor(Color.parseColor("#FFC627"));
                viewHolder.title.setTextColor(Color.parseColor("#F7F7F7"));
                viewHolder.date.setTextColor(Color.parseColor("#F7F7F7"));
                break;
            case 2:
                viewHolder.linear.setBackgroundColor(Color.parseColor("#D8232A"));
                viewHolder.title.setTextColor(Color.parseColor("#F7F7F7"));
                viewHolder.date.setTextColor(Color.parseColor("#F7F7F7"));
                break;
        }
        viewHolder.title.setText(task.getDescription());
        viewHolder.date.setText(Util.getDateFromNextAlarm(locale, task.getNextAlarm()));
        viewHolder.title.setTypeface(Typeface.createFromAsset(context.getAssets(),"font/font.ttf"), Typeface.BOLD);
        viewHolder.date.setTypeface(Typeface.createFromAsset(context.getAssets(),"font/font.ttf"), Typeface.BOLD);
    }

    public class TaskRVViewHolder extends  RecyclerView.ViewHolder{
        public TextView title;
        public TextView date;
        public CardView card;
        public LinearLayout linear;
        public TaskRVViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.textview1);
            date = itemView.findViewById(R.id.textview2);
            card = itemView.findViewById(R.id.linear1);
            linear = itemView.findViewById(R.id.linear2);
        }
    }
}
