package com.vpe_soft.intime.intime.view

import android.content.Context
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.vpe_soft.intime.intime.R
//TODO: OBSOLETE
fun Context.showDialog(
    acknowledge: () -> Unit = {},
    edit: () -> Unit = {},
    delete: () -> Unit = {}
) {
    val dialog = AlertDialog.Builder(this)
    val options = listOf(
        getString(R.string.context_menu_acknowledge),
        getString(R.string.context_menu_edit),
        getString(R.string.context_menu_delete)
    )
    dialog.setAdapter(
        ArrayAdapter(
            this,
            R.layout.dialog_item,
            R.id.text,
            options
        )
    ) { _, pos ->
        when (pos) {
            0 -> acknowledge()
            1 -> edit()
            2 -> delete()
        }
    }
    dialog.create().show()
}