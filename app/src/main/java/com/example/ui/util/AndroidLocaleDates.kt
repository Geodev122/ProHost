package com.example.ui.util

import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Android's [LocaleDates]: exactly the java.time calls the date picker made before it moved to :shared. */
object AndroidLocaleDates {
    fun install() {
        LocaleDates.format = { d, pattern, tag ->
            java.time.LocalDate.of(d.year, d.monthNumber, d.dayOfMonth).format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag(tag)))
        }
        LocaleDates.weekdayShort = { dow, tag -> DayOfWeek.of(dow).getDisplayName(TextStyle.SHORT, Locale.forLanguageTag(tag)) }
    }
}
