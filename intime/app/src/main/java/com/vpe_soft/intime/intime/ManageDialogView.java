package com.vpe_soft.intime.intime;

import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.graphics.Typeface;
import android.widget.ArrayAdapter;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;

public class ManageDialogView {

    private Context context;

    public ManageDialogView(Context context){
        this.context = context;
    }
    public void show(){
        AlertDialog.Builder dialog =new AlertDialog.Builder(context);
        ArrayList<String> list1 = new ArrayList<String>();
        list1.add("Maximum");
        list1.add("Medium");
        list1.add("Minimum");
        /*dialog.setAdapter(new ArrayAdapter(this, R.layout.item_layout, R.id.editableText, list1), new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dia, int pos) {

            }
        });*/
        dialog.create().show();
    }
    private class TextViewWithFont extends AppCompatTextView {
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
            setTypeface(Typeface.createFromAsset(context.getAssets(), "font/font.ttf"),Typeface.BOLD);
        }
    }

}
