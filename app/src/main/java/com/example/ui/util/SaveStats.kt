package com.example.ui.util

import com.example.data.model.SpaceListing
import java.util.Calendar
import java.util.TimeZone

/**
 * Save ("heart") numbers a Pro Host sees, from the server's counters (functions/src/users/favoritesSync.ts):
 * favoriteCount = people who have the listing saved now, favoritesByMonth = saves per UTC month ("yyyymm").
 * Unit-tested in SaveStatsTest.
 */
object SaveStats {
    /** "202610" for [ms], in UTC like the server. [monthsBack] steps back whole months. */
    fun monthKey(ms: Long, monthsBack: Int = 0): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms; add(Calendar.MONTH, -monthsBack) }
        return "%04d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    /** New saves across [listings] in the month [monthsBack] months before [now]. */
    fun savesInMonth(listings: List<SpaceListing>, now: Long, monthsBack: Int = 0): Int {
        val key = monthKey(now, monthsBack)
        return listings.sumOf { it.favoritesByMonth[key] ?: 0 }
    }

    /** Whole percent of [part] over [views], capped at 100; null without views (nothing to compare). */
    fun ratePercent(part: Int, views: Int): Int? =
        if (views <= 0) null else ((part.coerceAtLeast(0) * 100.0 / views).toInt()).coerceAtMost(100)
}
