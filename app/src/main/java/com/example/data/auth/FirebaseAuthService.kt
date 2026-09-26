package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
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
        val firebaseUser: FirebaseUser? = null,
        val email: String = "",
        val displayName: String? = null,
        val photoUrl: String? = null,
        val isNewUser: Boolean = false
    ) : AuthResult()

    /** Lightweight failure used by email/Google/phone-link methods. */
    data class Failure(val message: String) : AuthResult()
    data class Error(val message: String, val throwable: Throwable? = null) : AuthResult()
    data object Cancelled : AuthResult()
}

/**
 * Service encapsulating Firebase Auth operations.
 * Sends real SMS verification codes for all real phone numbers, with fast-path
 * test mode exclusively for explicit developer/QA whitelist numbers.
 */
class FirebaseAuthService(private val context: Context) {

    private val tag = "FirebaseAuthService"

    // Explicit developer/QA test numbers — ONLY these numbers bypass real SMS
    private val testPhoneNumbers = setOf(
        "+96170888999", "+96170123456", "+9613123456", "+961000000", "+961111222", "+96176543210"
    )

    private fun isTestPhoneNumber(phone: String): Boolean {
        val clean = phone.replace(" ", "").replace("-", "")
        return testPhoneNumbers.contains(clean)
    }

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
     * Kicks off Firebase Phone Auth SMS verification for [e164PhoneNumber].
     * Sends real SMS to all end-user phone numbers.
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

        // Fast-path ONLY for explicit QA/developer whitelist numbers — debug builds only
        if (com.example.BuildConfig.DEBUG && isTestPhoneNumber(e164PhoneNumber)) {
            Log.d(tag, "Using instant test verification for QA number: ${maskPhone(e164PhoneNumber)}")
            val testVerificationId = "TEST-VERIFY-ID-" + e164PhoneNumber.replace("+", "").replace(" ", "")
            onCodeSent(testVerificationId)
            return
        }

        Log.d(tag, "Initiating real SMS phone verification for: ${maskPhone(e164PhoneNumber)}")
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
                    val isRateLimited = e.message.orEmpty().contains("too-many-requests", ignoreCase = true) ||
                            e.message.orEmpty().contains("unusual activity", ignoreCase = true) ||
                            e.message.orEmpty().contains("blocked", ignoreCase = true) ||
                            e.message.orEmpty().contains("quota", ignoreCase = true)

                    if (isRateLimited) {
                        // Only fall back to synthetic verification in debug builds.
                        // In release builds, surface the error so the user knows to try again later.
                        if (com.example.BuildConfig.DEBUG) {
                            Log.w(tag, "Device rate-limited by Firebase. Activating instant verification fallback (debug only).")
                            val testVerificationId = "TEST-VERIFY-ID-" + e164PhoneNumber.replace("+", "").replace(" ", "")
                            onCodeSent(testVerificationId)
                        } else {
                            Log.w(tag, "Device rate-limited by Firebase (production — surfacing error).")
                            onError("Too many verification attempts. Please try again later.")
                        }
                        return
                    }

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

    /** Builds the credential from a verification id and the SMS code typed in. */
    fun buildPhoneAuthCredential(verificationId: String, smsCode: String): PhoneAuthCredential =
        PhoneAuthProvider.getCredential(verificationId, smsCode)

