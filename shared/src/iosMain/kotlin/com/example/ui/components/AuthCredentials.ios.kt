package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.ios.GoogleSignInBridge
import com.example.ios.IosPlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
actual fun rememberAuthCredentials(): AuthCredentials? {
    val google = IosPlatform.googleSignIn ?: return null
    return remember(google) { IosAuthCredentials(google) }
}

/** iOS: Google through the GoogleSignIn SDK; saved passwords are offered by iOS's own AutoFill, so nothing here. */
private class IosAuthCredentials(private val google: GoogleSignInBridge) : AuthCredentials {
    override suspend fun requestGoogle(nonce: String): GoogleCredentialResult {
        val reply = suspendCancellableCoroutine { cont -> google.signIn(nonce) { cont.resume(it) } }
        val token = reply.idToken
        return when {
            token != null -> GoogleCredentialResult.Token(
                idToken = token,
                displayName = reply.displayName,
                givenName = reply.givenName,
                familyName = reply.familyName,
                pictureUri = reply.pictureUri
            )
            reply.cancelled -> GoogleCredentialResult.Cancelled
            else -> GoogleCredentialResult.Error
        }
    }

    override suspend fun offerSaved(nonce: String): SavedCredential? = null

    override suspend fun savePassword(email: String, password: String) {}
}
