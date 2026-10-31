package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The client mirror of Firebase's enforced password policy (min 8, a lowercase letter, a number). */
class PasswordRulesTest {

    @Test
    fun `a password meeting every rule is valid`() {
        assertTrue(PasswordRules.isValid("clinic2026"))
        assertTrue(PasswordRules.isValid("Ab3defgh"))
        assertTrue(PasswordRules.isValid("كلمةpass1"))
    }

    @Test
    fun `each missing rule is reported on its own`() {
        assertEquals(PasswordRules.Check(longEnough = false, hasLowercase = true, hasNumber = true), PasswordRules.check("abc123"))
        assertEquals(PasswordRules.Check(longEnough = true, hasLowercase = false, hasNumber = true), PasswordRules.check("ABCDEF123"))
        assertEquals(PasswordRules.Check(longEnough = true, hasLowercase = true, hasNumber = false), PasswordRules.check("abcdefghi"))
    }

    @Test
    fun `seven characters is too short, eight is enough`() {
        assertFalse(PasswordRules.isValid("abcde12"))
        assertTrue(PasswordRules.isValid("abcdef12"))
        assertEquals(8, PasswordRules.MIN_LENGTH)
    }

    @Test
    fun `empty password fails every rule`() {
        val c = PasswordRules.check("")
        assertFalse(c.longEnough || c.hasLowercase || c.hasNumber || c.ok)
    }
}
