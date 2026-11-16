package com.example.util

import android.util.Log

/** Android part of [AppLog]: the same `android.util.Log` calls the code made before it moved to `:shared`. */
object AndroidAppLog {
    fun install() {
        AppLog.sink = { level, tag, message, error ->
            when (level) {
                'd' -> Log.d(tag, message, error)
                'i' -> Log.i(tag, message, error)
                'w' -> Log.w(tag, message, error)
                else -> Log.e(tag, message, error)
            }
        }
    }
}
