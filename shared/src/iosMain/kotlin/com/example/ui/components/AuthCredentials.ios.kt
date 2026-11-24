package com.example.ui.components

import com.example.util.resumeOnce
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.ios.AppleSignInBridge
import com.example.ios.GoogleSignInBridge
import com.example.ios.IosPlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
actual fun rememberAuthCredentials(): AuthCredentials? {
    val google = IosPlatform.googleSignIn ?: return null
    val apple = IosPlatform.appleSignIn
    return remember(google, apple) { IosAuthCredentials(google, apple) }
}

/** iOS: Google through the GoogleSignIn SDK; saved passwords are offered by iOS's own AutoFill, so nothing here. */
private class IosAuthCredentials(
    private val google: GoogleSignInBridge,
    private val apple: AppleSignInBridge?
) : AuthCredentials {
    override suspend fun requestGoogle(nonce: String): GoogleCredentialResult {
        val reply = suspendCancellableCoroutine { cont -> google.signIn(nonce) { cont.resumeOnce(it) } }
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

    override val supportsApple: Boolean get() = apple != null

    override suspend fun requestApple(rawNonce: String): AppleCredentialResult {
        val bridge = apple ?: return AppleCredentialResult.Error
        val reply = suspendCancellableCoroutine { cont -> bridge.signInWithAppleSheet(rawNonce) { cont.resumeOnce(it) } }
        val token = reply.idToken
        return when {
            token != null -> AppleCredentialResult.Token(token, reply.givenName, reply.familyName)
            reply.cancelled -> AppleCredentialResult.Cancelled
            else -> AppleCredentialResult.Error
        }
    }

    override suspend fun offerSaved(nonce: String): SavedCredential? = null

    override suspend fun savePassword(email: String, password: String) {}
}
