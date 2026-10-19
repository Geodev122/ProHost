package com.example.data.auth

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
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

    /** Lightweight failure used by the email and Google methods. */
    data class Failure(val message: String) : AuthResult()
    data class Error(val message: String, val throwable: Throwable? = null) : AuthResult()
    data object Cancelled : AuthResult()
}

/**
 * Service encapsulating Firebase Auth operations (email link, Google, custom token).
 * There is no phone/SMS sign-in or verification: the WhatsApp number is saved to the profile.
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

    /** Firebase Auth's own mailer. The failure carries the real reason (logged, never shown raw). */
    suspend fun sendSignInLinkToEmail(email: String, continueUrl: String): Result<Unit> {
        val auth = firebaseAuth ?: return Result.failure(IllegalStateException("Authentication service unavailable"))
        return try {
            val settings = ActionCodeSettings.newBuilder()
                .setUrl(continueUrl)
                .setHandleCodeInApp(true)
                .setAndroidPackageName("app.geonajjar.prohost", true, "24")
                .build()
            auth.sendSignInLinkToEmail(email, settings).awaitTask()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(tag, "sendSignInLinkToEmail failed (${(e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode}): ${e.message}")
            Result.failure(e)
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

    @Suppress("DEPRECATION")
    suspend fun fetchSignInMethodsForEmail(email: String): List<String> {
        val auth = firebaseAuth ?: return emptyList()
        return try {
            auth.fetchSignInMethodsForEmail(email).awaitTask().signInMethods ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun signInWithEmailAndPassword(email: String, password: String) {
        val auth = firebaseAuth ?: throw Exception("Authentication service unavailable.")
        auth.signInWithEmailAndPassword(email, password).awaitTask()
    }

    suspend fun signInWithCustomToken(token: String): AuthResult {
        val auth = firebaseAuth ?: return AuthResult.Failure("Authentication service unavailable.")
        return try {
            val result = auth.signInWithCustomToken(token).awaitTask()
            val isNewUser = result.additionalUserInfo?.isNewUser ?: false
            AuthResult.Success(isNewUser = isNewUser)
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Sign-in failed")
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
