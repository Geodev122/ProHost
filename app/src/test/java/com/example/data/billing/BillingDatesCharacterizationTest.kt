package com.example.data.billing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * P2.3c finding (8 Nov 2026): these two functions keep `java.util.Calendar` for now. Reproduced on the JVM, a kotlinx-datetime / java.time port
 * differs from Calendar by exactly one hour when the result lands in the repeated hour of a clock change, because Calendar resolves such a
 * wall-clock time to the LATER (standard-time) occurrence and java.time to the EARLIER one:
 *  - `endOfDayBeirut`: the Saturday before Beirut's clocks go back (8 dates in 2025-2032, e.g. 20261024); a port would end the promo an hour early.
 *  - `regularPriceStartsAt`: 4 of 87,600 sampled cases (Europe/Berlin, result in the repeated 02:xx hour).
 * This file pins the CURRENT behaviour (including those edge cases) so the later port (common offer model, P5) must reproduce it
 * (take the later offset in an overlap) and cannot change it silently.
 */
class BillingDatesCharacterizationTest {
    private lateinit var original: TimeZone

    @Before
    fun remember() { original = TimeZone.getDefault() }

    @After
    fun restore() = TimeZone.setDefault(original)

    @Test
    fun endOfDayBeirutIsTwentyThreeFiftyNineFiftyNineLocalAndInvalidDaysAreNull() {
        assertEquals(1_792_879_199_000L, PlayPromo.endOfDayBeirut("20261024")) // overlap night: the LATER 23:59:59 (an hour after java.time's)
        assertNull(PlayPromo.endOfDayBeirut("20260230"))
        assertNull(PlayPromo.endOfDayBeirut("20250229"))
        assertNull(PlayPromo.endOfDayBeirut("00001231"))
        assertNull(PlayPromo.endOfDayBeirut("20261301"))
        assertNull(PlayPromo.endOfDayBeirut("abcdefgh"))
        assertNotNull(PlayPromo.endOfDayBeirut("20280229")) // 2028 is a leap year
    }

    @Test
    fun regularPriceStartsAtKeepsLocalWallClockTimeAcrossDst() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        // 2026-07-25 02:02:03 local + 3 months = 2026-10-25 02:02:03 local, which occurs twice; Calendar takes the later one (01:02:03Z).
        assertEquals(1_792_890_123_456L, PlayOfferText.regularPriceStartsAt(1_784_937_723_456L, listOf("P3M" to 1)))
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertEquals(1_784_937_723_456L + 3 * 86_400_000L, PlayOfferText.regularPriceStartsAt(1_784_937_723_456L, listOf("P3D" to 1)))
        assertEquals(1_784_937_723_456L, PlayOfferText.regularPriceStartsAt(1_784_937_723_456L, listOf("junk" to 1)))
    }
}
