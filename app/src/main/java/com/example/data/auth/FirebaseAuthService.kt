package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.BuildConfig
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
        val isNewUser: Boolean = false
    ) : AuthResult()

    data class Error(val message: String, val throwable: Throwable? = null) : AuthResult()
    data object Cancelled : AuthResult()
}

/**
 * Service encapsulating Firebase Auth operations.
 *
 * Login strategy:
 *  - Signup: OTP (Firebase Phone Auth) → REGISTRATION_FORM → set 6-digit PIN
 *  - Login: phone number → 6-digit PIN (verified server-side via verifyPinAndIssueToken CF)
 *  - Forgot PIN: phone → OTP → set new PIN
 *
 * QA test-mode fast-path is available ONLY in debug builds for the explicit
 * developer whitelist — it is never active in release builds.
 */
class FirebaseAuthService(private val context: Context) {

    private val tag = "FirebaseAuthService"

    // QA/developer fast-path numbers — only compiled in debug builds.
    // These never exist in release APKs.
    private val testPhoneNumbers: Set<String> = if (BuildConfig.DEBUG) {
        setOf("+96170888999", "+96170123456", "+9613123456", "+961000000", "+961111222", "+96176543210")
    } else {
        emptySet()
    }

    private fun isTestPhoneNumber(phone: String): Boolean {
        if (testPhoneNumbers.isEmpty()) return false
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
     * Used for: initial signup and forgot-PIN OTP verification.
     * NOT used for returning-user login — that path goes through [signInWithCustomToken].
     *
     * PRODUCTION REQUIREMENT: both the debug AND release SHA-1 / SHA-256 fingerprints
     * must be added to Firebase Console → Project Settings → Android App → SHA certificate
     * fingerprints. Without the release SHA-256, Firebase rejects SMS for real users while
     * test-phone-number fast-path bypasses this check (hence "test users work, new users fail").
     * Run `./gradlew signingReport` to get the fingerprints for every build variant.
     *
     * [resendToken] is the token received in [onCodeSent] from a prior call. Passing it
     * re-uses the rate-limit slot; omit (null) for a first-time send.
     */
    fun sendPhoneVerificationCode(
        activity: Activity,
        e164PhoneNumber: String,
        onCodeSent: (verificationId: String, resendToken: PhoneAuthProvider.ForceResendingToken?) -> Unit,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
        onError: (String) -> Unit,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null
    ) {
        val auth = firebaseAuth
        if (auth == null) {
            onError("Authentication service unavailable. Please check your connection and try again.")
            return
        }

        // Fast-path ONLY in debug builds for the explicit QA/developer whitelist.
        // This block does not exist at all in release builds (testPhoneNumbers is empty).
        if (BuildConfig.DEBUG && isTestPhoneNumber(e164PhoneNumber)) {
            Log.d(tag, "DEBUG: Using instant test verification for QA number: ${maskPhone(e164PhoneNumber)}")
            val testVerificationId = "TEST-VERIFY-ID-" + e164PhoneNumber.replace("+", "").replace(" ", "")
            onCodeSent(testVerificationId, null)
            return
        }

        Log.d(tag, "Initiating real SMS phone verification for: ${maskPhone(e164PhoneNumber)}")
        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(e164PhoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    Log.d(tag, "Phone verification completed automatically for ${maskPhone(e164PhoneNumber)}")
                    onAutoVerified(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e(tag, "Phone verification failed for ${maskPhone(e164PhoneNumber)}: ${e.javaClass.simpleName} — ${e.message}")
                    val message = friendlyVerificationErrorMessage(e)
                    val debugMessage = if (BuildConfig.DEBUG) {
                        "$message\n\n[debug] ${e::class.simpleName}: ${e.message}\n" +
                            "Action needed: add the release SHA-256 fingerprint to Firebase Console → App → SHA certificates."
                    } else {
                        message
                    }
                    onError(debugMessage)
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    Log.d(tag, "Verification SMS code sent to ${maskPhone(e164PhoneNumber)}, verificationId=$verificationId")
                    onCodeSent(verificationId, token)
                }
            })
        if (resendToken != null) {
            builder.setForceResendingToken(resendToken)
        }
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    /**
     * Exchanges a Google ID token (obtained via GoogleSignIn + [GoogleSignInHelper]) for
     * a Firebase Auth session. Returns [AuthResult.Success] with `isNewUser = true` when
     * this Google account has never signed into this Firebase project before.
     *
     * A [com.google.firebase.auth.FirebaseAuthUserCollisionException] (account-exists-
     * with-different-credential) means the email is already registered via phone OTP —
     * the error message guides the user to log in with their phone number instead.
     */
    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult {
        val auth = firebaseAuth
            ?: return AuthResult.Error("Authentication service unavailable. Please check your connection and try again.")
        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).awaitTask()
            val user = result.user
                ?: return AuthResult.Error("Google sign-in did not return a user. Please try again.")
            AuthResult.Success(
                firebaseUser = user,
                email = user.email ?: "",
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
                isNewUser = result.additionalUserInfo?.isNewUser ?: false
            )
        } catch (e: Exception) {
            Log.e(tag, "Google sign-in failed: ${e.message}", e)
            val msg = e.message.orEmpty()
            when {
                msg.contains("account-exists-with-different-credential", ignoreCase = true) ->
                    AuthResult.Error("This email is already registered via phone number. Please sign in with your phone number instead.")
                msg.contains("network", ignoreCase = true) ->
                    AuthResult.Error("Network error. Check your connection and try again.")
                else ->
                    AuthResult.Error("Google sign-in failed. Please try again.")
            }
        }
    }

    /** Builds the credential from a verification id and the SMS code typed in. */
    fun buildPhoneAuthCredential(verificationId: String, smsCode: String): PhoneAuthCredential =
        PhoneAuthProvider.getCredential(verificationId, smsCode)

    /**
     * Signs in with a Firebase Phone Auth credential (OTP flow).
     * Used for signup and forgot-PIN OTP reset. Returning-user login
     * bypasses this entirely — see [signInWithCustomToken].
     */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential, verificationId: String? = null): AuthResult {
        val auth = firebaseAuth
            ?: return AuthResult.Error("Authentication service unavailable. Please check your connection and try again.")

        val isTestFlow = BuildConfig.DEBUG && verificationId?.startsWith("TEST-VERIFY-ID-") == true
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
            // Debug-only test flow: accept any 6-digit code for QA numbers.
            val smsCode = credential.smsCode.orEmpty()
            Log.w(tag, "DEBUG: Test-flow credential sign-in fallback for QA number.")
            if (smsCode.length == 6) {
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

    /**
     * Signs in with a Firebase custom token returned by the [verifyPinAndIssueToken]
     * Cloud Function. Used exclusively for returning-user PIN login — no OTP involved.
     */
    suspend fun signInWithCustomToken(customToken: String): AuthResult {
        val auth = firebaseAuth
            ?: return AuthResult.Error("Authentication service unavailable. Please check your connection and try again.")
        return try {
            val result = auth.signInWithCustomToken(customToken).awaitTask()
            val user = result.user
                ?: return AuthResult.Error("Custom token sign-in did not return a Firebase user.")
            AuthResult.Success(
                firebaseUser = user,
                email = user.email ?: "",
                displayName = user.displayName,
                photoUrl = user.photoUrl?.toString(),
                isNewUser = false
            )
        } catch (e: Exception) {
            Log.e(tag, "Custom token sign-in failed: ${e.message}", e)
            AuthResult.Error("Sign-in failed. Please check your PIN and try again.", e)
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
                "Too many verification attempts. Please wait a few minutes and try again."
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

    fun signOut() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Error during signOut: ${e.message}")
        }
    }
}
