package com.example.util

actual fun formatFixed(value: Double, decimals: Int, grouping: Boolean): String = formatFixedPortable(value, decimals, grouping)

actual fun formatTwoDigits(pattern: String, value: Int): String = formatTwoDigitsPortable(pattern, value)
