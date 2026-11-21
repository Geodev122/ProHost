package com.example.ui.util

actual fun formatMillisDate(millis: Long, pattern: String, languageTag: String?): String =
    java.text.SimpleDateFormat(pattern, languageTag?.let { java.util.Locale.forLanguageTag(it) } ?: java.util.Locale.getDefault())
        .format(java.util.Date(millis))
