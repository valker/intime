@file:Suppress("MemberVisibilityCanBePrivate")

package com.vpe_soft.intime.intime.recyclerview

import android.content.Context
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.vpe_soft.intime.intime.R
import com.vpe_soft.intime.intime.activity.cardIndicatorAlmost
import com.vpe_soft.intime.intime.activity.cardIndicatorNeutral
import com.vpe_soft.intime.intime.activity.cardIndicatorReady
import com.vpe_soft.intime.intime.activity.indicatorCornerRadius
import com.vpe_soft.intime.intime.kotlin.*
import com.vpe_soft.intime.intime.receiver.getDateFromNextAlarm

class TaskRecyclerViewAdapter(context: Context) :
    RecyclerViewCursorAdapter<TaskRecyclerViewAdapter.TaskRecyclerViewVH>(context), Taggable {

    override val tag = "TaskRecyclerViewAdapter"

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): TaskRecyclerViewVH =
        TaskRecyclerViewVH(
            LayoutInflater.from(context).inflate(R.layout.task_item, viewGroup, false)
        )


    override fun onBindViewHolder(holder: TaskRecyclerViewVH, position: Int) {
        cursorAdapter.cursor.moveToPosition(position)
        setViewHolder(holder)
        cursorAdapter.bindView(null, context, cursorAdapter.cursor)
    }

    inner class TaskRecyclerViewVH(itemView: View) : RecyclerViewCursorViewHolder(itemView), Taggable {
        val title: TextView = itemView.findViewById(R.id.title)
        val date: TextView = itemView.findViewById(R.id.description)
        val card: CardView = itemView.findViewById(R.id.card)
        val indicator: LinearLayout = itemView.findViewById(R.id.indicator)
        val help: LinearLayout = itemView.findViewById(R.id.help)
        override val tag = "TaskRecyclerViewVH"

        override fun bindCursor(cursor: Cursor) {
            log("bindCursor")
            val description = cursor.getString(cursor.getColumnIndexOrThrow("description"))
            val nextAlarm = cursor.getLong(cursor.getColumnIndexOrThrow("next_alarm"))
            val nextCaution = cursor.getLong(cursor.getColumnIndexOrThrow("next_caution"))
            val id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
            val pos = cursor.position
            // 0 - not ready (white), 1 - almost (yellow), 2 - ready (red)
            val millis = System.currentTimeMillis()
            val phase = if (millis > nextCaution) if (millis > nextAlarm) 2 else 1 else 0
            with(card) {
                radius = 0f
                setCardBackgroundColor(Color.WHITE)
                cardElevation = 0f
                outlineProvider = null
            }
            with(mainActivity) {
                if (!isDefaultViewOutlineProviderSet) {
                    isDefaultViewOutlineProviderSet = true
                    setDefaultViewOutlineProvider(card.outlineProvider)
                }
            }
            val gradientDrawable1 = GradientDrawable()
            gradientDrawable1.cornerRadius = indicatorCornerRadius
            val gradientDrawable2 = GradientDrawable()
            with(gradientDrawable2) {
                cornerRadius = indicatorCornerRadius
                setColor(Color.WHITE)
            }
            when (phase) {
                0 -> gradientDrawable1.setColor(context.cardIndicatorNeutral)
                1 -> gradientDrawable1.setColor(context.cardIndicatorAlmost)
                2 -> gradientDrawable1.setColor(context.cardIndicatorReady)
            }
            indicator.background = gradientDrawable1
            help.background = gradientDrawable2
            title.text = description
            date.text = getDateFromNextAlarm(context.locale, nextAlarm)
            card.longClickListener = {
                mainActivity.onItemLongClicked(id, pos)
            }
        }
    }

    init {
        setupCursorAdapter(context.cursor(), R.layout.task_item)
    }
}
