package com.example.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactRulesTest {
    private val base = AppUser(id = "u1", email = "t@example.com", fullName = "Test", role = UserRole.SPECIALIST, specialty = "", phone = "")
    private val phone = "+9613000000"
    private val photo = "https://example.com/p.jpg"

    @Test
    fun `booking needs both a photo and a verified phone`() {
        assertFalse(base.canTransact())
        assertFalse(base.copy(profilePictureUrl = photo).canTransact())
        assertFalse(base.copy(phone = phone, isVerified = true).canTransact())
        assertFalse(base.copy(phone = phone, isVerified = false, profilePictureUrl = photo).canTransact())
        assertTrue(base.copy(phone = phone, isVerified = true, profilePictureUrl = photo).canTransact())
    }

    @Test
    fun `a verified phone needs both the number and the verified flag`() {
        assertFalse(base.copy(phone = phone).hasVerifiedPhone)
        assertFalse(base.copy(isVerified = true).hasVerifiedPhone)
        assertTrue(base.copy(phone = phone, isVerified = true).hasVerifiedPhone)
    }
}
