package com.example.ui.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

actual fun formatMillisDate(millis: Long, pattern: String, languageTag: String?): String {
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return pattern
        .replace("yyyy", d.year.toString())
        .replace("MMM", MONTHS[d.monthNumber - 1])
        .replace("d", d.dayOfMonth.toString())
}
