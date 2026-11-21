package com.example.ui.map

/** ARGB of a colour string, or null when it can't be read. Android: `Color.parseColor` (hex and named colours), elsewhere hex only. */
expect fun parseColorOrNull(color: String): Int?

/** "#RRGGBB" / "#AARRGGBB" as ARGB, or null. */
fun parseHexColorOrNull(color: String): Int? {
    if (!color.startsWith("#")) return null
    val hex = color.substring(1)
    val value = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> (value or 0xFF000000L).toInt()
        8 -> value.toInt()
        else -> null
    }
}

fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
fun redOf(color: Int): Int = (color shr 16) and 0xFF
fun greenOf(color: Int): Int = (color shr 8) and 0xFF
fun blueOf(color: Int): Int = color and 0xFF
