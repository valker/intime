package com.vpe_soft.intime.intime.recyclerview;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;

import androidx.cardview.widget.CardView;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.util.Util;

import java.util.Locale;

public class TaskRecyclerViewAdapter extends RecyclerViewCursorAdapter<TaskRecyclerViewAdapter.TaskRecyclerViewVH>{

    public TaskRecyclerViewAdapter(Context context, Cursor cursor, Locale locale) {
        super(context, locale);
        setupCursorAdapter(cursor, 0, R.layout.task_item, false);
    }

    @Override
    public TaskRecyclerViewVH onCreateViewHolder(ViewGroup viewGroup, int i) {
        View itemView = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.task_item,viewGroup,false);
        return new TaskRecyclerViewVH(itemView);
    }

    @Override
    public void onBindViewHolder(TaskRecyclerViewVH holder, int position) {
        cursorAdapter.getCursor().moveToPosition(position);
        setViewHolder(holder);
        cursorAdapter.bindView(null, context, cursorAdapter.getCursor());
    }

    public class TaskRecyclerViewVH extends RecyclerViewCursorViewHolder {
        public TextView title;
        public TextView date;
        public CardView card;
        public LinearLayout linear;
        public TaskRecyclerViewVH(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.textview1);
            date = itemView.findViewById(R.id.textview2);
            card = itemView.findViewById(R.id.linear1);
            linear = itemView.findViewById(R.id.linear2);
        }

        @Override
        public void bindCursor(Cursor cursor) {
            String description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
            long nextAlarm = cursor.getLong(cursor.getColumnIndexOrThrow("next_alarm"));
            long nextCaution = cursor.getLong(cursor.getColumnIndexOrThrow("next_caution"));
            Log.d("tag", "desc " + description);
            Log.d("tag", "next_alarm " + nextAlarm);
            Log.d("tag", "next_caution " + nextCaution);
            // 0 - not ready (white), 1 - almost (yellow), 2 - ready (red)
            int phase = System.currentTimeMillis() > nextCaution ? System.currentTimeMillis() > nextCaution ? 2 : 1 : 0;
            Log.d("tag", "phase " + phase);
            card.setCardElevation(12f);
            card.setRadius(40f);
            switch(phase){
                case 0:
                    linear.setBackgroundColor(Color.parseColor("#FFFFFF"));
                    title.setTextColor(Color.parseColor("#000000"));
                    date.setTextColor(Color.parseColor("#757575"));
                    break;
                case 1:
                    linear.setBackgroundColor(Color.parseColor("#FFC627"));
                    title.setTextColor(Color.parseColor("#F7F7F7"));
                    date.setTextColor(Color.parseColor("#F7F7F7"));
                    break;
                case 2:
                    linear.setBackgroundColor(Color.parseColor("#D8232A"));
                    title.setTextColor(Color.parseColor("#F7F7F7"));
                    date.setTextColor(Color.parseColor("#F7F7F7"));
                    break;
            }
            title.setText(description);
            date.setText("date " + Util.getDateFromNextAlarm(locale, nextAlarm) + "\n"
                    + "next_alarm " + nextAlarm + "\n"
                    + "next_caution " + nextCaution + "\n"
                    + "id " + cursor.getPosition() + "\n"
                    + "phase " + phase + "\n"

            );
            title.setTypeface(Typeface.createFromAsset(context.getAssets(),"font/font.ttf"), Typeface.BOLD);
            date.setTypeface(Typeface.createFromAsset(context.getAssets(),"font/font.ttf"), Typeface.BOLD);
        }
    }
}
