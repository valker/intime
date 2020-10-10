package com.vpe_soft.intime.intime.kotlin

import android.util.Log
import com.vpe_soft.intime.intime.BuildConfig.DEBUG

//basic usage: log("a", "b", "c", tag = "tag")
fun log(vararg value: String, tag: String = "") {
    if (DEBUG) Log.d(tag, value.toString())
}