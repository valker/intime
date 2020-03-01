package com.vpe_soft.intime.intime;
import android.app.Activity;
import android.content.Context;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import androidx.recyclerview.widget.RecyclerView;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.database.Cursor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import androidx.cardview.widget.CardView;
import android.graphics.Typeface;
import android.widget.LinearLayout;

public class TaskRecyclerViewAdapter extends RecyclerView.Adapter<TaskRecyclerViewAdapter.TaskRVViewHolder> {
    //    private Locale locale;
//    private static final String SKELETON = "jjmm ddMMyyyy";
//    private String taskdescription;
//    private String taskdate;
//    private Cursor tasksCursor;
    private String[] titles;
    private String[] dates;
    private String[] types;
    private Context context;

    public TaskRecyclerViewAdapter(Context context, String[] titles, String[] dates, String[] types){
        this.context = context;
        this.titles = titles;
        this.dates = dates;
        this.types = types;
//        this.locale = locale;
//        this.context = activity.getApplicationContext();
//        InTimeOpenHelper openHelper = new InTimeOpenHelper(activity);
//        SQLiteDatabase database = openHelper.getReadableDatabase();
//        this.tasksCursor = database.query(
//			Util.TASK_TABLE,
//			new String[]{
//				"description",
//				"id AS _id",
//				"next_alarm",
//				"next_caution"},
//			null, null,
//			null,
//			null,
//			"next_alarm");
    }
    @Override
    public TaskRVViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View itemView = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.task_item,viewGroup,false);
        return new TaskRVViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(TaskRVViewHolder viewHolder, int i) {
//        taskdescription = tasksCursor.getString(tasksCursor.getColumnIndexOrThrow("description"));
//        long next_alarm = tasksCursor.getLong(tasksCursor.getColumnIndexOrThrow("next_alarm"));
//        long next_caution = tasksCursor.getLong(tasksCursor.getColumnIndexOrThrow("next_caution"));
//        final long currentTimeMillis = System.currentTimeMillis();
//        Date date = new Date(next_alarm);
//        final String pattern = DateFormat.getBestDateTimePattern(locale, SKELETON);
//        SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
//        format.setTimeZone(TimeZone.getDefault());
//        taskdate = format.format(date);
//        final int type = currentTimeMillis > next_caution ? currentTimeMillis > next_alarm?2:0:0;
//		switch(type){
//			case 0://white
//				myViewHolder.foreground.setBackground(new PaintDrawable(Color.WHITE));
//				break;
//			case 2://red
//				myViewHolder.foreground.setBackground(new PaintDrawable(Color.RED));
//				break;
//			case 1://yellow
//				myViewHolder.foreground.setBackground(new PaintDrawable(Color.YELLOW));
//				break;
//		}
        setPhase(null, viewHolder, i, Integer.parseInt(types[i]));
    }

    @Override
    public int getItemCount() {
        return dates.length;
    }

    public void setPhase(TaskRecyclerViewAdapter adapter, TaskRVViewHolder viewHolder, int pos, int phase){
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
                viewHolder.title.setTextColor(Color.parseColor("#FFFFFF"));
                viewHolder.date.setTextColor(Color.parseColor("#FFFFFF"));
                break;
            case 2:
                viewHolder.linear.setBackgroundColor(Color.parseColor("#D8232A"));
                viewHolder.title.setTextColor(Color.parseColor("#FFFFFF"));
                viewHolder.date.setTextColor(Color.parseColor("#FFFFFF"));
                break;
        }
        viewHolder.title.setText(titles[pos]);
        viewHolder.date.setText(dates[pos]);
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
