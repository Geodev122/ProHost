package com.example.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactRulesTest {
    private val base = AppUser(id = "u1", email = "t@example.com", fullName = "Test", role = UserRole.SPECIALIST, specialty = "", phone = "")
    private val phone = "+9613000000"
    private val photo = "https://example.com/p.jpg"

    @Test
    fun `booking needs both a photo and a phone linked in Firebase Auth`() {
        assertFalse(base.canTransact(phoneLinkedInAuth = true))
        assertFalse(base.copy(profilePictureUrl = photo).canTransact(phoneLinkedInAuth = true))
        assertFalse(base.copy(phone = phone).canTransact(phoneLinkedInAuth = true))
        // A typed profile phone that was never linked isn't verified.
        assertFalse(base.copy(phone = phone, profilePictureUrl = photo).canTransact(phoneLinkedInAuth = false))
        assertTrue(base.copy(phone = phone, profilePictureUrl = photo).canTransact(phoneLinkedInAuth = true))
    }

    @Test
    fun `isVerified alone (email-verified accounts) is not a verified phone`() {
        assertFalse(base.copy(isVerified = true).hasVerifiedPhone(phoneLinkedInAuth = false))
        assertFalse(base.copy(phone = phone, isVerified = true).hasVerifiedPhone(phoneLinkedInAuth = false))
        assertTrue(base.copy(phone = phone).hasVerifiedPhone(phoneLinkedInAuth = true))
    }

    @Test
    fun `hosting also needs country and city, never email verification`() {
        val ready = base.copy(phone = phone, profilePictureUrl = photo)
        assertFalse(ready.canHost(phoneLinkedInAuth = true))
        assertFalse(ready.copy(country = "Lebanon").canHost(phoneLinkedInAuth = true))
        assertTrue(ready.copy(country = "Lebanon", city = "Beirut", emailVerified = false).canHost(phoneLinkedInAuth = true))
        assertFalse(ready.copy(country = "Lebanon", city = "Beirut").canHost(phoneLinkedInAuth = false))
    }
}
