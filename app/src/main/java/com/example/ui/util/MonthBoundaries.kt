package com.example.ui.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Instant
import kotlinx.datetime.atTime

/**
 * The month boundaries (epoch millis, local midnight of the 1st etc.) the Manage page and Owner Analytics compare booking times against,
 * computed without `java.util.Calendar`. Results equal the old Calendar chains in every JDK time zone (`MonthBoundariesEquivalenceTest`),
 * including the old behaviour that "the 1st of last month" inherits the time of day of "the same day last month" (01:00 when that midnight fell
 * into a clock-change gap).
 */
object MonthBoundaries {
    /** Manage page: local midnight of the 1st of this month and of last month. */
    fun manage(nowMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Pair<Long, Long> {
        val firstThis = IsoDate.firstOfMonth(IsoDate.localDateOf(nowMillis, zone))
        return IsoDate.startOfLocalDayMillis(firstThis, zone) to IsoDate.startOfLocalDayMillis(IsoDate.plusMonths(firstThis, -1), zone)
    }

    data class Analytics(val currentMonthStart: Long, val prevMonthSameDay: Long, val prevMonthStart: Long)

    /** Owner Analytics: this month's start, the same day last month (midnight, day clamped to the month length) and last month's start. */
    fun analytics(nowMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Analytics {
        val today = IsoDate.localDateOf(nowMillis, zone)
        val prevDay = IsoDate.plusMonths(today, -1)
        val prevSame = IsoDate.startOfLocalDayMillis(prevDay, zone)
        val carried = Instant.fromEpochMilliseconds(prevSame).toLocalDateTime(zone).time
        return Analytics(
            currentMonthStart = IsoDate.startOfLocalDayMillis(IsoDate.firstOfMonth(today), zone),
            prevMonthSameDay = prevSame,
            prevMonthStart = IsoDate.localMillis(IsoDate.firstOfMonth(prevDay).atTime(carried), zone)
        )
    }
}
