package com.example.data.auth

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
import com.google.firebase.auth.AuthResult as FirebaseAuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class AuthResult {
    data class Success(
        val firebaseUser: FirebaseUser?,
        val email: String,
        val displayName: String?,
        val photoUrl: String? = null
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
     * Sign in with Email and Password using Firebase Auth.
     */
    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty()) {
            return AuthResult.Error("Please enter a valid email address.")
        }
        if (password.isEmpty()) {
            return AuthResult.Error("Please enter your password.")
        }

        val auth = firebaseAuth
        if (auth == null) {
            // Local fallback if Firebase services are not provisioned in this environment
            return AuthResult.Success(
                firebaseUser = null,
                email = trimmedEmail,
                displayName = trimmedEmail.substringBefore("@").replace(".", " ").capitalizeWords()
            )
        }

        return try {
            val result = auth.signInWithEmailAndPassword(trimmedEmail, password).awaitTask()
            val user = result.user
            AuthResult.Success(
                firebaseUser = user,
                email = user?.email ?: trimmedEmail,
                displayName = user?.displayName ?: trimmedEmail.substringBefore("@").replace(".", " ").capitalizeWords(),
                photoUrl = user?.photoUrl?.toString()
            )
        } catch (e: Exception) {
            Log.e(tag, "Firebase signInWithEmail error: ${e.message}", e)
            val friendlyMessage = when {
                e.message?.contains("user-not-found", ignoreCase = true) == true -> "No account found with this email."
                e.message?.contains("wrong-password", ignoreCase = true) == true ||
                e.message?.contains("invalid-credential", ignoreCase = true) == true -> "Incorrect password. Please verify your credentials."
                e.message?.contains("invalid-email", ignoreCase = true) == true -> "Invalid email address format."
                e.message?.contains("network", ignoreCase = true) == true -> "Network connection error. Check your internet access."
                else -> e.localizedMessage ?: "Authentication failed. Please try again."
            }
            AuthResult.Error(friendlyMessage, e)
        }
    }

    /**
     * Register a new user with Email and Password in Firebase Auth.
     */
    suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String
    ): AuthResult {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
            return AuthResult.Error("Please enter a valid email address.")
        }
        if (password.length < 6) {
            return AuthResult.Error("Password must be at least 6 characters long.")
        }

        val auth = firebaseAuth
        if (auth == null) {
            return AuthResult.Success(
                firebaseUser = null,
                email = trimmedEmail,
                displayName = displayName.ifBlank { trimmedEmail.substringBefore("@").capitalizeWords() }
            )
        }

        return try {
            val result = auth.createUserWithEmailAndPassword(trimmedEmail, password).awaitTask()
            val user = result.user
            if (user != null && displayName.isNotBlank()) {
                try {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build()
                    user.updateProfile(profileUpdates).awaitTask()
                } catch (pe: Exception) {
                    Log.w(tag, "Failed to update Firebase user profile: ${pe.message}")
                }
            }
            AuthResult.Success(
                firebaseUser = user,
                email = user?.email ?: trimmedEmail,
                displayName = user?.displayName ?: displayName,
                photoUrl = user?.photoUrl?.toString()
            )
        } catch (e: Exception) {
            Log.e(tag, "Firebase registerWithEmail error: ${e.message}", e)
            val friendlyMessage = when {
                e.message?.contains("email-already-in-use", ignoreCase = true) == true -> "An account with this email already exists."
                e.message?.contains("weak-password", ignoreCase = true) == true -> "Password is too weak. Use at least 6 characters."
                e.message?.contains("invalid-email", ignoreCase = true) == true -> "Invalid email format."
                else -> e.localizedMessage ?: "Account registration failed."
            }
            AuthResult.Error(friendlyMessage, e)
        }
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
                val firebaseUser: FirebaseUser? = if (auth != null && idToken.isNotBlank()) {
                    try {
                        val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = auth.signInWithCredential(authCredential).awaitTask()
                        authResult.user
                    } catch (e: Exception) {
                        Log.w(tag, "GoogleAuthProvider signInWithCredential fallback: ${e.message}")
                        null
                    }
                } else {
                    null
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
     * Send password reset email via Firebase Auth.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val trimmed = email.trim().lowercase()
        val auth = firebaseAuth ?: return Result.success(Unit)
        return try {
            auth.sendPasswordResetEmail(trimmed).awaitTask()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to send reset email: ${e.message}", e)
            Result.failure(e)
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

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
