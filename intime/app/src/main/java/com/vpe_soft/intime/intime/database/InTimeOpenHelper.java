package com.vpe_soft.intime.intime.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Адаптер доступа к базе данных
 */
public class InTimeOpenHelper extends SQLiteOpenHelper {

    private static final String TAG = "InTimeOpenHelper";

    public InTimeOpenHelper(Context context) {
        super(context, "main", null, 5);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "onCreate");
        db.execSQL("CREATE TABLE main.tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL UNIQUE" +    // unique id of the task
                ", description TEXT NOT NULL" +                             // description for user
                ", interval INTEGER NOT NULL" +                             // type of interval : minutes/hours/etc...
                ", amount INTEGER NOT NULL" +                               // amount of interval to next alarm
                ", next_alarm INTEGER NOT NULL DEFAULT 0" +                 // next alarm timestamp
                ", next_caution INTEGER NOT NULL DEFAULT 0" +               // next caution timestamp
                ", last_ack INTEGER NOT NULL DEFAULT 0" +                   // last acknowledge timestamp
                ", quant INTEGER NOT NULL DEFAULT 1" +                      // divider of interval
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "onUpgrade");

        if(oldVersion < 2) {
            Log.d(TAG, "onUpgrade: up to version 2");
            String sqlCommand = "ALTER TABLE main.tasks ADD COLUMN next_alarm INTEGER NOT NULL DEFAULT 0;";
            db.execSQL(sqlCommand);
        }

        if(oldVersion < 3) {
            Log.d(TAG, "onUpgrade: up to version 3");
            String sqlCommand = "ALTER TABLE main.tasks ADD COLUMN next_caution INTEGER NOT NULL DEFAULT 0;";
            db.execSQL(sqlCommand);
        }

        if(oldVersion < 4) {
            Log.d(TAG, "onUpgrade: up to version 4");
            String sqlCommand = "ALTER TABLE main.tasks ADD COLUMN last_ack INTEGER NOT NULL DEFAULT 0;";
            db.execSQL(sqlCommand);
        }

        if(oldVersion <5) {
            Log.d(TAG, "onUpgrade: up to version 5");
            String sqlCommand = "ALTER TABLE main.tasks ADD COLUMN quant INTEGER NOT NULL DEFAULT 1;";
            db.execSQL(sqlCommand);
        }
    }
}
