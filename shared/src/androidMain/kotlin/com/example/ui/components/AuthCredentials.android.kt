package com.example.ui.components

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.ui.util.sharedText
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
actual fun rememberAuthCredentials(): AuthCredentials? {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    return remember(context, isPreview) { if (!isPreview) AndroidAuthCredentials(context) else null }
}

/** The login screen's Credential Manager code, unchanged, behind [AuthCredentials]. */
private class AndroidAuthCredentials(private val context: Context) : AuthCredentials {
    private val cm = CredentialManager.create(context)
    private val activity = context.findActivity()
    private fun webClientId(): String = sharedText("default_web_client_id")

    private fun map(result: androidx.credentials.GetCredentialResponse): GoogleCredentialResult {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val g = GoogleIdTokenCredential.createFrom(credential.data)
            return GoogleCredentialResult.Token(g.idToken, g.displayName, g.givenName, g.familyName, g.profilePictureUri?.toString())
        }
        return GoogleCredentialResult.NotGoogle
    }

    override suspend fun requestGoogle(nonce: String): GoogleCredentialResult {
        val webClientId = webClientId()
        return try {
            val oneTapOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setNonce(nonce)
                .build()
            map(cm.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(oneTapOption).build()))
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            GoogleCredentialResult.Cancelled
        } catch (e: androidx.credentials.exceptions.NoCredentialException) {
            // One Tap found no matching credential: fall back to the standard Sign In With Google sheet.
            try {
                val signInOption = GetSignInWithGoogleOption.Builder(webClientId).setNonce(nonce).build()
                map(cm.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(signInOption).build()))
            } catch (e2: androidx.credentials.exceptions.GetCredentialCancellationException) {
                GoogleCredentialResult.Cancelled
            } catch (e2: GetCredentialException) {
                android.util.Log.w("LoginAuthScreen", "Google sign-in unavailable: ${e2.type}")
                GoogleCredentialResult.Unavailable
            }
        } catch (e: GetCredentialException) {
            android.util.Log.w("LoginAuthScreen", "Google sign-in error: ${e.type}")
            GoogleCredentialResult.Error
        }
    }

    override suspend fun offerSaved(nonce: String): SavedCredential? {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(androidx.credentials.GetPasswordOption())
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(webClientId())
                    .setNonce(nonce)
                    .build()
            )
            .build()
        val response = try {
            cm.getCredential(context, request)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null // nothing saved, dismissed, or no credential provider on this device
        } ?: return null
        return when (val credential = response.credential) {
            is androidx.credentials.PasswordCredential -> SavedCredential.Password(credential.id, credential.password)
            is CustomCredential -> if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val g = GoogleIdTokenCredential.createFrom(credential.data)
                SavedCredential.Google(g.idToken, g.displayName, g.profilePictureUri?.toString())
            } else null
            else -> null
        }
    }

    override suspend fun savePassword(email: String, password: String) {
        val act = activity ?: return
        try {
            cm.createCredential(act, androidx.credentials.CreatePasswordRequest(email, password))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Dismissed, or no password manager on this device: nothing to save.
        }
    }
}
