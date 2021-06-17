package com.vpe_soft.intime.intime.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.vpe_soft.intime.intime.kotlin.Taggable
import com.vpe_soft.intime.intime.kotlin.log
//TODO: OBSOLETE
/**
 * Created by Valentin on 19.08.2015.
 */
class InTimeOpenHelper(context: Context) : SQLiteOpenHelper(context, "main", null, 4), Taggable {
    override val tag = "InTimeOpenHelper"
    
    override fun onCreate(db: SQLiteDatabase) {
        log("onCreate")
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
        log("onUpgrade")
        if (oldVersion < 2) {
            log("onUpgrade: up to version 2")
            val sqlCommand =
                "ALTER TABLE main.tasks ADD COLUMN next_alarm INTEGER NOT NULL DEFAULT 0;"
            db.execSQL(sqlCommand)
        }
        if (oldVersion < 3) {
            log("onUpgrade: up to version 3")
            val sqlCommand =
                "ALTER TABLE main.tasks ADD COLUMN next_caution INTEGER NOT NULL DEFAULT 0;"
            db.execSQL(sqlCommand)
        }
        if (oldVersion < 4) {
            log("onUpgrade: up to version 4")
            val sqlCommand =
                "ALTER TABLE main.tasks ADD COLUMN last_ack INTEGER NOT NULL DEFAULT 0;"
            db.execSQL(sqlCommand)
        }
    }
}