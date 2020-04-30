package com.vpe_soft.intime.intime.view;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AlertDialog;

import com.vpe_soft.intime.intime.R;

import java.util.ArrayList;

public class ManageDialogView {

    private Context context;
    private Actions actions;

    public ManageDialogView(Context context, Actions actions){
        this.context = context;
        this.actions = actions;
    }

    public void show(){
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        ArrayList<String> options = new ArrayList<>();
        options.add(context.getString(R.string.context_menu_acknowledge));
        options.add(context.getString(R.string.context_menu_edit));
        options.add(context.getString(R.string.context_menu_delete));
        dialog.setAdapter(new ArrayAdapter(context, R.layout.dialog_item, R.id.text, options),
                new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dia, int pos) {
                switch (pos) {
                    case 0:
                        actions.acknowledge();
                        break;
                    case 1:
                        actions.edit();
                        break;
                    case 2:
                        actions.delete();
                        break;
                }
            }
        });
        dialog.create().show();
    }
    public interface Actions {
        void acknowledge();
        void edit();
        void delete();
    }
}
