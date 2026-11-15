package com.example.util

/**
 * Fixed-format (US) number text: digits after the point, optional thousands separators. The one platform seam for number
 * formatting: Android keeps `String.format(Locale.US, …)` exactly as before (no digit changes); iOS and the web console use
 * [formatFixedPortable] (round half up), which can differ from Android only for a value lying exactly on a rounding tie.
 */
expect fun formatFixed(value: Double, decimals: Int, grouping: Boolean): String

/** Pure-Kotlin fixed formatting for platforms without `String.format`: half-up rounding, "," groups, "." decimal point. */
fun formatFixedPortable(value: Double, decimals: Int, grouping: Boolean): String {
    var scale = 1.0
    repeat(decimals) { scale *= 10.0 }
    val negative = value < 0
    val scaled = kotlin.math.floor(kotlin.math.abs(value) * scale + 0.5).toLong()
    val divisor = scale.toLong()
    val whole = (scaled / divisor).toString()
    val grouped = if (!grouping) whole else whole.reversed().chunked(3).joinToString(",").reversed()
    val fraction = if (decimals == 0) "" else "." + (scaled % divisor).toString().padStart(decimals, '0')
    return (if (negative && scaled != 0L) "-" else "") + grouped + fraction
}

/**
 * The app's fixed-format (US) number text. Behaviour on Android is exactly what the call sites printed before the move to
 * `:shared` (same formatter, same rounding). Device-locale formats (`%,.0f` / `%.2f` without a Locale, `%02d`) are NOT here on
 * purpose: they follow the person's language and stay at their call sites until that behaviour is decided (PROGRESS.md).
 */
object NumberFormats {
    /** "$1,250" for whole amounts, "$12.50" otherwise (no trailing ".00"). */
    fun usdAuto(v: Double): String = "$" + if (v % 1.0 == 0.0) formatFixed(v, 0, true) else formatFixed(v, 2, true)

    /** "$1,235": whole dollars with grouping. */
    fun usdWhole(v: Double): String = "$" + formatFixed(v, 0, true)

    /** "3" for whole amounts, "2.50" otherwise (per-attendee prices). */
    fun plainAmount(amount: Double): String = if (amount % 1.0 == 0.0) amount.toLong().toString() else formatFixed(amount, 2, false)

    /** One decimal, e.g. "2.5". */
    fun oneDecimal(v: Double): String = formatFixed(v, 1, false)
}

/** Lowercase two-digit hex of a byte, locale-independent (what `"%02x".format(byte)` printed). */
object Hex {
    fun of(b: Byte): String = (b.toInt() and 0xFF).toString(16).padStart(2, '0')
}
