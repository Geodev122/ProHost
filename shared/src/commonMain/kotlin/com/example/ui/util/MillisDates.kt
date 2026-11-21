package com.example.ui.util

/**
 * [millis] (an instant) as a local calendar date with a `SimpleDateFormat` [pattern] in [languageTag]
 * (null = the device's default locale). Android: `SimpleDateFormat(pattern, locale).format(Date(millis))`, exactly the call the
 * Premium page made; iOS/web: an English rendering of the patterns the app uses ("MMM d, yyyy").
 */
expect fun formatMillisDate(millis: Long, pattern: String, languageTag: String? = null): String
