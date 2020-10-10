package com.vpe_soft.intime.intime.recyclerview

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cursoradapter.widget.CursorAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vpe_soft.intime.intime.activity.MainActivity
import java.util.*

abstract class RecyclerViewCursorAdapter<T : RecyclerViewCursorViewHolder> protected constructor(
    protected val context: Context
) :
    RecyclerView.Adapter<T>() {
    protected var mainActivity: MainActivity = context as MainActivity
    protected lateinit var cursorAdapter: CursorAdapter
    private lateinit var viewHolder: T
    protected fun setupCursorAdapter(
        cursor: Cursor,
        @Suppress("SameParameterValue") resource: Int
    ) {
        cursorAdapter = object : CursorAdapter(context, cursor, 0) {
            override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View =
                LayoutInflater.from(context).inflate(resource, parent, false)

            override fun bindView(view: View?, context: Context, cursor: Cursor) =
                viewHolder.bindCursor(cursor)
        }
    }

    override fun getItemCount(): Int = cursorAdapter.count

    fun swapCursor(cursor: Cursor) {
        cursorAdapter.swapCursor(cursor)
        notifyDataSetChanged()
    }

    protected fun setViewHolder(newViewHolder: T) {
        viewHolder = newViewHolder
    }
}