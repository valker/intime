package com.vpe_soft.intime.intime.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.vpe_soft.intime.intime.R
import com.vpe_soft.intime.intime.kotlin.Taggable
import com.vpe_soft.intime.intime.kotlin.log
import com.vpe_soft.intime.intime.kotlin.clickListener
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity(), Taggable {
    override val tag = "SettingsActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        actionExport.clickListener = {
            log("Export")
            //TODO: action - export
        }
        actionImport.clickListener = {
            log("Import")
            //TODO: action - import
        }
    }
}
