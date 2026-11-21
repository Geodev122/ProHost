package com.example.data.auth
import android.content.Context
import com.example.data.model.AppUser
import com.example.data.repository.ProHostRepository
import com.example.util.PlayIntegrityManager
import com.google.firebase.auth.FirebaseAuth

/** Android implementation of [SessionFinisher]: Firebase Auth's current user + Play Integrity. */
class FirebaseSessionFinisher(
    private val context: Context,
    private val repository: ProHostRepository,
    private val functionsClient: com.example.data.gateway.FunctionsGateway
) : SessionFinisher {

    private fun user() = FirebaseAuth.getInstance().currentUser
        ?: throw IllegalStateException("No signed-in Firebase user")

    private suspend fun integrityToken(uid: String) = PlayIntegrityManager(context).requestIntegrityToken(uid)

    override suspend fun completeLogin(): AppUser {
        val u = user()
        return completeVerifiedLogin(repository, functionsClient, u.toSignedInUser(), integrityToken(u.uid))
    }

    override suspend fun completeRegistration(details: RegistrationDetails): AppUser {
        val u = user()
        return completeVerifiedRegistration(repository, functionsClient, u.toSignedInUser(), details, integrityToken(u.uid))
    }

    override suspend fun uploadProfilePicture(file: com.example.util.PickedFile): String? =
        repository.storage.uploadProfilePicture(user().uid, file)

    override suspend fun existingProfileComplete(): Boolean =
        repository.getUserProfile(user().uid)?.fullName?.isNotBlank() == true

    override suspend fun discardIncompleteSession() = repository.discardIncompleteSession()

    override suspend fun syncEmailVerified() {
        functionsClient.ensureInitialRole().getOrThrow()
    }
}
