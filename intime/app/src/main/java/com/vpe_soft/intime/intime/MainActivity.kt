package com.vpe_soft.intime.intime

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
import com.vpe_soft.intime.intime.database.*
import com.vpe_soft.intime.intime.kotlin.*
import com.vpe_soft.intime.intime.utility.Taggable
import com.vpe_soft.intime.intime.utility.log

class MainActivity : AppCompatActivity(), Taggable {
    override val tag = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {

        }
    }

    override fun onCreateOptionsMenu(menu: Menu) = true.also {
        menuInflater.inflate(R.menu.menu_main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_add_task -> {
            //task editting
            true
        }
        R.id.action_settings -> {
            //settings
            true
        }
        else -> super.onOptionsItemSelected(item)
    }


    override fun onStart() {
        log("onStart")
        super.onStart()
    }

    override fun onStop() {
        log("onStop")
        super.onStop()
    }

    override fun onPause() {
        log("onPause")
        isOnScreen = false
        with(getSharedPreferences("SessionInfo", MODE_PRIVATE).edit()) {
            putLong("LastUsageTimestamp", millis())
            apply()
        }
        super.onPause()
    }
    override fun onResume() {
        log("onResume")
        isOnScreen = true
        super.onResume()
    }
}
