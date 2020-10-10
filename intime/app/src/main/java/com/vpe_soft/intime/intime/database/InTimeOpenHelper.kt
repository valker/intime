package com.vpe_soft.intime.intime.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * Created by Valentin on 19.08.2015.
 */
class InTimeOpenHelper(context: Context) : SQLiteOpenHelper(context, "main", null, 4) {
    private val tag = "InTimeOpenHelper"
    override fun onCreate(db: SQLiteDatabase) {
        Log.d(tag, "onCreate")
        db.execSQL(
            "CREATE TABLE main.tasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL UNIQUE" +  // unique id of the task
                    ", description TEXT NOT NULL" +  // description for user
                    ", interval INTEGER NOT NULL" +  // type of interval : minutes/hours/etc...
                    ", amount INTEGER NOT NULL" +  // amount of interval to next alarm
                    ", next_alarm INTEGER NOT NULL DEFAULT 0" +  // next alarm timestamp
                    ", next_caution INTEGER NOT NULL DEFAULT 0" +  // next caution timestamp
                    ", last_ack INTEGER NOT NULL DEFAULT 0" +  // last acknowledge timestamp
                    ")"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(tag, "onUpgrade")
        if (oldVersion < 2) {
            Log.d(tag, "onUpgrade: up to version 2")
            val sqlCommand =
                "ALTER TABLE main.tasks ADD COLUMN next_alarm INTEGER NOT NULL DEFAULT 0;"
            db.execSQL(sqlCommand)
        }
        if (oldVersion < 3) {
            Log.d(tag, "onUpgrade: up to version 3")
            val sqlCommand =
                "ALTER TABLE main.tasks ADD COLUMN next_caution INTEGER NOT NULL DEFAULT 0;"
            db.execSQL(sqlCommand)
        }
        if (oldVersion < 4) {
            Log.d(tag, "onUpgrade: up to version 4")
            val sqlCommand =
                "ALTER TABLE main.tasks ADD COLUMN last_ack INTEGER NOT NULL DEFAULT 0;"
            db.execSQL(sqlCommand)
        }
    }
}