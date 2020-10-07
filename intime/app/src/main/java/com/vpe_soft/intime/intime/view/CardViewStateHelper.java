package com.vpe_soft.intime.intime.view;

import android.view.ViewOutlineProvider;

import com.vpe_soft.intime.intime.activity.Dimensions;
import com.vpe_soft.intime.intime.recyclerview.TaskRecyclerViewAdapter;

public class CardViewStateHelper {

    private ViewOutlineProvider defaultProvider;

    private boolean isIdle = true;

    public void setDefaultProvider(ViewOutlineProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public void setDefaultState(TaskRecyclerViewAdapter.TaskRecyclerViewVH holder) {
        if (!isIdle) {
            isIdle = true;
            holder.card.setRadius(0);
            holder.card.setOutlineProvider(null);
        }
    }

    public void setOnSwipeState(TaskRecyclerViewAdapter.TaskRecyclerViewVH holder) {
        if (isIdle) {
            isIdle = false;
            holder.card.setRadius(Dimensions.cardCornerRadius);
            holder.card.setOutlineProvider(defaultProvider);
        }
    }
}
