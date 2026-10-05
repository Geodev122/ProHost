package com.example.data.repository

import android.util.Log
import com.example.data.auth.toUserMessage
import com.example.data.auth.FirebaseFunctionsClient
import com.example.data.auth.RegistrationDetails
import com.example.data.demo.DemoDataGenerator
import com.example.data.firestore.FirestoreSchema
import com.example.data.firestore.FirestoreService
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Part of [ProHostRepository], split out by area. Every function runs against the shared
 * repository state (`with(repo)`), and ProHostRepository keeps a same-signature delegate
 * for each, so callers and tests are unchanged.
 */
internal class ProfilesRepository(private val repo: ProHostRepository) {

    // --- User Authentication & Member Registration ---
    /**
     * Registers a new member. [uid] must be the real Firebase Auth UID (so this user's
     * `id` lines up with the `user_profiles/{uid}` document the role-claim Cloud Functions
     * write to) and [verifiedRole] must already have been confirmed server-side — see
     * [com.example.data.auth.completeVerifiedRegistration]. [isVerified] reflects that
     * [uid]'s Firebase Auth account already completed phone-number SMS verification
     * before this is ever called (see LoginAuthScreen's OTP flow) — there is no admin
     * accreditation step anymore.
     *
     * Suspend, and its Firestore write is awaited and checked, because of a real bug this
     * fixes: assignInitialRole.ts's Admin-SDK write always lands (and creates the
     * user_profiles/{uid} document) before this function's own write does, so by the time
     * this write reaches Firestore it's evaluated as an UPDATE, not a create — and
     * AppUser.toFirestoreMap() unconditionally includes ownerPackageId/
     * ownerPackageExpiryMillis, both on the update rule's protected-fields list (see
     * ProHostRepository.PROTECTED_UPDATE_FIELDS below), so the whole write used to be silently rejected
     * with permission-denied. The caller (completeVerifiedRegistration) is already a
     * suspend fun with exactly one call site, so making this suspend too costs nothing.
     */
    suspend fun registerMember(
        uid: String,
        verifiedRole: UserRole,
        details: RegistrationDetails
    ): AppUser {
        return with(repo) {
            val cleanEmail = details.email.trim().lowercase()
            val newUser = AppUser(
                id = uid,
                email = cleanEmail,
                fullName = details.fullName.trim(),
                role = verifiedRole,
                specialty = details.specialty.trim(),
                phone = details.phone.trim(),
                profilePictureUrl = details.profilePictureUrl,
                country = details.country.trim(),
                governorate = details.governorate.trim(),
                city = details.city.trim(),
                isVerified = true
            )

            // A targeted merge of only the fields this registration actually owns — never the
            // fields firestore.rules' user_profiles update rule protects (role/isVerified/
            // ownerPackageId/etc. — assignInitialRole.ts already correctly set/defaulted all
            // of those). Filtering toFirestoreMap() by this list, rather than hand-listing the
            // "safe" fields, means a future field added to toFirestoreMap() is safe-by-default
            // unless it's also added here. Keep this in sync with firestore.rules' own list.
            val safeFields = newUser.toFirestoreMap().filterKeys { it !in ProHostRepository.PROTECTED_UPDATE_FIELDS }
            val saved = firestoreService.updateUserProfileFields(uid, safeFields)
            if (!saved) {
                throw IllegalStateException("We couldn't save your profile. Please check your connection and try again.")
            }

            _users.value = _users.value.filterNot { it.id == uid } + newUser
            _currentUser.value = newUser

            addAuditLog(
                actionType = "MEMBER_REGISTRATION",
                details = "New member registered: ${newUser.fullName} (${newUser.role.name}) • ${newUser.specialty} • ${newUser.city}" +
                    ", ${newUser.governorate}, ${newUser.country}",
                severity = "SECURE",
                actorEmail = newUser.email
            )
            return newUser
        }
    }

    /** Cache-first lookup used for idempotency checks (e.g. before re-writing a profile). */
    suspend fun getUserProfile(uid: String): AppUser? =
        with(repo) { _users.value.find { it.id == uid }
            ?: firestoreService.getUserProfile(uid)?.let { AppUser.fromFirestoreMap(uid, it) } }

