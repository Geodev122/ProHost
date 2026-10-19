package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountriesTest {
    @Test
    fun `Syria is selectable with its dial code and nothing in the list blocks it`() {
        val syria = findCountryByIsoCode("SY")
        assertEquals("Syria", syria.name)
        assertEquals("+963", syria.dialCode)
        assertTrue(COUNTRIES.any { it.isoCode == "SY" })
    }

    @Test
    fun `Syrian numbers format to E164 however they are typed`() {
        val syria = findCountryByIsoCode("SY")
        assertEquals("+963933123456", formatToE164(syria, "0933 123 456"))
        assertEquals("+963933123456", formatToE164(syria, "933123456"))
        assertEquals("+963933123456", formatToE164(syria, "+963 933 123 456"))
        assertEquals("+963933123456", formatToE164(syria, "00963933123456"))
    }

    @Test
    fun `Lebanese numbers keep working`() {
        val lebanon = findCountryByIsoCode("LB")
        assertEquals("+96170123456", formatToE164(lebanon, "70 123 456"))
        assertEquals("+9613123456", formatToE164(lebanon, "03 123 456"))
        assertEquals("+96171123456", formatToE164(lebanon, "+961 71 123 456"))
    }
}
