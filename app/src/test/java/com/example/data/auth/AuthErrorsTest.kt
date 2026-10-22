package com.example.data.auth

import com.example.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** Firebase Auth error codes → what the sign-in screen says (never Firebase's own English text). */
class AuthErrorsTest {

    @Test
    fun `wrong email or password read the same`() {
        listOf(
            "ERROR_INVALID_CREDENTIAL", "INVALID_LOGIN_CREDENTIALS", "ERROR_INVALID_LOGIN_CREDENTIALS",
            "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "error_wrong_password"
        ).forEach { assertEquals(it, AuthError.INVALID_CREDENTIALS, AuthError.fromCode(it)) }
    }

    @Test
    fun `sign-up errors`() {
        assertEquals(AuthError.EMAIL_IN_USE, AuthError.fromCode("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals(AuthError.EMAIL_IN_USE, AuthError.fromCode("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL"))
        assertEquals(AuthError.WEAK_PASSWORD, AuthError.fromCode("ERROR_WEAK_PASSWORD"))
        assertEquals(AuthError.WEAK_PASSWORD, AuthError.fromCode("PASSWORD_DOES_NOT_MEET_REQUIREMENTS"))
        assertEquals(AuthError.INVALID_EMAIL, AuthError.fromCode("ERROR_INVALID_EMAIL"))
    }

    @Test
    fun `account and service states`() {
        assertEquals(AuthError.TOO_MANY_REQUESTS, AuthError.fromCode("ERROR_TOO_MANY_REQUESTS"))
        assertEquals(AuthError.USER_DISABLED, AuthError.fromCode("ERROR_USER_DISABLED"))
        assertEquals(AuthError.NETWORK, AuthError.fromCode("ERROR_NETWORK_REQUEST_FAILED"))
        assertEquals(AuthError.UNAVAILABLE, AuthError.fromCode("ERROR_OPERATION_NOT_ALLOWED"))
    }

    @Test
    fun `unknown codes fall back to a generic message`() {
        assertEquals(AuthError.UNKNOWN, AuthError.fromCode(null))
        assertEquals(AuthError.UNKNOWN, AuthError.fromCode("SOMETHING_NEW"))
        assertEquals(AuthError.UNKNOWN, AuthError.from(IllegalStateException("boom")))
    }

    @Test
    fun `every error has its own resource and wrong credentials point at Forgot password`() {
        assertEquals(R.string.err_invalid_credentials, AuthError.INVALID_CREDENTIALS.message)
        assertEquals(R.string.password_doesnt_meet_rules, AuthError.WEAK_PASSWORD.message)
        assertEquals(AuthError.entries.size, AuthError.entries.map { it.message }.toSet().size)
    }
}