    suspend fun login(uid: String, email: String, verifiedRole: UserRole): AppUser {
        return with(repo) {
            val cleanEmail = email.trim().lowercase()
            val existing = _users.value.find { it.id == uid }
                ?: firestoreService.getUserProfile(uid)?.let { AppUser.fromFirestoreMap(uid, it) }

            // No stored profile yet → a blank name, so isProfileComplete() routes the user to
            // the registration form instead of into the app with an invented identity.
            val user = existing?.copy(role = verifiedRole, email = cleanEmail.ifBlank { existing.email }) ?: AppUser(
                id = uid,
                email = cleanEmail,
                fullName = "",
                role = verifiedRole,
                specialty = "",
                phone = "",
                country = "",
                governorate = "",
                city = "",
                isVerified = true
            )

            _users.value = _users.value.filterNot { it.id == uid } + user
            _currentUser.value = user
            // assignInitialRole.ts already runs (Admin SDK, bypassing rules) before this
            // is ever called — see completeVerifiedLogin — and keeps role/isVerified/
            // createdAtMillis/lastSignInAtMillis correctly in sync server-side on every
            // sign-in. This client-side write exists only to fix up email, and only ever
            // as a targeted field: writing this function's other, locally-fabricated
            // defaults (specialty="", phone="", country="Lebanon", ...) for a user whose
            // real profile just hasn't synced to this device yet would clobber their real
            // stored values, and echoing role/isVerified back risks disagreeing with the
            // real server-stored value and getting the whole write rejected.
            if (cleanEmail.isNotBlank()) {
                coroutineScope.launch { firestoreService.updateUserProfileFields(uid, mapOf("email" to cleanEmail)) }
            }

            addAuditLog(
                actionType = "USER_LOGIN_SUCCESS",
                details = "Role: ${user.role.name} • Name: ${user.fullName} (${user.email})",
                severity = if (user.role == UserRole.ADMIN) "SECURE" else "INFO",
                actorEmail = user.email
            )
            return user
        }
    }

    /**
     * @param clearRemotePushToken Best-effort clears this device's fcmToken off the
     * signed-out user's own profile doc before it's nulled locally, so a push meant
     * for them can't keep reaching this device once someone else signs in on it —
     * see ProHostViewModel.logout()'s comment for why this must run before
     * FirebaseAuth.signOut() invalidates the write's auth context. Pass false from
     * account-deletion (ProHostViewModel.deleteAccount()): the profile doc there has
     * already been deleted server-side, and a merge write after that would just
     * resurrect a stub user_profiles/{uid} doc with nothing in it but this field.
     */
    suspend fun logout(clearRemotePushToken: Boolean = true) {
        with(repo) {
            val loggedOutUser = _currentUser.value
            val previous = loggedOutUser?.email ?: "Unknown"
            if (clearRemotePushToken && loggedOutUser != null) {
                try {
                    // Only clear the stored token when it is THIS device's — otherwise signing
                    // out here would silence push on the user's other, still-signed-in phone.
                    val deviceToken = runCatching {
                        @Suppress("DEPRECATION")
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                    }.getOrNull()
                    val storedToken = firestoreService.getUserProfile(loggedOutUser.id)?.get("fcmToken") as? String
                    if (storedToken != null && (deviceToken == null || storedToken == deviceToken)) {
                        firestoreService.updateUserProfileFields(loggedOutUser.id, mapOf("fcmToken" to null))
                    }
                } catch (e: Exception) {
                    Log.w(ProHostRepository.TAG, "FCM token clear on logout failed: ${e.message}")
                }
            }
            // Also clear the in-app "Real-time Alerts Terminal" — this StateFlow is a
            // process-wide singleton with no per-uid scoping, so without this an alert
            // history from the account that just signed out stayed visible to whoever
            // signs in next in the same app process.
            _fcmAlerts.value = emptyList()
            _currentUser.value = null
            _hasLoadedBookingsOnce.value = false
            _hasLoadedSpacesOnce.value = false
            addAuditLog(
                actionType = "USER_LOGOUT",
                details = "Session closed for $previous",
                severity = "INFO",
                actorEmail = previous
            )
        }
    }

    /**
     * Undoes completeVerifiedLogin's currentUser assignment for one specific
     * case: a phone number that's Firebase-Auth-verified but whose registration
     * was interrupted before the profile form was ever submitted (app killed
     * between OTP verification and completeVerifiedRegistration). completeVerifiedLogin
     * always treats a returning uid as a normal login and sets currentUser
     * unconditionally — the caller (ProHostViewModel's cold-start check /
     * AuthViewModel.finishPhoneVerification) detects the bare profile
     * afterward and calls this to put the app back in "signed out" state for
     * routing purposes, without touching the still-valid Firebase Auth
     * session itself (unlike [logout], this is not a real sign-out — the
     * caller is about to route into the registration form, which needs that
     * session to stay alive).
     */
    fun discardIncompleteSession() {
        with(repo) {
            _currentUser.value = null
        }
    }

    suspend fun updateCurrentUserProfile(
        name: String,
        specialty: String,
        phone: String,
        country: String,
        governorate: String,
        city: String,
        profilePictureUrl: String? = null
    ): Boolean {
        return with(repo) {
            val current = _currentUser.value ?: return false
            val updated = current.copy(
                fullName = name,
                specialty = specialty,
                phone = phone,
                country = country,
                governorate = governorate,
                city = city,
                profilePictureUrl = profilePictureUrl ?: current.profilePictureUrl
            )
            // A targeted write of only these fields — never role/isVerified/isSuspended/
            // ownerPackageId/etc. Echoing those back from the locally-cached AppUser
            // (the old approach) could disagree with the real server-stored value (e.g.
            // right after a role grant the local cache hasn't refreshed yet) and get the
            // *entire* write rejected by firestore.rules' protected-fields check, even
            // though the caller only meant to change their name.
            val success = firestoreService.updateUserProfileFields(
                current.id,
                mapOf(
                    "fullName" to updated.fullName,
                    "specialty" to updated.specialty,
                    "phone" to updated.phone,
                    "country" to updated.country,
                    "governorate" to updated.governorate,
                    "city" to updated.city,
                    "profilePictureUrl" to updated.profilePictureUrl
                )
            )
            if (success) {
                _currentUser.value = updated
                _users.value = _users.value.map { if (it.id == updated.id) updated else it }
            }
            return success
        }
    }

