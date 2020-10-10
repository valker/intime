package com.vpe_soft.intime.intime.recyclerview

import android.database.Cursor
import android.view.View
import androidx.recyclerview.widget.RecyclerView

abstract class RecyclerViewCursorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    abstract fun bindCursor(cursor: Cursor)
}