package com.example.util

/**
 * A file the person picked or took (profile photo, signed agreement, verification document), as an opaque handle that only the platform layer can
 * open: on Android it is the `content://` (or `https://`) URI string. View models, repositories and shared code pass [PickedFile] around and never
 * touch `Uri` / `ContentResolver`; [FileAccess] and the storage gateway resolve it.
 */
data class PickedFile(val handle: String)

/** What shared code needs to know about a picked file. One implementation per platform (Android below; iOS later). */
interface FileAccess {
    /** Best-effort file extension without the dot (`pdf`, `jpeg`, ...): the content type first, then the name, then [fallback]. */
    fun extension(file: PickedFile, fallback: String): String

    companion object {
        /** Registered once by `ProHostApplication`; tests set their own. */
        @Volatile
        var current: FileAccess? = null

        fun require(): FileAccess = current ?: error("FileAccess is not initialised (ProHostApplication.onCreate)")
    }
}