    /**
     * Persists the phone number to Firestore right after a successful KYC phone
     * link (FirebaseAuthService.linkPhoneCredentialToCurrentUser only links the
     * credential at the Firebase Auth level — it never touches Firestore). Without
     * this write, a Google/email user who completes phone KYC keeps a blank
     * user_profiles.phone forever, which both re-triggers the "needs KYC" gate
     * (ProHostNavGraph) and re-sends them to the registration form on their next
     * sign-in (AuthViewModel.finishVerification's stranded-account check).
     */
    suspend fun updatePhoneAfterKycLink(e164Phone: String): Boolean {
        return with(repo) {
            val current = _currentUser.value ?: return false
            val success = firestoreService.updateUserProfileFields(
                current.id,
                mapOf("phone" to e164Phone)
            )
            if (success) {
                val updated = current.copy(phone = e164Phone)
                _currentUser.value = updated
                _users.value = _users.value.map { if (it.id == updated.id) updated else it }
            }
            return success
        }
    }

    /**
     * Records the purchase token so the RTDN handler can look up this user by token
     * as a fallback. Role promotion and expiry are written exclusively by the server
     * (billing/playBillingRtdn.ts grantSubscription) using Play's canonical expiryTimeMillis —
     * never set from the client to avoid clock skew and protected-field rule rejections.
     */
    /**
     * Toggles [spaceId] in the current user's personal saved/favorites list. Not a
     * protected field — any signed-in user may freely write their own savedSpaceIds,
     * so a plain merge write of the recomputed list is enough (no rules change needed).
     */
    /**
     * Was fully non-optimistic (waited for the Firestore round-trip before ever
     * flipping currentUser), so every heart-icon tap had a visible delay before
     * it reflected — and both call sites (DiscoveryScreen, SpaceDetailsScreen)
     * discarded the returned Boolean entirely, so a write failure did nothing:
     * not even a revert of the local state that, in this old code, hadn't
     * changed yet anyway. Now flips currentUser immediately (this field is a
     * per-user preference list, not security/money-sensitive, so an optimistic
     * update carries no real risk) and reverts it if the write genuinely fails —
     * a real, visible signal instead of a silent no-op.
     */
    suspend fun toggleSavedSpace(spaceId: String): Boolean {
        return with(repo) {
            val current = _currentUser.value ?: return false
            val updatedIds = if (current.savedSpaceIds.contains(spaceId)) {
                current.savedSpaceIds - spaceId
            } else {
                current.savedSpaceIds + spaceId
            }
            val updated = current.copy(savedSpaceIds = updatedIds)
            _currentUser.value = updated
            _users.value = _users.value.map { if (it.id == updated.id) updated else it }

            val success = firestoreService.updateUserProfileFields(
                current.id,
                mapOf("savedSpaceIds" to updatedIds)
            )
            // Revert only if nothing has changed currentUser since this call made
            // its own optimistic write — comparing against [updated] (not
            // overwriting with [current] unconditionally) so a rapid second toggle
            // that already landed isn't clobbered by this call's late failure.
            if (!success && _currentUser.value == updated) {
                _currentUser.value = current
                _users.value = _users.value.map { if (it.id == current.id) current else it }
            }
            return success
        }
    }

    /** Registers this device's FCM token against [uid]'s profile — see FirestoreService.saveFcmToken. */
    suspend fun registerFcmToken(uid: String, token: String): Boolean =
        with(repo) { firestoreService.saveFcmToken(uid, token) }

    /** Address-only profile update (RequirementsSheet's hosting step). */
    suspend fun updateAddress(country: String, city: String): Boolean {
        return with(repo) {
            val current = _currentUser.value ?: return false
            val success = firestoreService.updateUserProfileFields(current.id, mapOf("country" to country, "city" to city))
            if (success) _currentUser.value = current.copy(country = country, city = city)
            return success
        }
    }

    /** Sets only the signed-in user's photo (RequirementsSheet). A targeted, rules-safe write. */
    suspend fun updateProfilePicture(profilePictureUrl: String): Boolean {
        return with(repo) {
            val current = _currentUser.value ?: return false
            val success = firestoreService.updateUserProfileFields(current.id, mapOf("profilePictureUrl" to profilePictureUrl))
            if (success) _currentUser.value = current.copy(profilePictureUrl = profilePictureUrl)
            return success
        }
    }
}
