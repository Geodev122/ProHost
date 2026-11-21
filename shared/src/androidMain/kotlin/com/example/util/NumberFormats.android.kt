package com.example.util

import java.util.Locale

actual fun formatFixed(value: Double, decimals: Int, grouping: Boolean): String =
    String.format(Locale.US, if (grouping) "%,.${decimals}f" else "%.${decimals}f", value)

actual fun formatTwoDigits(pattern: String, value: Int): String = pattern.format(value)
