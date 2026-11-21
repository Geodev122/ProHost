package com.example.ui.map

actual fun parseColorOrNull(color: String): Int? = try {
    android.graphics.Color.parseColor(color)
} catch (e: Exception) { // the old code caught every exception here too
    null
}
