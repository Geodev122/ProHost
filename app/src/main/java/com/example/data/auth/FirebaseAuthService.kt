package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.AuthResult as FirebaseAuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class AuthResult {
    data class Success(
        val firebaseUser: FirebaseUser?,
        val email: String,
        val displayName: String?,
        val photoUrl: String? = null,
        // True only when this credential just created a brand-new Firebase Auth account
        // (Firebase's own signal, from AuthResult.additionalUserInfo — never guessed
        // client-side). The registration UI uses this to decide whether a phone number
        // that just verified belongs to a first-time registrant (show the "complete your
        // profile" form) or a returning member (go straight to sign-in).
        val isNewUser: Boolean = false
    ) : AuthResult()

    data class Error(val message: String, val throwable: Throwable? = null) : AuthResult()
    data object Cancelled : AuthResult()
}

/**
 * Service encapsulating Firebase Auth operations and Google Credential Manager integration.
 */
class FirebaseAuthService(private val context: Context) {

    private val tag = "FirebaseAuthService"

    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "Firebase initialization warning: ${e.message}")
            null
        }
    }

    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    val currentFirebaseUser: FirebaseUser?
        get() = try {
            firebaseAuth?.currentUser
        } catch (e: Exception) {
            null
        }

    val isUserSignedIn: Boolean
        get() = currentFirebaseUser != null

    /**
     * Suspend helper for Firebase Tasks to avoid missing dependencies.
     */
    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }

    /**
     * Kicks off Firebase Phone Auth SMS verification for [e164PhoneNumber] (must already
     * be in full E.164 form, e.g. "+96170123456" — building that from a country-code
     * selector + local number is the caller's job). Exactly one of [onCodeSent] /
     * [onAutoVerified] / [onError] fires. Auto-verification (Google Play services
     * silently confirming the SMS matches this device, no manual code entry needed) is
     * a real possibility on many devices — callers must handle both outcomes, not just
     * treat this as "always shows an OTP entry screen."
     */
    fun sendPhoneVerificationCode(
        activity: Activity,
        e164PhoneNumber: String,
        onCodeSent: (verificationId: String) -> Unit,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
        onError: (String) -> Unit
    ) {
        val auth = firebaseAuth
        if (auth == null) {
            onError("Authentication service unavailable. Please check your connection and try again.")
            return
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(e164PhoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    onAutoVerified(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e(tag, "Phone verification failed: ${e.message}", e)
                    val friendlyMessage = when {
                        e.message?.contains("invalid", ignoreCase = true) == true &&
                            e.message?.contains("phone", ignoreCase = true) == true ->
                            "That phone number doesn't look valid — check the country code and number."
                        e.message?.contains("quota", ignoreCase = true) == true ->
                            "Too many verification attempts right now. Please try again later."
                        e.message?.contains("network", ignoreCase = true) == true ->
                            "Network connection error. Check your internet access."
                        else -> e.localizedMessage ?: "Phone verification failed. Please try again."
                    }
                    onError(friendlyMessage)
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    onCodeSent(verificationId)
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /** Builds the credential from a verification id (from [sendPhoneVerificationCode]'s onCodeSent) and the SMS code the user typed in. */
    fun buildPhoneAuthCredential(verificationId: String, smsCode: String): PhoneAuthCredential =
        PhoneAuthProvider.getCredential(verificationId, smsCode)

    /**
     * Signs in (or, for a brand-new phone number, creates the Firebase Auth account for)
     * the phone number behind [credential]. This is the ONLY way a phone number becomes
     * a signed-in identity in this app — the SMS OTP itself is what Firebase verifies,
     * never anything client-supplied.
     */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): AuthResult {
        val auth = firebaseAuth
            ?: return AuthResult.Error("Authentication service unavailable. Please check your connection and try again.")
        return try {
            val result = auth.signInWithCredential(credential).awaitTask()
            val user = result.user
                ?: return AuthResult.Error("Phone sign-in did not return a Firebase user.")
            AuthResult.Success(
                firebaseUser = user,
                email = user.email ?: "",
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
                isNewUser = result.additionalUserInfo?.isNewUser ?: false
            )
        } catch (e: Exception) {
            Log.e(tag, "signInWithPhoneCredential error: ${e.message}", e)
            AuthResult.Error(phoneCredentialErrorMessage(e), e)
        }
    }

    /**
     * Links [credential] to the currently signed-in Firebase user — used to attach a
     * verified phone number to an account that signed up via Google Sign-In (which
     * doesn't itself verify a phone number), completing the "every account gets
     * phone-verified" requirement without creating a second, separate account.
     */
    suspend fun linkPhoneCredential(credential: PhoneAuthCredential): AuthResult {
        val auth = firebaseAuth
            ?: return AuthResult.Error("Authentication service unavailable. Please check your connection and try again.")
        val user = auth.currentUser
            ?: return AuthResult.Error("You need to be signed in before linking a phone number.")
        return try {
            val result = user.linkWithCredential(credential).awaitTask()
            val linkedUser = result.user ?: user
            AuthResult.Success(
                firebaseUser = linkedUser,
                email = linkedUser.email ?: "",
                displayName = linkedUser.displayName,
                photoUrl = linkedUser.photoUrl?.toString()
            )
        } catch (e: Exception) {
            Log.e(tag, "linkPhoneCredential error: ${e.message}", e)
            val friendlyMessage = if (e.message?.contains("credential-already-in-use", ignoreCase = true) == true) {
                "This phone number is already registered to a different account."
            } else {
                phoneCredentialErrorMessage(e)
            }
            AuthResult.Error(friendlyMessage, e)
        }
    }

    private fun phoneCredentialErrorMessage(e: Exception): String = when {
        e.message?.contains("invalid-verification-code", ignoreCase = true) == true -> "That code doesn't match. Please check and try again."
        e.message?.contains("session-expired", ignoreCase = true) == true ||
            e.message?.contains("code-expired", ignoreCase = true) == true -> "This code has expired — request a new one."
        e.message?.contains("network", ignoreCase = true) == true -> "Network connection error. Check your internet access."
        else -> e.localizedMessage ?: "Phone verification failed. Please try again."
    }

    /**
     * Google Sign-In using Android Credential Manager and Firebase Auth GoogleAuthProvider.
     */
    suspend fun signInWithGoogleCredentialManager(
        activityContext: Context,
        serverClientId: String? = null
    ): AuthResult {
        return try {
            val clientId = serverClientId
                ?: "114265295089-prospace-android.apps.googleusercontent.com"

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                context = activityContext,
                request = request
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName ?: email.substringBefore("@")
                val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()

                val auth = firebaseAuth
                if (auth == null) {
                    return AuthResult.Error("Authentication service unavailable. Please check your connection and try again.")
                }
                if (idToken.isBlank()) {
                    return AuthResult.Error("Google sign-in did not return a valid identity token.")
                }

                val firebaseUser: FirebaseUser = try {
                    val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = auth.signInWithCredential(authCredential).awaitTask()
                    authResult.user
                        ?: return AuthResult.Error("Google sign-in did not return a Firebase user.")
                } catch (e: Exception) {
                    Log.e(tag, "GoogleAuthProvider signInWithCredential error: ${e.message}", e)
                    return AuthResult.Error("Google sign-in failed: ${e.localizedMessage}", e)
                }

                AuthResult.Success(
                    firebaseUser = firebaseUser,
                    email = email,
                    displayName = displayName,
                    photoUrl = photoUrl
                )
            } else {
                AuthResult.Error("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.i(tag, "Credential manager cancelled by user")
            AuthResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(tag, "No Google accounts found or Google Play Services unavailable: ${e.message}")
            AuthResult.Error("No Google credentials available on this device.", e)
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(tag, "Failed to parse Google ID token: ${e.message}", e)
            AuthResult.Error("Failed to parse Google ID token.", e)
        } catch (e: GetCredentialException) {
            Log.e(tag, "CredentialManager error: ${e.message}", e)
            AuthResult.Error("Google Sign-In failed: ${e.localizedMessage}", e)
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error during Google Sign-In: ${e.message}", e)
            AuthResult.Error("Sign in error: ${e.localizedMessage}", e)
        }
    }

    /**
     * Sign out from Firebase Auth.
     */
    fun signOut() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Error during signOut: ${e.message}")
        }
    }
}
