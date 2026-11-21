package com.example.data.auth

import com.example.data.model.AppUser

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
    suspend fun uploadProfilePicture(file: com.example.util.PickedFile): String?
    suspend fun existingProfileComplete(): Boolean
    suspend fun discardIncompleteSession()
    /** Mirrors a newly verified email onto the profile (assignInitialRole reads email_verified). */
    suspend fun syncEmailVerified()
}
