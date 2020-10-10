package com.vpe_soft.intime.intime.view

import android.view.ViewOutlineProvider
import com.vpe_soft.intime.intime.activity.cardCornerRadius
import com.vpe_soft.intime.intime.recyclerview.TaskRecyclerViewAdapter.TaskRecyclerViewVH

//todo: rewrite this
class CardViewStateHelper {
    var defaultProvider: ViewOutlineProvider? = null
    private var isIdle = true

    fun setDefaultState(holder: TaskRecyclerViewVH) {
        if (!isIdle) {
            isIdle = true
            with(holder.card) {
                radius = 0f
                outlineProvider = null
            }
        }
    }

    fun setOnSwipeState(holder: TaskRecyclerViewVH) {
        if (isIdle) {
            isIdle = false
            with(holder.card) {
                radius = cardCornerRadius
                outlineProvider = defaultProvider
            }
        }
    }
}