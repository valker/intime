package com.vpe_soft.intime.intime.view

import android.content.Context
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.vpe_soft.intime.intime.R
import com.vpe_soft.intime.intime.activity.snackbarActionColor
import com.vpe_soft.intime.intime.activity.snackbarBackgroundColor

fun Context.showOnDeleted(view: View, onCancelled: () -> Unit) {
    showSnackbar(
        this,
        getString(R.string.task_deleted),
        getString(R.string.cancel),
        view,
        onCancelled
    )
}

fun Context.showOnAcknowledged(view: View, onCancelled: () -> Unit) =
    showSnackbar(
        this,
        getString(R.string.task_acknowledged),
        getString(R.string.cancel),
        view,
        onCancelled
    )

private fun showSnackbar(
    context: Context,
    message: String,
    action: String,
    view: View,
    onCancelled: () -> Unit
) = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
    .setAction(action) { onCancelled() }
    .setActionTextColor(context.snackbarActionColor)
    .setBackgroundTint(context.snackbarBackgroundColor)
    .show()