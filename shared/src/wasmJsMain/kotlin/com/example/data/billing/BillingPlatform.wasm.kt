package com.example.data.billing

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

actual fun addLocalPeriods(startMillis: Long, steps: List<Pair<PeriodUnit, Int>>): Long {
    val zone = TimeZone.currentSystemDefault()
    var t = Instant.fromEpochMilliseconds(startMillis)
    for ((unit, amount) in steps) {
        t = when (unit) {
            PeriodUnit.DAY -> t.plus(amount, DateTimeUnit.DAY, zone)
            PeriodUnit.WEEK -> t.plus(amount * 7, DateTimeUnit.DAY, zone)
            PeriodUnit.MONTH -> t.plus(amount, DateTimeUnit.MONTH, zone)
            PeriodUnit.YEAR -> t.plus(amount * 12, DateTimeUnit.MONTH, zone)
        }
    }
    return t.toEpochMilliseconds()
}

actual fun endOfDayMillis(zoneId: String, year: Int, month: Int, day: Int): Long? = runCatching {
    LocalDateTime(year, month, day, 23, 59, 59).toInstant(TimeZone.of(zoneId)).toEpochMilliseconds()
}.getOrNull()

actual fun formatCurrencyDeviceLocale(amount: Double, currencyCode: String): String? =
    "$currencyCode " + com.example.util.formatFixedPortable(amount, 2, true)
