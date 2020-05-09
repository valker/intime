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
            holder.card.setCardElevation(0f);
            holder.card.setOutlineProvider(null);
        }
    }
    public void setOnSwipeState(TaskRecyclerViewAdapter.TaskRecyclerViewVH holder) {
        if (isIdle) {
            isIdle = false;
            holder.card.setCardElevation(1f);
            holder.card.setOutlineProvider(defaultProvider);
        }
    }
}
