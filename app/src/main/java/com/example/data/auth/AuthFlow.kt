package com.example.data.auth

import com.example.data.model.AppUser
import com.example.data.model.Governorate
import com.example.data.model.UserRole
import com.example.data.repository.ProSpaceRepository
import com.google.firebase.auth.FirebaseUser

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
 */
suspend fun completeVerifiedRegistration(
    repository: ProSpaceRepository,
    functionsClient: FirebaseFunctionsClient,
    firebaseUser: FirebaseUser,
    fullName: String,
    phone: String,
    specialty: String,
    syndicateNumber: String,
    affiliation: String,
    governorate: Governorate
): AppUser {
    functionsClient.ensureInitialRole().getOrThrow()
    val role = resolveVerifiedRole(functionsClient, firebaseUser)
    return repository.registerMember(
        uid = firebaseUser.uid,
        fullName = fullName,
        email = firebaseUser.email ?: "",
        phone = phone,
        verifiedRole = role,
        specialty = specialty,
        syndicateNumber = syndicateNumber,
        affiliation = affiliation,
        governorate = governorate
    )
}

private suspend fun resolveVerifiedRole(
    functionsClient: FirebaseFunctionsClient,
    firebaseUser: FirebaseUser
): UserRole {
    val claim = FirebaseFunctionsClient.readRoleClaim(firebaseUser, forceRefresh = true)
        ?: run {
            // Brand-new account with no claim yet — ask the server to assign the default,
            // then re-read the (now force-refreshed) token.
            functionsClient.ensureInitialRole().getOrThrow()
            FirebaseFunctionsClient.readRoleClaim(firebaseUser, forceRefresh = true)
        }
    return claim?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: UserRole.SPECIALIST
}
