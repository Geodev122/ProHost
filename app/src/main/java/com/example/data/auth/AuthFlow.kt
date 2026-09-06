package com.example.data.auth

import com.example.data.model.AppUser
import com.example.data.model.UserRole
import com.example.data.repository.ProSpaceRepository
import com.google.firebase.auth.FirebaseUser

/**
 * Thrown when assignInitialRole.ts rejects a sign-in because the account is
 * suspended (see setAccountSuspended.ts). Callers must sign the (already
 * Firebase-Auth-authenticated) user back out and surface [message] instead of
 * completing sign-in — see ProSpaceViewModel's phone/Google sign-in flows.
 */
class AccountSuspendedException(message: String) : Exception(message)

/**
 * Resolves the signed-in [firebaseUser]'s server-verified role (assigning the default
 * via Cloud Functions on first sign-in if none exists yet) and completes the local
 * sign-in against [repository]. This is the only path by which the app should ever
 * decide a returning/new user's role after real Firebase Auth succeeds.
 */
suspend fun completeVerifiedLogin(
    repository: ProSpaceRepository,
    functionsClient: FirebaseFunctionsClient,
    firebaseUser: FirebaseUser
): AppUser {
    val role = resolveVerifiedRole(functionsClient, firebaseUser)
    return repository.login(
        uid = firebaseUser.uid,
        email = firebaseUser.email ?: "",
        verifiedRole = role
    )
}

/**
 * Same as [completeVerifiedLogin], but for brand-new member registration. Every new
 * account is a SPECIALIST — there is no registration-time way to become a PRO_HOST
 * (that role is granted exclusively, server-side, once a real package/PAYG Whish
 * payment settles; see FirebaseFunctionsClient's note on grantEntitlement()), and
 * requesting ADMIN is never honored by any reachable code path.
 *
 * [firebaseUser] here has already been phone-verified (see LoginAuthScreen's OTP flow)
 * — that's what [firebaseUser.uid] and [firebaseUser.phoneNumber] represent. [email]
 * comes from the registration form, not [firebaseUser.email] (a phone-auth FirebaseUser
 * has no email of its own). There is no admin accreditation review anymore: profile
 * picture and ID document are simply kept on file (their Storage URLs, already
 * uploaded by the caller once [firebaseUser.uid] existed to key the upload path on).
 */
suspend fun completeVerifiedRegistration(
    repository: ProSpaceRepository,
    functionsClient: FirebaseFunctionsClient,
    firebaseUser: FirebaseUser,
    fullName: String,
    email: String,
    phone: String,
    specialty: String,
    profilePictureUrl: String?,
    idDocumentUrl: String?,
    country: String,
    governorate: String,
    city: String
): AppUser {
    functionsClient.ensureInitialRole().getOrThrow()
    val role = resolveVerifiedRole(functionsClient, firebaseUser)
    return repository.registerMember(
        uid = firebaseUser.uid,
        fullName = fullName,
        email = email,
        phone = phone,
        verifiedRole = role,
        specialty = specialty,
        profilePictureUrl = profilePictureUrl,
        idDocumentUrl = idDocumentUrl,
        country = country,
        governorate = governorate,
        city = city
    )
}

private suspend fun resolveVerifiedRole(
    functionsClient: FirebaseFunctionsClient,
    firebaseUser: FirebaseUser
): UserRole {
    val existingClaim = FirebaseFunctionsClient.readRoleClaim(firebaseUser, forceRefresh = true)
    // Always call assignInitialRole, not just when there's no claim yet — it's the only
    // place lastSignInAtMillis (and isVerified) get refreshed, and that needs to happen
    // on every sign-in, not just account creation. Idempotent server-side; a transient
    // failure here shouldn't block sign-in for a returning user who already has a valid
    // claim — EXCEPT an account-suspended rejection, which must always block sign-in
    // regardless of whether a claim already existed (see setAccountSuspended.ts).
    val result = functionsClient.ensureInitialRole()
    result.exceptionOrNull()?.let { error ->
        if (error is AccountSuspendedException || existingClaim == null) throw error
    }
    val claim = existingClaim
        ?: FirebaseFunctionsClient.readRoleClaim(firebaseUser, forceRefresh = true)
    return claim?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: UserRole.SPECIALIST
}
