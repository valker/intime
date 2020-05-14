package com.vpe_soft.intime.intime.view;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

public class TextViewWithFont extends AppCompatTextView {
    public TextViewWithFont(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public TextViewWithFont(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TextViewWithFont(Context context) {
        super(context);
        init(context);
    }
    public void init(Context context) {
        setTypeface(ViewUtil.getTypeface(context),Typeface.NORMAL);
    }
}
