package com.example.companion.util

import android.util.Log

object Logger {
    private const val TAG = "CartoonCompanion"
    fun i(msg: String) = Log.i(TAG, msg)
    fun e(msg: String, t: Throwable? = null) = Log.e(TAG, msg, t)
}
