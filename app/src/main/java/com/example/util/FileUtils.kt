package com.example.util

import android.content.Context
import android.net.Uri

/** Best-effort file extension for a picked content:// Uri — tries the resolver's
 * reported MIME type first (most reliable), falls back to the Uri's own path
 * segment, then [fallback]. Shared by AuthViewModel (registration uploads) and
 * ProHostViewModel (booking-agreement uploads) — previously duplicated. */
fun guessFileExtension(context: Context, uri: Uri, fallback: String): String {
    val mime = context.contentResolver.getType(uri)
    val fromMime = mime?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    if (!fromMime.isNullOrBlank()) return fromMime
    val path = uri.lastPathSegment ?: return fallback
    return path.substringAfterLast('.', fallback)
}
