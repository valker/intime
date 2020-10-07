package com.vpe_soft.intime.intime.view;

import android.content.res.Resources;
import android.util.TypedValue;

public class ViewUtil {

    public static float toPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics());
    }
}
