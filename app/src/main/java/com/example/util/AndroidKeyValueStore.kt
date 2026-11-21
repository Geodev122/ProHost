package com.example.util

import android.content.Context

/** [KeyValueStore] over SharedPreferences (private mode); same files and keys the app always used. */
class AndroidKeyValueStore(context: Context, name: String) : KeyValueStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun put(strings: Map<String, String>, longs: Map<String, Long>) {
        val e = prefs.edit()
        strings.forEach { (k, v) -> e.putString(k, v) }
        longs.forEach { (k, v) -> e.putLong(k, v) }
        e.apply()
    }

    companion object {
        fun install(context: Context) {
            val app = context.applicationContext
            KeyValueStores.open = { name -> AndroidKeyValueStore(app, name) }
        }
    }
}
