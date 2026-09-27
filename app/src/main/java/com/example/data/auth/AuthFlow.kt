package com.example.data.auth

import com.example.data.model.AppUser
import com.example.data.model.UserRole
import com.example.data.repository.ProHostRepository
import com.google.firebase.auth.FirebaseUser

/**
 * Thrown when assignInitialRole.ts rejects a sign-in because the account is
 * suspended (see setAccountSuspended.ts). Callers must sign the (already
 * Firebase-Auth-authenticated) user back out and surface [message] instead of
 * completing sign-in — see ProHostViewModel's phone/Google sign-in flows.
 */
class AccountSuspendedException(message: String) : Exception(message)

/**
 * Groups the registration-form fields shared by [completeVerifiedRegistration] and
 * [completeGoogleRegistration] so neither signature needs a long flat parameter list.
 */
data class RegistrationDetails(
    val fullName: String,
    val email: String,
    val phone: String,
    val specialty: String,
    val profilePictureUrl: String?,
    val country: String,
    val governorate: String,
    val city: String,
    val tosAccepted: Boolean
)

/**
 * Resolves the signed-in [firebaseUser]'s server-verified role (assigning the default
 * via Cloud Functions on first sign-in if none exists yet) and completes the local
 * sign-in against [repository]. This is the only path by which the app should ever
 * decide a returning/new user's role after real Firebase Auth succeeds.
 */
suspend fun completeVerifiedLogin(
    repository: ProHostRepository,
    functionsClient: FirebaseFunctionsClient,
    firebaseUser: FirebaseUser,
    integrityToken: String? = null
): AppUser {
    val role = resolveVerifiedRole(functionsClient, firebaseUser, integrityToken)
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
 * picture is simply kept on file (its Storage URL, already uploaded by the caller once
 * [firebaseUser.uid] existed to key the upload path on). Registration never collects a
 * government ID — that only happens later, through the KYC flow (KycScreen.kt /
 * KycVerificationDialog.kt) required before a Specialist can book, or before upgrading
 * to Pro Host.
 */
suspend fun completeVerifiedRegistration(
    repository: ProHostRepository,
    functionsClient: FirebaseFunctionsClient,
    firebaseUser: FirebaseUser,
    details: RegistrationDetails,
    integrityToken: String? = null
): AppUser {
    functionsClient.ensureInitialRole(
        registrationDraft = mapOf(
            "fullName" to details.fullName,
            "email" to details.email,
            "tosAccepted" to details.tosAccepted
        ),
        integrityToken = integrityToken
    ).getOrThrow()
    val role = resolveVerifiedRole(functionsClient, firebaseUser, integrityToken)
    return repository.registerMember(
        uid = firebaseUser.uid,
        verifiedRole = role,
        details = details
    )
}

/**
 * Completes sign-in for an existing user who authenticated via Google Sign-In.
 * Functionally identical to [completeVerifiedLogin] — reuses the same role-resolution
 * and repository.login() path. The distinction exists for call-site clarity.
 */
suspend fun completeGoogleSignIn(
    repository: ProHostRepository,
    functionsClient: FirebaseFunctionsClient,
    firebaseUser: FirebaseUser,
    integrityToken: String? = null
): AppUser = completeVerifiedLogin(repository, functionsClient, firebaseUser, integrityToken)

/**
 * Registers a brand-new user who signed up via Google Sign-In.
 * The Google account provides email, display name, and photo — the caller
 * should pre-fill the registration form with these values and pass them here
 * after the user completes the remaining required fields (specialty, location).
 */
suspend fun completeGoogleRegistration(
    repository: ProHostRepository,
    functionsClient: FirebaseFunctionsClient,
    firebaseUser: FirebaseUser,
    details: RegistrationDetails,
    integrityToken: String? = null
): AppUser = completeVerifiedRegistration(
    repository = repository,
    functionsClient = functionsClient,
    firebaseUser = firebaseUser,
    // firebaseUser.phoneNumber is always null for Google Sign-In — Google's Auth
    // API exposes no phone number at all, so this fallback is a no-op for Google
    // users and phone correctly stays blank until real phone OTP KYC verification
    // (linkKycPhone) writes it. Never treat a Google account as phone-verified.
    details = details.copy(phone = details.phone.ifBlank { firebaseUser.phoneNumber ?: "" }),
    integrityToken = integrityToken
)

private suspend fun resolveVerifiedRole(
    functionsClient: FirebaseFunctionsClient,
    firebaseUser: FirebaseUser,
    integrityToken: String? = null
): UserRole {
    // forceRefresh=true makes this a real network round-trip to Firebase's token
    // endpoint — offline, that throws outright, before this function's own
    // documented "offline but a claim already exists" fallback below ever gets a
    // chance to run (the throw happens on this very first line, well before the
    // exceptionOrNull() tolerance check). A plain, non-forced read serves the ID
    // token's already-cached claims with no network call at all — exactly the
    // "existing claim" this function's fallback logic wants. Wrapped in
    // runCatching as well, purely defensive: even a non-forced read touches the
    // token cache and there's no reason a truly corrupt/missing cache should be
    // allowed to bring down session restoration when the real fallback (network
    // ensureInitialRole call, below) might still succeed.
    val existingClaim = runCatching {
        FirebaseFunctionsClient.readRoleClaim(firebaseUser, forceRefresh = false)
    }.getOrNull()
    // Always call assignInitialRole, not just when there's no claim yet — it's the only
    // place lastSignInAtMillis (and isVerified) get refreshed, and that needs to happen
    // on every sign-in, not just account creation. Idempotent server-side; a transient
    // failure here shouldn't block sign-in for a returning user who already has a valid
    // claim — EXCEPT an account-suspended rejection, which must always block sign-in
    // regardless of whether a claim already existed (see setAccountSuspended.ts).
    val result = functionsClient.ensureInitialRole(integrityToken = integrityToken)
    result.exceptionOrNull()?.let { error ->
        if (error is AccountSuspendedException || existingClaim == null) throw error
    }
    val claim = existingClaim
        ?: FirebaseFunctionsClient.readRoleClaim(firebaseUser, forceRefresh = true)
    return claim?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: UserRole.SPECIALIST
}
