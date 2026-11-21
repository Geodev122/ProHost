package com.example.data.billing

/** Calendar units for [addLocalPeriods]. */
enum class PeriodUnit { DAY, WEEK, MONTH, YEAR }

/**
 * [startMillis] plus each (unit, amount) step in the device's time zone. Android: `java.util.Calendar.add`, whose handling
 * of a repeated clock-change hour is the spec (owner decision, `BillingDatesCharacterizationTest`); other platforms use
 * kotlinx-datetime.
 */
expect fun addLocalPeriods(startMillis: Long, steps: List<Pair<PeriodUnit, Int>>): Long

/** 23:59:59 on [year]-[month]-[day] in [zoneId]; null for an impossible date. Android: a non-lenient `Calendar`. */
expect fun endOfDayMillis(zoneId: String, year: Int, month: Int, day: Int): Long?

/** [amount] in [currencyCode] formatted in the device locale (Android `NumberFormat.getCurrencyInstance()`); null if unknown. */
expect fun formatCurrencyDeviceLocale(amount: Double, currencyCode: String): String?
