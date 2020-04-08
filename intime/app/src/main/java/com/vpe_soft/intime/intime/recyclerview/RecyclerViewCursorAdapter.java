package com.vpe_soft.intime.intime.recyclerview;

import android.content.Context;
import android.database.Cursor;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Locale;

public abstract class RecyclerViewCursorAdapter<T extends RecyclerViewCursorViewHolder> extends RecyclerView.Adapter<T> {

    protected final Context context;
    protected final Locale locale;
    protected CursorAdapter cursorAdapter;
    private T viewHolder;
    protected RecyclerViewCursorAdapter(Context context, Locale locale) {
        this.context = context;
        this.locale = locale;
    }

    protected void setupCursorAdapter(Cursor cursor, int flags, final int resource, final boolean attachToRoot) {
        this.cursorAdapter = new CursorAdapter(context, cursor, flags) {
            @Override
            public View newView(Context context, Cursor cursor, ViewGroup parent) {
                return LayoutInflater.from(context).inflate(resource, parent, attachToRoot);
            }

            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                viewHolder.bindCursor(cursor);
            }
        };
    }

    @Override
    public int getItemCount() {
        return cursorAdapter.getCount();
    }

    public void swapCursor(Cursor cursor) {
        this.cursorAdapter.swapCursor(cursor);
        notifyDataSetChanged();
    }

    protected void setViewHolder(T viewHolder) {
        this.viewHolder = viewHolder;
    }
}