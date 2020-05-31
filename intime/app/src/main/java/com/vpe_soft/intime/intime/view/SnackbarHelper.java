package com.vpe_soft.intime.intime.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.material.snackbar.Snackbar;
import com.vpe_soft.intime.intime.R;
import com.vpe_soft.intime.intime.activity.Colors;
import com.vpe_soft.intime.intime.activity.Dimensions;

public class SnackbarHelper {
    public static void showOnDeleted(Context context, View view, final Listener listener) {
        showSnackbar(new Colors(context), context.getString(R.string.task_deleted), context.getString(R.string.cancel), view, listener);

    }
    public static void showOnAcknowledged(Context context, View view, Listener listener) {
        showSnackbar(new Colors(context), context.getString(R.string.task_acknowledged), context.getString(R.string.cancel), view, listener);
    }
    private static void showSnackbar(Colors colors, String message, String action, View view, final Listener listener) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                .setAction(action, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onCancelled();
                    }
                })
                .setActionTextColor(colors.snackbarActionColor)
                .setBackgroundTint(colors.snackbarBackgroundColor);
        snackbar.show();
    }
    public interface Listener {
        void onCancelled();
    }
}