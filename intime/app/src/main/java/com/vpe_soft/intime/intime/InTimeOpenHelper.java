package com.vpe_soft.intime.intime;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by Valentin on 19.08.2015.
 */
public class InTimeOpenHelper extends SQLiteOpenHelper {

    private String KEY_DEFINITION = "definition";
    private String DATABASE_NAME = "com_vpesoft_intime";
    private final String DATABASE_VERSION = "1";

    public InTimeOpenHelper(Context context) {
        super(context, "main", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE main.tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL UNIQUE" +
                ", description TEXT NOT NULL" +
                ", interval INTEGER NOT NULL" +
                ", amount INTEGER NOT NULL" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}
