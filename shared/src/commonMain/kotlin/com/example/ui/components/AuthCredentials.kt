package com.example.ui.components

import androidx.compose.runtime.Composable

/** What the Google sign-in sheet returned. */
sealed interface GoogleCredentialResult {
    /** A Google ID token (the caller checks its nonce). */
    data class Token(
        val idToken: String,
        val displayName: String?,
        val givenName: String?,
        val familyName: String?,
        val pictureUri: String?,
    ) : GoogleCredentialResult
    /** The person dismissed the account picker. */
    data object Cancelled : GoogleCredentialResult
    /** No Google sign-in on this device (One Tap and the fallback sheet both unavailable). */
    data object Unavailable : GoogleCredentialResult
    /** The first request failed for another reason. */
    data object Error : GoogleCredentialResult
    /** A credential came back that is not a Google ID token: nothing to do. */
    data object NotGoogle : GoogleCredentialResult
}

/** A credential the device's password manager offered to a returning person. */
sealed interface SavedCredential {
    data class Password(val id: String, val password: String) : SavedCredential
    data class Google(val idToken: String, val displayName: String?, val pictureUri: String?) : SavedCredential
}

/**
 * The device's sign-in credentials (Android: Credential Manager — Google One Tap with the Sign in with Google sheet as
 * fallback, saved passwords; iOS: Sign in with Apple / Google in C2). Every request carries the caller's [nonce].
 */
interface AuthCredentials {
    suspend fun requestGoogle(nonce: String): GoogleCredentialResult
    /** A saved password or an already-authorized Google account, or null (nothing saved, dismissed, no provider). */
    suspend fun offerSaved(nonce: String): SavedCredential?
    /** Offers to save [email]/[password] in the password manager; returns when the offer is done (dismissed is fine). */
    suspend fun savePassword(email: String, password: String)
}

/** The screen's [AuthCredentials], or null in previews and on platforms without one yet. */
@Composable
expect fun rememberAuthCredentials(): AuthCredentials?
