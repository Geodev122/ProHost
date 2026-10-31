package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactRulesTest {
    private val base = AppUser(id = "u1", email = "t@example.com", fullName = "Test", role = UserRole.SPECIALIST, specialty = "", phone = "")
    private val phone = "+9613000000"
    private val photo = "https://example.com/p.jpg"

    @Test
    fun `booking needs both a photo and a WhatsApp number`() {
        assertFalse(base.canTransact())
        assertFalse(base.copy(profilePictureUrl = photo).canTransact())
        assertFalse(base.copy(phone = phone).canTransact())
        assertTrue(base.copy(phone = phone, profilePictureUrl = photo).canTransact())
    }

    @Test
    fun `the number must be in international form`() {
        assertFalse(base.copy(phone = "+961").hasWhatsAppNumber()) // bare dial code
        assertFalse(base.copy(phone = "03000000").hasWhatsAppNumber()) // no country code
        assertFalse(base.copy(phone = "+96170abc123").hasWhatsAppNumber())
        assertTrue(base.copy(phone = "+961 70 123 456").hasWhatsAppNumber())
        assertTrue(base.copy(phone = phone).hasWhatsAppNumber())
        // isVerified (email-verified accounts) never stands in for a number.
        assertFalse(base.copy(isVerified = true).hasWhatsAppNumber())
    }

    @Test
    fun `hosting also needs country and city, never email verification`() {
        val ready = base.copy(phone = phone, profilePictureUrl = photo)
        assertFalse(ready.canHost())
        assertFalse(ready.copy(country = "Lebanon").canHost())
        assertTrue(ready.copy(country = "Lebanon", city = "Beirut", emailVerified = false).canHost())
        assertFalse(ready.copy(phone = "", country = "Lebanon", city = "Beirut").canHost())
    }

    @Test
    fun `a saved number splits back into country and local part`() {
        val lebanon = findCountryByName("Lebanon")
        val (country, local) = splitE164("+96170123456", findCountryByName("France"))
        assertEquals(lebanon.dialCode, country.dialCode)
        assertEquals("70123456", local)
        assertEquals(lebanon to "", splitE164("", lebanon))
    }
}
