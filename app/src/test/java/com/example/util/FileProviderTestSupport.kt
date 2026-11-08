package com.example.util

/**
 * `androidx.core.content.FileProvider` caches its path strategy per authority in a static map. Under Robolectric every test gets its own temporary
 * directories, so a strategy cached by an earlier test (e.g. the camera capture test) no longer contains the files of a later one and
 * `getUriForFile` throws "Failed to find configured root". Tests that use FileProvider call [reset] first.
 */
object FileProviderTestSupport {
    fun reset() {
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        cache.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (cache.get(null) as MutableMap<String, Any?>).clear()
    }
}
