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
