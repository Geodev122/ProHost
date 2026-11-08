package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P2.3i: the fixed-format US number text now goes through NumberFormats (same String.format calls as before; this test pins known outputs that are
 * not rounding-boundary cases, so it holds on any platform formatter) and the hex byte is pure Kotlin, proven for all 256 values.
 */
class NumberFormatsTest {
    @Test
    fun usdAutoShowsNoTrailingZerosOnWholeAmounts() {
        assertEquals("$1,250", NumberFormats.usdAuto(1250.0))
        assertEquals("$12.50", NumberFormats.usdAuto(12.5))
        assertEquals("$1,234,567.89", NumberFormats.usdAuto(1_234_567.891))
        assertEquals("$0", NumberFormats.usdAuto(0.0))
        assertEquals("$600", NumberFormats.usdAuto(600.0))
    }

    @Test
    fun usdWholeGroupsThousands() {
        assertEquals("$0", NumberFormats.usdWhole(0.0))
        assertEquals("$1,235", NumberFormats.usdWhole(1234.6))
        assertEquals("$12,000", NumberFormats.usdWhole(12000.0))
        assertEquals("$1,000,000", NumberFormats.usdWhole(1_000_000.0))
    }

    @Test
    fun plainAmountAndOneDecimal() {
        assertEquals("3", NumberFormats.plainAmount(3.0))
        assertEquals("2.50", NumberFormats.plainAmount(2.5))
        assertEquals("7.99", NumberFormats.plainAmount(7.99))
        assertEquals("2.5", NumberFormats.oneDecimal(2.5))
        assertEquals("0.3", NumberFormats.oneDecimal(1.0 / 3.0))
        assertEquals("12.0", NumberFormats.oneDecimal(12.0))
    }

    @Test
    fun hexOfEveryByteEqualsThePercentO2xFormat() {
        for (i in 0..255) {
            val b = i.toByte()
            assertEquals("byte $i", "%02x".format(b), Hex.of(b))
        }
        assertEquals("00", Hex.of(0)); assertEquals("ff", Hex.of(-1)); assertEquals("0a", Hex.of(10))
    }
}
