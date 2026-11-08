package com.example.util

import java.util.Locale

/**
 * The app's fixed-format (US) number text: the one place that still calls the platform formatter (`String.format`), so shared code never does.
 * Behaviour is exactly what the call sites printed before (same formatter, same rounding, whatever the platform does for halves); nothing here
 * changes a digit. In P3 this becomes an `expect`/`actual` seam: Android keeps `String.format`, iOS gets its own formatter (round half up on the
 * shortest decimal digits). Device-locale formats (`%,.0f` / `%.2f` without a Locale, `%02d`) are NOT here on purpose: they follow the person's
 * language and stay at their call sites until that behaviour is decided (listed in PROGRESS.md).
 */
object NumberFormats {
    /** "$1,250" for whole amounts, "$12.50" otherwise (no trailing ".00"). */
    fun usdAuto(v: Double): String =
        if (v % 1.0 == 0.0) String.format(Locale.US, "$%,.0f", v) else String.format(Locale.US, "$%,.2f", v)

    /** "$1,235": whole dollars with grouping. */
    fun usdWhole(v: Double): String = "$" + String.format(Locale.US, "%,.0f", v)

    /** "3" for whole amounts, "2.50" otherwise (per-attendee prices). */
    fun plainAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toLong().toString() else String.format(Locale.US, "%.2f", amount)

    /** One decimal, e.g. "2.5". */
    fun oneDecimal(v: Double): String = String.format(Locale.US, "%.1f", v)
}

/** Lowercase two-digit hex of a byte, locale-independent (what `"%02x".format(byte)` printed). */
object Hex {
    fun of(b: Byte): String = (b.toInt() and 0xFF).toString(16).padStart(2, '0')
}
