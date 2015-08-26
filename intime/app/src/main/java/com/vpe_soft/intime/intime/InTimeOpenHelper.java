package com.vpe_soft.intime.intime;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by Valentin on 19.08.2015.
 */
public class InTimeOpenHelper extends SQLiteOpenHelper {

    public InTimeOpenHelper(Context context) {
        super(context, "main", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE main.tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL UNIQUE" +    // unique id of the task
                ", description TEXT NOT NULL" +                             // description for user
                ", interval INTEGER NOT NULL" +                             // type of interval : minutes/hours/etc...
                ", amount INTEGER NOT NULL" +                               // amount of interval to next alarm
                ", next_alarm INTEGER NOT NULL DEFAULT 0" +                           // next alarm timestamp
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion < 2) {
            String sqlCommand = "ALTER TABLE main.tasks ADD COLUMN next_alarm INTEGER NOT NULL DEFAULT 0;";
            db.execSQL(sqlCommand);
        }
    }
}
