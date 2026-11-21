package com.example.data.billing

actual fun addLocalPeriods(startMillis: Long, steps: List<Pair<PeriodUnit, Int>>): Long {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = startMillis }
    for ((unit, amount) in steps) {
        val field = when (unit) {
            PeriodUnit.DAY -> java.util.Calendar.DAY_OF_YEAR
            PeriodUnit.WEEK -> java.util.Calendar.WEEK_OF_YEAR
            PeriodUnit.MONTH -> java.util.Calendar.MONTH
            PeriodUnit.YEAR -> java.util.Calendar.YEAR
        }
        cal.add(field, amount)
    }
    return cal.timeInMillis
}

actual fun endOfDayMillis(zoneId: String, year: Int, month: Int, day: Int): Long? =
    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(zoneId)).apply {
        isLenient = false
        clear()
        set(year, month - 1, day, 23, 59, 59)
    }.let { runCatching { it.timeInMillis }.getOrNull() }

actual fun formatCurrencyDeviceLocale(amount: Double, currencyCode: String): String? = runCatching {
    val fmt = java.text.NumberFormat.getCurrencyInstance()
    fmt.currency = java.util.Currency.getInstance(currencyCode)
    fmt.format(amount)
}.getOrNull()
