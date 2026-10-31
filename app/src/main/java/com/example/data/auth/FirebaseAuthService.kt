package com.example.data.auth

import android.content.Context
import android.util.Log
import com.example.analytics.AnalyticsTracker
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firebase Authentication: email + password (sign up, sign in, reset) and Google. There is no
 * magic link, emailed code or phone sign-in any more (retired in 1.0.44).
 */
class FirebaseAuthService(private val context: Context) : AuthGateway {

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

    override val signedInUid: String? get() = currentFirebaseUser?.uid

    override val usesPassword: Boolean
        get() = currentFirebaseUser?.providerData?.any { it.providerId == "password" } == true

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

    private suspend fun run(block: suspend (FirebaseAuth) -> AuthOutcome): AuthOutcome {
        val auth = firebaseAuth ?: return AuthOutcome.Failure(AuthError.UNAVAILABLE, "no_firebase")
        return try {
            block(auth)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = (e as? FirebaseAuthException)?.errorCode ?: AnalyticsTracker.errorCode(e)
            Log.w(tag, "auth call failed ($code)")
            AuthOutcome.Failure(AuthError.from(e), code)
        }
    }

    override suspend fun signInWithPassword(email: String, password: String): AuthOutcome = run { auth ->
        auth.signInWithEmailAndPassword(email, password).awaitTask()
        AuthOutcome.Success(isNewUser = false)
    }

    override suspend fun createAccount(email: String, password: String): AuthOutcome = run { auth ->
        auth.createUserWithEmailAndPassword(email, password).awaitTask()
        AuthOutcome.Success(isNewUser = true)
    }

    override suspend fun signInWithGoogle(idToken: String): AuthOutcome = run { auth ->
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitTask()
        AuthOutcome.Success(isNewUser = result.additionalUserInfo?.isNewUser ?: false)
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthOutcome = run { auth ->
        auth.sendPasswordResetEmail(email).awaitTask()
        AuthOutcome.Success(isNewUser = false)
    }

    override suspend fun refreshEmailVerified(): Boolean {
        val user = currentFirebaseUser ?: return false
        return try {
            user.reload().awaitTask()
            val verified = firebaseAuth?.currentUser?.isEmailVerified == true
            // The ID token only carries email_verified after a refresh; the server mirrors it from there.
            if (verified) firebaseAuth?.currentUser?.getIdToken(true)?.awaitTask()
            verified
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override fun signOut() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Error during signOut: ${e.message}")
        }
    }
}
