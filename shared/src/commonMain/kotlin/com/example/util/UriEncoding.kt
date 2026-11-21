package com.example.util

/**
 * Percent-encoding exactly like Android's `android.net.Uri.encode(s)`: ASCII letters and digits and `_-!.~'()*` stay,
 * every other UTF-8 byte becomes `%XX` (upper-case hex; space is `%20`). Pinned to `Uri.encode` by `UriEncodingTest`.
 */
fun uriEncode(text: String): String {
    val hex = "0123456789ABCDEF"
    val allowed = "_-!.~'()*"
    val out = StringBuilder(text.length + 16)
    for (b in text.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if ((ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || (c < 0x80 && ch in allowed)) out.append(ch)
        else out.append('%').append(hex[c shr 4]).append(hex[c and 0x0F])
    }
    return out.toString()
}
