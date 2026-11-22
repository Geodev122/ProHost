package com.example.ios

import com.example.util.KeyValueStore
import platform.Foundation.NSUserDefaults

/** [KeyValueStore] over NSUserDefaults (Android: SharedPreferences); keys are "<store>.<key>" so stores never collide. */
class IosKeyValueStore(private val name: String) : KeyValueStore {
    private val defaults get() = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey("$name.$key")

    override fun getLong(key: String, default: Long): Long =
        if (defaults.objectForKey("$name.$key") == null) default else defaults.integerForKey("$name.$key")

    override fun put(strings: Map<String, String>, longs: Map<String, Long>) {
        strings.forEach { (k, v) -> defaults.setObject(v, forKey = "$name.$k") }
        longs.forEach { (k, v) -> defaults.setInteger(v, forKey = "$name.$k") }
    }
}
