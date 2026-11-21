package com.example.util

/**
 * application/x-www-form-urlencoded, byte for byte what java.net.URLEncoder.encode(s, "UTF-8") produces:
 * letters, digits and `.-*_` stay, space becomes `+`, every other UTF-8 byte becomes `%XX` (upper-case hex).
 */
fun formUrlEncode(text: String): String {
    val hex = "0123456789ABCDEF"
    val out = StringBuilder(text.length + 16)
    for (b in text.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        when {
            c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
                c == '.'.code || c == '-'.code || c == '*'.code || c == '_'.code -> out.append(c.toChar())
            c == ' '.code -> out.append('+')
            else -> out.append('%').append(hex[c shr 4]).append(hex[c and 0xF])
        }
    }
    return out.toString()
}
