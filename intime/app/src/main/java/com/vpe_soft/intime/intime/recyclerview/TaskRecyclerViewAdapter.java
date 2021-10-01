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

import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.activity.Dimensions;
import com.vpe_soft.intime.intime.database.DatabaseUtil;
import com.vpe_soft.intime.intime.receiver.AlarmUtil;
import com.vpe_soft.intime.intime.view.ViewUtil;

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
        private final String TAG = "TaskRecyclerViewVH";

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
            String description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseUtil.DESCRIPTION_FIELD));
            long nextAlarm = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseUtil.NEXT_ALARM_FIELD));
            long nextCaution = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseUtil.NEXT_CAUTION_FIELD));
            final long id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            final int pos = cursor.getPosition();
            // 0 - not ready (white), 1 - almost (yellow), 2 - ready (red)
            int phase = System.currentTimeMillis() > nextCaution ? System.currentTimeMillis() > nextAlarm ? 2 : 1 : 0;
            card.setRadius(0);
            card.setCardBackgroundColor(Color.WHITE);
            card.setCardElevation(0);
            if (!mainActivity.isDefaultViewOutlineProviderSet) {
                mainActivity.isDefaultViewOutlineProviderSet = true;
                mainActivity.setDefaultViewOutlineProvider(card.getOutlineProvider());
            }
            card.setOutlineProvider(null);
            GradientDrawable gradientDrawable1 = new GradientDrawable();
            gradientDrawable1.setCornerRadius(Dimensions.indicatorCornerRadius);
            GradientDrawable gradientDrawable2 = new GradientDrawable();
            gradientDrawable2.setCornerRadius(Dimensions.indicatorCornerRadius);
            gradientDrawable2.setColor(Color.WHITE);
            switch(phase){
                case 0:
                    gradientDrawable1.setColor(colors.cardIndicatorNeutral);
                    break;
                case 1:
                    gradientDrawable1.setColor(colors.cardIndicatorAlmost);
                    break;
                case 2:
                    gradientDrawable1.setColor(colors.cardIndicatorReady);
                    break;
            }
            indicator.setBackground(gradientDrawable1);
            help.setBackground(gradientDrawable2);
            title.setText(description);
            date.setText(AlarmUtil.getDateFromNextAlarm(locale, nextAlarm));
                 /*+ "\n"
                    + "next_alarm " + nextAlarm + "\n"
                    + "next_caution " + nextCaution + "\n"
                    + "id " + id + "\n"
                    + "pos " + pos + "\n"
                    + "phase " + phase + "\n");*/
            title.setTypeface(ViewUtil.getTypeface(context), Typeface.NORMAL);
            date.setTypeface(ViewUtil.getTypeface(context), Typeface.NORMAL);
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
