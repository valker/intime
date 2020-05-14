package com.vpe_soft.intime.intime.util;

import android.view.ViewOutlineProvider;

import com.vpe_soft.intime.intime.recyclerview.TaskRecyclerViewAdapter;

public class CardViewOutlineHelper {

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
            holder.card.setRadius(Util.getCardCornerRadius());
            holder.card.setOutlineProvider(defaultProvider);
        }
    }
}
