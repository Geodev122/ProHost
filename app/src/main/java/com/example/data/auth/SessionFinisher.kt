package com.example.data.auth

import android.content.Context
import android.net.Uri
import com.example.data.model.AppUser
import com.example.data.repository.ProHostRepository
import com.example.data.storage.FirebaseStorageService
import com.example.util.PlayIntegrityManager
import com.google.firebase.auth.FirebaseAuth

/**
 * The server half of a sign-in, after Firebase Authentication accepted the credential: Play
 * Integrity token, role from assignInitialRole, local session, registration. An interface so
 * AuthViewModel's routing (registration vs. straight in) is unit-testable.
 */
interface SessionFinisher {
    /** Returning user: resolves the server role and opens the local session. */
    suspend fun completeLogin(): AppUser
    /** New user: writes the registration through assignInitialRole and opens the session. */
    suspend fun completeRegistration(details: RegistrationDetails): AppUser
    suspend fun uploadProfilePicture(uri: Uri): String?
    suspend fun existingProfileComplete(): Boolean
    suspend fun discardIncompleteSession()
    /** Mirrors a newly verified email onto the profile (assignInitialRole reads email_verified). */
    suspend fun syncEmailVerified()
}

class FirebaseSessionFinisher(
    private val context: Context,
    private val repository: ProHostRepository,
    private val functionsClient: FirebaseFunctionsClient
) : SessionFinisher {

    private fun user() = FirebaseAuth.getInstance().currentUser
        ?: throw IllegalStateException("No signed-in Firebase user")

    private suspend fun integrityToken(uid: String) = PlayIntegrityManager(context).requestIntegrityToken(uid)

    override suspend fun completeLogin(): AppUser {
        val u = user()
        return completeVerifiedLogin(repository, functionsClient, u, integrityToken(u.uid))
    }

    override suspend fun completeRegistration(details: RegistrationDetails): AppUser {
        val u = user()
        return completeVerifiedRegistration(repository, functionsClient, u, details, integrityToken(u.uid))
    }

    override suspend fun uploadProfilePicture(uri: Uri): String? =
        FirebaseStorageService.getInstance().uploadProfilePicture(user().uid, uri)

    override suspend fun existingProfileComplete(): Boolean =
        repository.getUserProfile(user().uid)?.fullName?.isNotBlank() == true

    override suspend fun discardIncompleteSession() = repository.discardIncompleteSession()

    override suspend fun syncEmailVerified() {
        functionsClient.ensureInitialRole().getOrThrow()
    }
}
