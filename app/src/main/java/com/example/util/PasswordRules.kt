package com.example.util

/**
 * The password policy, mirrored from Firebase Authentication's enforced policy (Maintenance
 * `authconfig apply`: minimum 8 characters, a lowercase letter and a number). The server is the
 * authority; this only lets the sign-up form show the checklist and stop obvious misses early.
 */
object PasswordRules {
    const val MIN_LENGTH = 8

    data class Check(val longEnough: Boolean, val hasLowercase: Boolean, val hasNumber: Boolean) {
        val ok: Boolean get() = longEnough && hasLowercase && hasNumber
    }

    fun check(password: String): Check = Check(
        longEnough = password.length >= MIN_LENGTH,
        hasLowercase = password.any { it.isLowerCase() },
        hasNumber = password.any { it.isDigit() }
    )

    fun isValid(password: String): Boolean = check(password).ok
}
