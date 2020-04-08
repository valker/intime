package com.vpe_soft.intime.intime.recyclerview;

import android.database.Cursor;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

public abstract class RecyclerViewCursorViewHolder extends RecyclerView.ViewHolder {

    public RecyclerViewCursorViewHolder(View view) { super(view); }

    public abstract void bindCursor(Cursor cursor);
}
