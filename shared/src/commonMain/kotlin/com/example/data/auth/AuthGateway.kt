package com.example.data.auth

/**
 * Firebase Authentication as the sign-in screens need it (email + password, Google). An interface
 * so AuthViewModel can be unit-tested without Firebase; [FirebaseAuthService] is the real one.
 */
interface AuthGateway {
    val signedInUid: String?
    suspend fun signInWithPassword(email: String, password: String): AuthOutcome
    suspend fun createAccount(email: String, password: String): AuthOutcome
    suspend fun signInWithGoogle(idToken: String): AuthOutcome
    /** Sign in with Apple (iOS): Apple's ID token and the raw nonce whose SHA-256 the request carried. Android: not offered. */
    suspend fun signInWithApple(idToken: String, rawNonce: String, givenName: String?, familyName: String?): AuthOutcome =
        AuthOutcome.Failure(AuthError.UNAVAILABLE, "ERROR_OPERATION_NOT_ALLOWED")
    /** Firebase's own reset mailer: the fallback when our branded callable is unavailable. */
    suspend fun sendPasswordResetEmail(email: String): AuthOutcome
    /** Reloads the signed-in user; true when their email is verified now. */
    suspend fun refreshEmailVerified(): Boolean
    /** Whether the signed-in account signs in with a password (and so may need email verification). */
    val usesPassword: Boolean
    fun signOut()
}

sealed interface AuthOutcome {
    data class Success(val isNewUser: Boolean) : AuthOutcome
    data class Failure(val error: AuthError, val code: String) : AuthOutcome
}

/**
 * What went wrong with an email/password or Google sign-in, in terms the screen can explain.
 * Firebase's own messages are English developer text ("The supplied auth credential is
 * incorrect, malformed or has expired") and must never reach people.
 */
enum class AuthError {
    /** Wrong email or password (enumeration protection merges "no such user" into this). */
    INVALID_CREDENTIALS,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    TOO_MANY_REQUESTS,
    USER_DISABLED,
    NETWORK,
    UNAVAILABLE,
    UNKNOWN;

    companion object {
        /** Pure mapping of Firebase Auth error codes (unit-tested). */
        fun fromCode(code: String?): AuthError = when (code?.uppercase()) {
            "ERROR_INVALID_CREDENTIAL", "INVALID_LOGIN_CREDENTIALS", "ERROR_INVALID_LOGIN_CREDENTIALS",
            "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_USER_MISMATCH" -> INVALID_CREDENTIALS
            "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_CREDENTIAL_ALREADY_IN_USE",
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> EMAIL_IN_USE
            "ERROR_WEAK_PASSWORD", "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS",
            "PASSWORD_DOES_NOT_MEET_REQUIREMENTS" -> WEAK_PASSWORD
            "ERROR_INVALID_EMAIL", "ERROR_MISSING_EMAIL" -> INVALID_EMAIL
            "ERROR_TOO_MANY_REQUESTS" -> TOO_MANY_REQUESTS
            "ERROR_USER_DISABLED" -> USER_DISABLED
            "ERROR_NETWORK_REQUEST_FAILED" -> NETWORK
            "ERROR_OPERATION_NOT_ALLOWED", "ERROR_INTERNAL_ERROR" -> UNAVAILABLE
            else -> UNKNOWN
        }
    }
}
