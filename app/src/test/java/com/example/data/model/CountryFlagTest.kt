package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The flag is built in common Kotlin (no `Character.toChars`); it must equal what the JVM call produced. */
class CountryFlagTest {
    @Test
    fun `flag emoji equals the JVM code point conversion`() {
        for (iso in listOf("LB", "US", "ae", "FR", "ZA")) {
            val expected = iso.uppercase().map { 0x1F1E6 + (it - 'A') }.joinToString("") { String(Character.toChars(it)) }
            assertEquals(expected, Country("x", iso, "+1").flagEmoji)
        }
        assertEquals("🇱🇧", Country("Lebanon", "LB", "+961").flagEmoji)
    }
}
