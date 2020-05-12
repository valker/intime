package com.vpe_soft.intime.intime.recyclerview;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.activity.MainActivity;
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
        public LinearLayout indicator;
        public LinearLayout help;
        private String TAG = "TaskRecyclerViewVH";

        public TaskRecyclerViewVH(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            date = itemView.findViewById(R.id.description);
            card = itemView.findViewById(R.id.linear1);
            indicator = itemView.findViewById(R.id.indicator);
            help = itemView.findViewById(R.id.help);
        }

        @Override
        public void bindCursor(Cursor cursor) {
            Log.d(TAG, "bindCursor");
            String description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
            long nextAlarm = cursor.getLong(cursor.getColumnIndexOrThrow("next_alarm"));
            long nextCaution = cursor.getLong(cursor.getColumnIndexOrThrow("next_caution"));
            final long id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            final int pos = cursor.getPosition();
            // 0 - not ready (white), 1 - almost (yellow), 2 - ready (red)
            int phase = System.currentTimeMillis() > nextCaution ? System.currentTimeMillis() > nextAlarm ? 2 : 1 : 0;
            float cornerRadiusSecond = Util.toPx(context, 40);
            card.setRadius(0);
            card.setCardBackgroundColor(Color.WHITE);
            card.setCardElevation(0);
            if (!mainActivity.isDefaultViewOutlineProviderSet) {
                mainActivity.isDefaultViewOutlineProviderSet = true;
                mainActivity.setDefaultViewOutlineProvider(card.getOutlineProvider());
            }
            card.setOutlineProvider(null);
            GradientDrawable gradientDrawable1 = new GradientDrawable();
            gradientDrawable1.setCornerRadius(cornerRadiusSecond);
            GradientDrawable gradientDrawable2 = new GradientDrawable();
            gradientDrawable2.setCornerRadius(cornerRadiusSecond);
            gradientDrawable2.setColor(Color.parseColor("#FFFFFF"));
            switch(phase){
                case 0:
                    gradientDrawable1.setColor(Color.parseColor("#595959"));
                    break;
                case 1:
                    gradientDrawable1.setColor(Color.parseColor("#FFC627"));
                    break;
                case 2:
                    gradientDrawable1.setColor(Color.parseColor("#D8232A"));
                    break;
            }
            indicator.setBackground(gradientDrawable1);
            help.setBackground(gradientDrawable2);
            title.setText(description);
            date.setText(Util.getDateFromNextAlarm(locale, nextAlarm));
                 /*+ "\n"
                    + "next_alarm " + nextAlarm + "\n"
                    + "next_caution " + nextCaution + "\n"
                    + "id " + id + "\n"
                    + "pos " + pos + "\n"
                    + "phase " + phase + "\n");*/
            title.setTypeface(Util.getTypeface(context), Typeface.NORMAL);
            date.setTypeface(Util.getTypeface(context), Typeface.NORMAL);
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    mainActivity.onItemLongClicked(id, pos);
                    return true;
                }
            });
        }
    }
}
