package com.example.util

import kotlin.concurrent.Volatile

/** A small named key-value file (Android SharedPreferences, iOS NSUserDefaults). */
interface KeyValueStore {
    fun getString(key: String): String?
    fun getLong(key: String, default: Long): Long
    /** Writes all values in one go (asynchronously on Android, like SharedPreferences.apply()). */
    fun put(strings: Map<String, String> = emptyMap(), longs: Map<String, Long> = emptyMap())
}

object KeyValueStores {
    private val memory = mutableMapOf<String, KeyValueStore>()

    /** Installed by the platform at start; until then (and in plain unit tests) stores live in memory. */
    @Volatile
    var open: (name: String) -> KeyValueStore = { name -> memory.getOrPut(name) { InMemoryKeyValueStore() } }
}

class InMemoryKeyValueStore : KeyValueStore {
    private val strings = mutableMapOf<String, String>()
    private val longs = mutableMapOf<String, Long>()
    override fun getString(key: String): String? = strings[key]
    override fun getLong(key: String, default: Long): Long = longs[key] ?: default
    override fun put(strings: Map<String, String>, longs: Map<String, Long>) {
        this.strings.putAll(strings); this.longs.putAll(longs)
    }
}
