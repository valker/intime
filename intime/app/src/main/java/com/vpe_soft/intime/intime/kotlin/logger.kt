package com.vpe_soft.intime.intime.kotlin

import android.util.Log
import com.vpe_soft.intime.intime.BuildConfig.DEBUG

//basic usage: log("a", "b", "c", tag = "tag")
fun printLog(vararg values: Any?, tag: String = "") {
    if (DEBUG) Log.d(tag, values.joinToString())
}

interface Taggable {
    val tag: String
}

fun Taggable.log(vararg values: Any?) = printLog(*values, tag)