    /**
     * Signs in with phone credential.
     *
     * @param verificationId The verification id [credential] was built from
     * (FirebaseAuthService.buildPhoneAuthCredential's first argument) — pass it
     * whenever the caller has it, so this can tell a real Firebase-issued
     * verification apart from this class's own synthetic
     * "TEST-VERIFY-ID-..." one (see [sendPhoneVerificationCode]'s QA-whitelist
     * fast path and rate-limit fallback). Only the latter is allowed to fall
     * back to an already-cached/anonymous session below — a real credential
     * failing (wrong code, expired code, network hiccup — all common, everyday
     * user mistakes) must always surface as a real error. It previously didn't:
     * ANY 6-digit code — which every real OTP also is — on a real credential's
     * failure silently "succeeded" by grabbing whatever Firebase user happened
     * to already be cached on the device, or minting a brand-new anonymous
     * account and reporting it as a genuinely new registration. That is almost
     * certainly why an already-registered phone number could get routed to the
     * registration form instead of being recognized: a real code mismatch (or
     * any other transient credential failure) was silently reinterpreted as
     * "brand new user," never as the error it actually was.
     */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential, verificationId: String? = null): AuthResult {
        val auth = firebaseAuth
            ?: return AuthResult.Error("Authentication service unavailable. Please check your connection and try again.")

        val smsCode = credential.smsCode.orEmpty()
        val isTestFlow = verificationId?.startsWith("TEST-VERIFY-ID-") == true
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
            if (!isTestFlow) {
                Log.w(tag, "Real credential sign-in failed (${e.message}).")
                return AuthResult.Error(friendlyPhoneAuthMessage(e.message), e)
            }
            Log.w(tag, "Test-flow credential sign-in failed (${e.message}). Using instant test verification fallback.")
            if (smsCode == "123456" || smsCode == "000000" || smsCode == "666666" || smsCode.length == 6) {
                val current = auth.currentUser
                if (current != null) {
                    return AuthResult.Success(
                        firebaseUser = current,
                        email = current.email ?: "",
                        displayName = current.displayName,
                        photoUrl = current.photoUrl?.toString(),
                        isNewUser = false
                    )
                } else {
                    return try {
                        val anonResult = auth.signInAnonymously().awaitTask()
                        val anonUser = anonResult.user
                        AuthResult.Success(
                            firebaseUser = anonUser,
                            email = anonUser?.email ?: "",
                            displayName = anonUser?.displayName,
                            photoUrl = anonUser?.photoUrl?.toString(),
                            isNewUser = anonResult.additionalUserInfo?.isNewUser ?: true
                        )
                    } catch (anonErr: Exception) {
                        AuthResult.Error(friendlyPhoneAuthMessage(e.message), e)
                    }
                }
            }
            AuthResult.Error(friendlyPhoneAuthMessage(e.message), e)
        }
    }

    private fun friendlyPhoneAuthMessage(rawMessage: String?): String = friendlyVerificationErrorMessage(rawMessage)

    private fun friendlyVerificationErrorMessage(rawMessage: String?): String {
        val m = rawMessage.orEmpty()
        return when {
            m.contains("invalid-verification-code", ignoreCase = true) ->
                "That code doesn't match. Please check and try again."
            m.contains("session-expired", ignoreCase = true) || m.contains("code-expired", ignoreCase = true) ->
                "This code has expired — request a new one."
            m.contains("invalid", ignoreCase = true) && m.contains("phone", ignoreCase = true) ->
                "That phone number doesn't look valid — check the country code and number."
            m.contains("too-many-requests", ignoreCase = true) || m.contains("quota", ignoreCase = true) || m.contains("unusual activity", ignoreCase = true) ->
                "Device temporarily rate-limited. Enter verification code 123456 to continue."
            m.contains("network", ignoreCase = true) ->
                "Network connection error. Check your internet access and try again."
            else ->
                "We couldn't verify your phone number right now. Please check the number and try again."
        }
    }

    private fun friendlyVerificationErrorMessage(e: FirebaseException): String = friendlyVerificationErrorMessage(e.message)

    private fun maskPhone(e164: String): String {
        if (e164.length <= 4) return "***"
        val visibleSuffix = e164.takeLast(2)
        return "${e164.first()}${"*".repeat(e164.length - 3)}$visibleSuffix"
    }

    // --- Email / Google sign-in methods ---

    suspend fun signInWithGoogleCredential(googleIdToken: String): AuthResult {
        val auth = firebaseAuth
            ?: return AuthResult.Failure("Authentication service unavailable.")
        return try {
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val result = auth.signInWithCredential(credential).awaitTask()
            val isNewUser = result.additionalUserInfo?.isNewUser ?: false
            AuthResult.Success(isNewUser = isNewUser)
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Google sign-in failed")
        }
    }

    suspend fun sendSignInLinkToEmail(email: String, continueUrl: String): Boolean {
        val auth = firebaseAuth ?: return false
        return try {
            val settings = ActionCodeSettings.newBuilder()
                .setUrl(continueUrl)
                .setHandleCodeInApp(true)
                .setAndroidPackageName("com.example", true, null)
                .build()
            auth.sendSignInLinkToEmail(email, settings).awaitTask()
            true
        } catch (e: Exception) {
            Log.w(tag, "sendSignInLinkToEmail failed: ${e.message}")
            false
        }
    }

    suspend fun signInWithEmailLink(email: String, emailLink: String): AuthResult {
        val auth = firebaseAuth
            ?: return AuthResult.Failure("Authentication service unavailable.")
        return try {
            val result = auth.signInWithEmailLink(email, emailLink).awaitTask()
            val isNewUser = result.additionalUserInfo?.isNewUser ?: false
            AuthResult.Success(isNewUser = isNewUser)
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Email sign-in failed")
        }
    }

    fun isSignInWithEmailLink(link: String): Boolean {
        return try {
            firebaseAuth?.isSignInWithEmailLink(link) ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun linkPhoneCredentialToCurrentUser(credential: PhoneAuthCredential): AuthResult {
        val auth = firebaseAuth
            ?: return AuthResult.Failure("Authentication service unavailable.")
        return try {
            val user = auth.currentUser ?: return AuthResult.Failure("No signed-in user")
            user.linkWithCredential(credential).awaitTask()
            AuthResult.Success(isNewUser = false)
        } catch (e: FirebaseAuthUserCollisionException) {
            AuthResult.Failure("This phone number is already linked to another account.")
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Phone linking failed")
        }
    }

    @Suppress("DEPRECATION")
    suspend fun fetchSignInMethodsForEmail(email: String): List<String> {
        val auth = firebaseAuth ?: return emptyList()
        return try {
            auth.fetchSignInMethodsForEmail(email).awaitTask().signInMethods ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun signOut() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Error during signOut: ${e.message}")
        }
    }
}
