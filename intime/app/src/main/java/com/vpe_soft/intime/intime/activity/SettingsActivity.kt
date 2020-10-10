package com.vpe_soft.intime.intime.activity

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.vpe_soft.intime.intime.R
import com.vpe_soft.intime.intime.kotlin.clickListener
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity() {
    private val tag = "SettingsActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        actionExport.clickListener = {
            Log.d(tag, "Export")
            //TODO: action - export
        }
        actionImport.clickListener = {
            Log.d(tag, "Import")
            //TODO: action - import
        }
    }
}