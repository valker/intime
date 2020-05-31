package com.vpe_soft.intime.intime.activity;

import android.content.Context;

import static  androidx.core.content.ContextCompat.getColor;

import com.vpe_soft.intime.intime.R;

public class Colors {

    public int activityBackground;
    public int contextMenuItem;
    public int titleText;
    public int cardBackground;
    public int cardTitleText;
    public int cardDateText;
    public int cardIndicatorReady;
    public int cardIndicatorAlmost;
    public int cardIndicatorNeutral;
    public int cardSwipeBackground;
    public int staticText;
    public int editTextErrorHint;
    public int editTextErrorTint;
    public int editTextHint;
    public int editTextTint;
    public int spinnerItemColor;
    public int snackbarActionColor;
    public int snackbarBackgroundColor;

    public Colors(Context context) {
        activityBackground = getColor(context, R.color.activityBackground);
        contextMenuItem = getColor(context, R.color.contextMenuItem);
        titleText = getColor(context, R.color.titleText);
        cardBackground = getColor(context, R.color.cardBackground);
        cardTitleText = getColor(context, R.color.cardTitleText);
        cardDateText = getColor(context, R.color.cardDateText);
        cardIndicatorReady = getColor(context, R.color.cardIndicatorReady);
        cardIndicatorAlmost = getColor(context, R.color.cardIndicatorAlmost);
        cardIndicatorNeutral = getColor(context, R.color.cardIndicatorNeutral);
        cardSwipeBackground = getColor(context, R.color.cardSwipeBackground);
        staticText = getColor(context, R.color.staticText);
        editTextErrorHint = getColor(context, R.color.editTextErrorHint);
        editTextErrorTint = getColor(context, R.color.editTextErrorTint);
        editTextHint = getColor(context, R.color.editTextHint);
        editTextTint = getColor(context, R.color.editTextTint);
        spinnerItemColor = getColor(context, R.color.spinnerItemColor);
        snackbarActionColor = getColor(context, R.color.snackbarActionColor);
        snackbarBackgroundColor = getColor(context, R.color.snackbarBackgroundColor);
    }
}