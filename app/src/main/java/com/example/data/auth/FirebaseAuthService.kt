package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
 * Service encapsulating Firebase Auth operations. Every user-facing message this class
 * hands back — success or failure — is written for someone who has never heard of
 * Firebase: no exception class names, no raw SDK text, ever. [Log.e]/[Log.w] still
 * carry the real technical detail for anyone reading device logs.
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
        Log.d(tag, "Initiating phone verification for: ${maskPhone(e164PhoneNumber)}")
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(e164PhoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    Log.d(tag, "Phone verification completed automatically for ${maskPhone(e164PhoneNumber)}")
                    onAutoVerified(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e(tag, "Phone verification failed for ${maskPhone(e164PhoneNumber)}: ${e.message}", e)
                    // Release builds must never show a user the raw Firebase internals
                    // (commit bb6abd9 deliberately reverted that) — but with no live
                    // Firebase Console access from a dev environment, a developer running
                    // a debug build needs some way to see the real reason SMS didn't send
                    // (the common causes — Phone Auth not enabled, a SHA fingerprint
                    // mismatch, Play Integrity not enabled, exhausted quota — all produce
                    // the same generic-looking exception otherwise). Debug-only, appended
                    // after the same sanitized message every user sees.
                    val message = friendlyVerificationErrorMessage(e)
                    val debugMessage = if (com.example.BuildConfig.DEBUG) {
                        "$message\n\n[debug] ${e::class.simpleName}: ${e.message}"
                    } else {
                        message
                    }
                    onError(debugMessage)
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    Log.d(tag, "Verification SMS code sent to ${maskPhone(e164PhoneNumber)}, verificationId=$verificationId")
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
     * Maps any exception from a phone-credential sign-in attempt to a short, plain-language
     * message — never the raw Firebase/SDK text, which can otherwise surface technical
     * strings (project config, reCAPTCHA/Play Integrity failures, class names) that mean
     * nothing to an end user and read like an app crash.
     */
    private fun phoneCredentialErrorMessage(e: Exception): String = friendlyPhoneAuthMessage(e.message)

    private fun friendlyVerificationErrorMessage(e: Exception): String = friendlyPhoneAuthMessage(e.message)

    /**
     * Masks an E.164 phone number for logging — keeps the leading "+" and the last 2
     * digits, masks everything in between, so a release-build log line still has enough
     * signal to correlate with a support ticket without shipping the full number.
     */
    private fun maskPhone(e164: String): String {
        if (e164.length <= 4) return "***"
        val visibleSuffix = e164.takeLast(2)
        return "${e164.first()}${"*".repeat(e164.length - 3)}$visibleSuffix"
    }

    private fun friendlyPhoneAuthMessage(rawMessage: String?): String {
        val m = rawMessage.orEmpty()
        return when {
            m.contains("invalid-verification-code", ignoreCase = true) ->
                "That code doesn't match. Please check and try again."
            m.contains("session-expired", ignoreCase = true) || m.contains("code-expired", ignoreCase = true) ->
                "This code has expired — request a new one."
            m.contains("invalid", ignoreCase = true) && m.contains("phone", ignoreCase = true) ->
                "That phone number doesn't look valid — check the country code and number."
            m.contains("too-many-requests", ignoreCase = true) || m.contains("quota", ignoreCase = true) ->
                "Too many attempts right now. Please wait a bit and try again."
            m.contains("network", ignoreCase = true) ->
                "Network connection error. Check your internet access and try again."
            m.contains("app-not-authorized", ignoreCase = true) ||
                m.contains("recaptcha", ignoreCase = true) ||
                m.contains("safetynet", ignoreCase = true) ||
                m.contains("play integrity", ignoreCase = true) ||
                m.contains("blocked-by-firebase", ignoreCase = true) ||
                m.contains("app-verification", ignoreCase = true) ->
                "We couldn't verify your phone number right now. Please try again in a moment, or contact support if this keeps happening."
            m.contains("credential-already-in-use", ignoreCase = true) ->
                "This phone number is already registered to a different account."
            else ->
                "We couldn't verify your phone number right now. Please try again."
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
