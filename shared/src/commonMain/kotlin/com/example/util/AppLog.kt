package com.example.util

import kotlin.concurrent.Volatile

/**
 * Logging for shared code. Android routes it to `android.util.Log` (`AndroidAppLog.install` in `ProHostApplication`);
 * elsewhere (iOS, JVM unit tests) it prints. Never log tokens or personal data (NIGHTHAWK "Secrets & Token Hygiene").
 */
object AppLog {
    /** level is 'd', 'i', 'w' or 'e'. */
    @Volatile
    var sink: (level: Char, tag: String, message: String, error: Throwable?) -> Unit = { level, tag, message, error ->
        println("${level.uppercaseChar()}/$tag: $message" + (error?.let { " ($it)" } ?: ""))
    }

    fun d(tag: String, message: String) = sink('d', tag, message, null)
    fun i(tag: String, message: String) = sink('i', tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = sink('w', tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = sink('e', tag, message, error)
}
