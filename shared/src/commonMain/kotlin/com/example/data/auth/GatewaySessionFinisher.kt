package com.example.data.auth

import com.example.data.gateway.FunctionsGateway
import com.example.data.model.AppUser
import com.example.data.repository.ProHostRepository

/**
 * [SessionFinisher] over [SessionAuth] and the shared gateways, used by iOS. Same steps as the Android
 * `FirebaseSessionFinisher`; [attestation] supplies the device-integrity token (Android: Play Integrity;
 * iOS: none yet, the server then allows the sign-in and logs it, as for a failed decode).
 */
class GatewaySessionFinisher(
    private val repository: ProHostRepository,
    private val functionsClient: FunctionsGateway,
    private val attestation: suspend (uid: String) -> String? = { null }
) : SessionFinisher {

    private fun user() = SessionAuth.currentUser() ?: throw IllegalStateException("No signed-in Firebase user")

    override suspend fun completeLogin(): AppUser {
        val u = user()
        return completeVerifiedLogin(repository, functionsClient, u, attestation(u.uid))
    }

    override suspend fun completeRegistration(details: RegistrationDetails): AppUser {
        val u = user()
        return completeVerifiedRegistration(repository, functionsClient, u, details, attestation(u.uid))
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
