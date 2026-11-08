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

    /** The file's bytes, or null when it cannot be read (as the inline `runCatching { openInputStream(...).readBytes() }` it replaces). */
    suspend fun readBytes(file: PickedFile): ByteArray?

    /** The file's size in bytes when the provider reports one, else null (as the inline `OpenableColumns.SIZE` query it replaces). */
    suspend fun sizeBytes(file: PickedFile): Long?

    companion object {
        /** Registered once by `ProHostApplication`; tests set their own. */
        @Volatile
        var current: FileAccess? = null

        fun require(): FileAccess = current ?: error("FileAccess is not initialised (ProHostApplication.onCreate)")
    }
}

/** Saves a nullable [PickedFile] across configuration changes and process death (as its handle string; empty = none). */
val PickedFileSaver: androidx.compose.runtime.saveable.Saver<PickedFile?, String> = androidx.compose.runtime.saveable.Saver(
    save = { it?.handle ?: "" },
    restore = { handle -> handle.takeIf { it.isNotEmpty() }?.let(::PickedFile) }
)
