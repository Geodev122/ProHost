package com.example.data.auth

import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around the server-verified role Cloud Functions
 * (functions/src/roles/ ts files). This — and reading the signed-in user's own ID
 * token claims — is the ONLY way this app ever learns or changes a user's
 * role. Nothing here accepts a role as a trusted client-side value.
 */
open class FirebaseFunctionsClient {

    private val tag = "FirebaseFunctionsClient"

    // Must match the region functions are deployed to (see firebase.json /
    // functions/src/index.ts setGlobalOptions).
    private val functions: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance("europe-west1")
    }

    /**
     * Ensures the signed-in user has a role claim, assigning the default
     * (SPECIALIST) server-side on first call. Safe/idempotent to call on
     * every sign-in.
     *
     * [registrationDraft], when non-null, is validated server-side (format only —
     * non-blank/reasonable-length name, a real-looking email, a genuine uploaded
     * Storage URL for the ID document if one is present) before this call
     * succeeds — see assignInitialRole.ts. Pass it only when completing a
     * brand-new registration; a plain sign-in never has one.
     *
     * [integrityToken], when present, is verified server-side against Google Play
     * Integrity in a log-only capacity (see assignInitialRole.ts) — a missing or
     * failed verdict never blocks this call from succeeding.
     */
    suspend fun ensureInitialRole(registrationDraft: Map<String, Any?>? = null, integrityToken: String? = null): Result<String> {
        return try {
            val payload: Map<String, Any?>? = if (registrationDraft != null || integrityToken != null) {
                mapOf("registration" to registrationDraft, "integrityToken" to integrityToken)
            } else {
                null
            }
            val result = functions.getHttpsCallable("assignInitialRole").call(payload).await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val role = data?.get("role") as? String
                ?: return Result.failure(IllegalStateException("assignInitialRole returned no role."))
            Result.success(role)
        } catch (e: Exception) {
            Log.e(tag, "ensureInitialRole failed: ${e.message}", e)
            // assignInitialRole.ts throws exactly this permission-denied shape for one
            // reason only — the account is suspended (setAccountSuspended.ts) — so this
            // is distinguished from any other failure and wrapped for callers to catch
            // specifically (see AuthFlow.resolveVerifiedRole).
            val isSuspension = e is com.google.firebase.functions.FirebaseFunctionsException &&
                e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.PERMISSION_DENIED &&
                e.message?.contains("suspended", ignoreCase = true) == true
            Result.failure(
                if (isSuspension) com.example.data.auth.AccountSuspendedException(e.message ?: "This account has been suspended.")
                else e
            )
        }
    }

    // The free self-service requestSpaceOwnerUpgrade() path (backed by the
    // requestRoleUpgrade Cloud Function) is gone: PRO_HOST is no longer a role
    // anyone can just ask for. It's granted exclusively, server-side, by
    // functions/src/lib/entitlements.ts's grantEntitlement() the moment a
    // SPECIALIST's Google Play subscription activates or an admin grants access
    // — see ProHostRepository.refreshCurrentUserAfterEntitlement(), called
    // once client-side polling observes that success.

    /** Only succeeds when the CALLER already has the Admin role server-side. */
    suspend fun grantAdminRole(targetEmail: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("grantAdminRole")
                .call(mapOf("targetEmail" to targetEmail))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "grantAdminRole failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Admin-only: suspends or reactivates an account (functions/src/roles/setAccountSuspended.ts)
     * — the state between "exists" and "deleted." An Admin account can never be
     * suspended through this path (the server rejects it).
     */
    suspend fun setAccountSuspended(targetUid: String, suspended: Boolean): Result<Unit> {
        return try {
            functions.getHttpsCallable("setAccountSuspended")
                .call(mapOf("targetUid" to targetUid, "suspended" to suspended))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "setAccountSuspended failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** functions/src/admin/grantPackage.ts — Admin-only: grants a package plan to a user. */
    /** functions/src/admin/lookupUserForGrant.ts — Admin-only identity check before a grant. */
    suspend fun lookupUserForGrant(email: String): Result<GrantLookupResult> {
        return try {
            val result = functions.getHttpsCallable("lookupUserForGrant")
                .call(mapOf("email" to email.trim()))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
                ?: return Result.failure(IllegalStateException("Empty lookup response"))
            Result.success(
                GrantLookupResult(
                    uid = data["uid"] as? String ?: return Result.failure(IllegalStateException("Lookup returned no UID")),
                    email = data["email"] as? String ?: email,
                    fullName = data["fullName"] as? String ?: "",
                    role = data["role"] as? String ?: "SPECIALIST",
                    hasProfile = data["hasProfile"] as? Boolean ?: false,
                    isSuspended = data["isSuspended"] as? Boolean ?: false,
                    isDisabled = data["isDisabled"] as? Boolean ?: false,
                    ownerPackageId = data["ownerPackageId"] as? String,
                    ownerPackageExpiryMillis = (data["ownerPackageExpiryMillis"] as? Number)?.toLong(),
                    activeListingCount = (data["activeListingCount"] as? Number)?.toInt() ?: 0,
                    displayCode = data["displayCode"] as? String ?: ""
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "lookupUserForGrant failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * functions/src/admin/forceProHostUpgrade.ts — Admin "Force Upgrade → ProHost" for the
     * UID verified via [lookupUserForGrant]. Permanent until revoked (revokeProHostRole).
     */
    suspend fun forceProHostUpgrade(targetUid: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("forceProHostUpgrade").call(mapOf("targetUid" to targetUid)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "forceProHostUpgrade failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** functions/src/billing/billingSyncJob.ts — Admin: re-sync Google Play subscriptions now. */
    suspend fun runBillingSync(): Result<Map<String, Any?>> {
        return try {
            val result = functions.getHttpsCallable("runBillingSync").call(emptyMap<String, Any>()).await()
            @Suppress("UNCHECKED_CAST")
            Result.success(result.data as? Map<String, Any?> ?: emptyMap())
        } catch (e: Exception) {
            Log.e(tag, "runBillingSync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** functions/src/admin/adminAnalytics.ts — Admin-only platform analytics. */
    suspend fun getAdminAnalytics(fromMillis: Long?, toMillis: Long?, country: String?): Result<AdminAnalytics> {
        return try {
            val payload = mutableMapOf<String, Any>()
            fromMillis?.let { payload["fromMillis"] = it }
            toMillis?.let { payload["toMillis"] = it }
            country?.takeIf { it.isNotBlank() }?.let { payload["country"] = it }
            val result = functions.getHttpsCallable("getAdminAnalytics").call(payload).await()
            val data = result.data as? Map<*, *> ?: return Result.failure(IllegalStateException("Empty analytics response"))
            fun counts(key: String): Map<String, Int> =
                (data[key] as? Map<*, *>)?.entries?.mapNotNull { (k, v) ->
                    val name = k as? String ?: return@mapNotNull null
                    val n = (v as? Number)?.toInt() ?: return@mapNotNull null
                    name to n
                }?.toMap() ?: emptyMap()
            Result.success(
                AdminAnalytics(
                    upgrades = (data["upgrades"] as? List<*>)?.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        val day = (m["dayMillis"] as? Number)?.toLong() ?: return@mapNotNull null
                        day to ((m["count"] as? Number)?.toInt() ?: 0)
                    } ?: emptyList(),
                    upgradesTotal = (data["upgradesTotal"] as? Number)?.toInt() ?: 0,
                    proHostsWithoutDate = (data["proHostsWithoutDate"] as? Number)?.toInt() ?: 0,
                    listingsTotal = (data["listingsTotal"] as? Number)?.toInt() ?: 0,
                    undatedListings = (data["undatedListings"] as? Number)?.toInt() ?: 0,
                    byCountry = counts("byCountry"),
                    byCity = counts("byCity"),
                    bySpaceType = counts("bySpaceType"),
                    divisionsBySpaceType = (data["divisionsBySpaceType"] as? Map<*, *>)?.entries?.mapNotNull { (k, v) ->
                        val type = k as? String ?: return@mapNotNull null
                        type to ((v as? Map<*, *>)?.entries?.mapNotNull { (dk, dv) ->
                            val name = dk as? String ?: return@mapNotNull null
                            name to ((dv as? Number)?.toInt() ?: 0)
                        }?.toMap() ?: emptyMap())
                    }?.toMap() ?: emptyMap(),
                    countries = (data["countries"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    bookingMix = (data["bookingMix"] as? List<*>)?.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        BookingMixRow(
                            key = m["key"] as? String ?: return@mapNotNull null,
                            label = m["label"] as? String ?: "",
                            requests = (m["requests"] as? Number)?.toInt() ?: 0,
                            accepted = (m["accepted"] as? Number)?.toInt() ?: 0,
                            acceptedRevenueUsd = (m["acceptedRevenueUsd"] as? Number)?.toDouble() ?: 0.0
                        )
                    } ?: emptyList(),
                    attendeeStats = (data["attendeeStats"] as? Map<*, *>)?.let { m ->
                        AttendeeBookingStats(
                            requests = (m["requests"] as? Number)?.toInt() ?: 0,
                            accepted = (m["accepted"] as? Number)?.toInt() ?: 0,
                            acceptedAttendees = (m["acceptedAttendees"] as? Number)?.toInt() ?: 0,
                            avgGroupSize = (m["avgGroupSize"] as? Number)?.toDouble() ?: 0.0
                        )
                    } ?: AttendeeBookingStats()
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "getAdminAnalytics failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** functions/src/admin/adminAnalytics.ts — fills missing Pro Host upgrade dates from the audit log. */
    /** Admin-only: functions/src/ids/displayCodes.ts — codes for docs created before codes existed. */
    suspend fun backfillDisplayCodes(): Result<Map<String, Int>> {
        return try {
            val result = functions.getHttpsCallable("backfillDisplayCodes").call(emptyMap<String, Any>()).await()
            val data = result.data as? Map<*, *> ?: emptyMap<String, Any>()
            Result.success(
                listOf("users", "listings", "bookings").associateWith { (data[it] as? Number)?.toInt() ?: 0 }
            )
        } catch (e: Exception) {
            Log.e(tag, "backfillDisplayCodes failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun backfillProHostUpgradeDates(): Result<Pair<Int, Int>> {
        return try {
            val result = functions.getHttpsCallable("backfillProHostUpgradeDates").call(emptyMap<String, Any>()).await()
            val data = result.data as? Map<*, *> ?: emptyMap<String, Any>()
            Result.success(
                ((data["updated"] as? Number)?.toInt() ?: 0) to ((data["stillMissing"] as? Number)?.toInt() ?: 0)
            )
        } catch (e: Exception) {
            Log.e(tag, "backfillProHostUpgradeDates failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- Admin directory (functions/src/admin/adminDirectory.ts): the console never loads
    // whole collections; it asks the server for counts, search results and one dossier. ---

    @Suppress("UNCHECKED_CAST")
    private suspend fun callAdmin(name: String, payload: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val result = functions.getHttpsCallable(name).call(payload).await()
        return result.data as? Map<String, Any?> ?: emptyMap()
    }

    suspend fun adminCounts(): Result<AdminCounts> = runCatching {
        AdminCounts.fromMap(callAdmin("adminCounts"))
    }.onFailure { Log.e(tag, "adminCounts failed: ${it.message}", it) }

    /** [kind]: "users", "listings", "bookings" or "all". */
    suspend fun adminSearch(query: String, kind: String): Result<AdminSearchResult> = runCatching {
        AdminSearchResult.fromMap(callAdmin("adminSearch", mapOf("query" to query.trim(), "kind" to kind)))
    }.onFailure { Log.e(tag, "adminSearch failed: ${it.message}", it) }

    suspend fun adminUserDossier(uid: String): Result<AdminUserDossier> = runCatching {
        AdminUserDossier.fromMap(callAdmin("adminUserDossier", mapOf("uid" to uid)))
            ?: throw IllegalStateException("Empty dossier response")
    }.onFailure { Log.e(tag, "adminUserDossier failed: ${it.message}", it) }

    /** One page (500 users) of the server-side user export; pass the returned cursor back. */
    suspend fun adminExportUsers(cursor: String?): Result<AdminExportPage> = runCatching {
        AdminExportPage.fromMap(callAdmin("adminExportUsers", mapOf("cursor" to cursor)))
    }.onFailure { Log.e(tag, "adminExportUsers failed: ${it.message}", it) }

    /** Admin maintenance: one-time backfills. Returns the server's summary map. */
    suspend fun runAdminMaintenance(callable: String): Result<Map<String, Any?>> = runCatching {
        callAdmin(callable)
    }.onFailure { Log.e(tag, "$callable failed: ${it.message}", it) }

    /** functions/src/roles/revokeProHostRole.ts — Admin-only downgrade to SPECIALIST. */
    suspend fun revokeProHostRole(targetUid: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("revokeProHostRole")
                .call(mapOf("targetUid" to targetUid))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "revokeProHostRole failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Routine audit log entry (functions/src/audit/recordClientAuditLog.ts) — the
     * only remaining path for non-admin audit events now that audit_security_logs
     * denies every direct client write. actorEmail is never accepted from the
     * client; the server always uses the caller's own verified token email.
     */
    open suspend fun recordAuditLog(actionType: String, details: String, severity: String = "INFO"): Result<Unit> {
        return try {
            functions.getHttpsCallable("recordClientAuditLog")
                .call(mapOf("actionType" to actionType, "details" to details, "severity" to severity))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "recordAuditLog failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Admin-only pricing patch (functions/src/admin/pricing.ts) — the only path
     * that can write system_metadata/pricing now that Phase 7's Firestore rules
     * deny every client write to it. Pass only the fields being changed.
     */
    suspend fun updatePricing(fields: Map<String, Any>): Result<Unit> {
        return try {
            functions.getHttpsCallable("updatePricing").call(fields).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "updatePricing failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // submitVerificationForReview()/reviewCredentialDocument()/setUserVerification()
    // used to live here — the whole admin-reviewed accreditation system they backed
    // (functions/src/admin/verification.ts) is gone. isVerified now means only "this
    // account's phone number passed Firebase Phone Auth SMS verification," synced
    // automatically by assignInitialRole.ts from the ID token's own phone_number
    // claim — there is nothing left for a human to submit, approve, or override.

    /**
     * Verifies a Google Play purchase token server-side and restores the Pro Host
     * entitlement (functions/src/billing/verifyAndRestorePurchase.ts). Called when
     * the client detects an active Play subscription with no matching Firestore
     * entitlement — e.g. because the original RTDN Pub/Sub delivery was dropped.
     *
     * Returns the verified expiry timestamp (ms) on success.
     */
    suspend fun verifyAndRestorePurchase(purchaseToken: String, productId: String, fromCheckout: Boolean = false): Result<Long> {
        return try {
            // The server reads the base plan from Google; the app never states what it bought.
            val result = functions.getHttpsCallable("verifyAndRestorePurchase")
                .call(mapOf("purchaseToken" to purchaseToken, "productId" to productId, "fromCheckout" to fromCheckout))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val expiryMillis = (data?.get("expiryMillis") as? Number)?.toLong()
                ?: return Result.failure(IllegalStateException("verifyAndRestorePurchase returned no expiryMillis."))
            Result.success(expiryMillis)
        } catch (e: Exception) {
            Log.e(tag, "verifyAndRestorePurchase failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a real cross-device push reminding the specialist to settle payment for
     * an accepted booking (functions/src/notifications/sendPaymentReminder.ts) — the
     * server verifies the caller actually owns the booking's space before sending.
     */
    suspend fun sendPaymentReminder(bookingId: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("sendPaymentReminder")
                .call(mapOf("bookingId" to bookingId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "sendPaymentReminder failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Admin-only: override a listing's verified badge (functions/src/admin/listings.ts). */
    open suspend fun setListingVerification(spaceId: String, verified: Boolean): Result<Unit> {
        return try {
            functions.getHttpsCallable("setListingVerification")
                .call(mapOf("spaceId" to spaceId, "verified" to verified))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "setListingVerification failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Admin-only: override a listing's subscription-active flag (functions/src/admin/listings.ts). */
    suspend fun setListingSubscriptionActive(spaceId: String, active: Boolean): Result<Unit> {
        return try {
            functions.getHttpsCallable("setListingSubscriptionActive")
                .call(mapOf("spaceId" to spaceId, "active" to active))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "setListingSubscriptionActive failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Host-callable self-service verification (functions/src/admin/listings.ts) —
     * the listing's own owner earns the Listing Verified badge once
     * SpaceListing.verificationDocUrl is on file. Fails with a
     * "failed-precondition" HttpsError (surfaced via the caught exception's
     * message) if no qualifying document has been uploaded yet.
     */
    suspend fun requestListingVerification(spaceId: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("requestListingVerification")
                .call(mapOf("spaceId" to spaceId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "requestListingVerification failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Self-service account deletion (functions/src/roles/deleteOwnAccount.ts) —
     * always targets the CALLER's own account server-side; this call never takes
     * a target uid. Purges the caller's owned listings, ID/profile-picture
     * uploads, Firestore profile, and finally their Firebase Auth account itself.
     */
    suspend fun deleteOwnAccount(): Result<Unit> {
        return try {
            functions.getHttpsCallable("deleteOwnAccount").call().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "deleteOwnAccount failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Generates a Firebase Auth sign-in link server-side and emails it to [email]
     * (functions/src/auth/emailLinkAuth.ts). Replaces sendEmailOtp for new sign-ins.
     * The client completes sign-in via signInWithEmailLink after tapping the link.
     */
    suspend fun sendSignInEmailLink(email: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("sendSignInEmailLink").call(mapOf("email" to email)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "sendSignInEmailLink failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Send a 6-digit email OTP to [email] (functions/src/auth/emailOtp.ts). */
    suspend fun sendEmailOtp(email: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("sendEmailOtp").call(mapOf("email" to email)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "sendEmailOtp failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Verifies the OTP code the user entered and returns a Firebase custom token
     * (functions/src/auth/emailOtp.ts). Sign in with the returned token via
     * [FirebaseAuthService.signInWithCustomToken].
     */
    suspend fun verifyEmailOtp(email: String, code: String): Result<String> {
        return try {
            val result = functions.getHttpsCallable("verifyEmailOtp")
                .call(mapOf("email" to email, "code" to code))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val token = data?.get("customToken") as? String
                ?: return Result.failure(IllegalStateException("verifyEmailOtp returned no token."))
            Result.success(token)
        } catch (e: Exception) {
            Log.e(tag, "verifyEmailOtp failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a Firebase Auth email verification link (functions/src/auth/emailLinkAuth.ts).
     * The user taps the link in their email app → Firebase Auth marks emailVerified=true
     * automatically — no OTP code entry or extra server round-trip needed.
     *
     * Replaces the legacy resendEmailVerification (HMAC-JWT + verifyEmailLink) pipeline.
     */
    suspend fun sendVerificationEmailLink(): Result<Unit> {
        return try {
            functions.getHttpsCallable("sendVerificationEmailLink").call(null).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "sendVerificationEmailLink failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** @deprecated Use [sendVerificationEmailLink] instead (Firebase Auth native link). */
    suspend fun resendEmailVerification(): Result<Unit> = sendVerificationEmailLink()

    companion object {
        /**
         * Reads the role custom claim from the given user's current ID token,
         * force-refreshing so a just-granted claim is picked up immediately
         * rather than waiting for the token's normal ~1 hour cache window.
         */
        suspend fun readRoleClaim(user: FirebaseUser, forceRefresh: Boolean = true): String? {
            val tokenResult = user.getIdToken(forceRefresh).await()
            return tokenResult.claims["role"] as? String
        }
    }
}

data class GrantLookupResult(
    val uid: String,
    val email: String,
    val fullName: String,
    val role: String,
    val hasProfile: Boolean,
    val isSuspended: Boolean,
    val isDisabled: Boolean,
    val ownerPackageId: String?,
    val ownerPackageExpiryMillis: Long?,
    val activeListingCount: Int,
    val displayCode: String = ""
)

data class GrantResult(
    val role: String,
    val packageId: String,
    val expiryMillis: Long?,
    val restoredListings: Int
)

data class AdminAnalytics(
    /** (UTC day start millis, upgrades that day), ascending. */
    val upgrades: List<Pair<Long, Int>>,
    val upgradesTotal: Int,
    val proHostsWithoutDate: Int,
    val listingsTotal: Int,
    val undatedListings: Int,
    /** Empty when a single country is selected. */
    val byCountry: Map<String, Int>,
    val byCity: Map<String, Int>,
    val bySpaceType: Map<String, Int>,
    val divisionsBySpaceType: Map<String, Map<String, Int>>,
    val countries: List<String>,
    /** Booking requests in range by renting strategy; "PER_ATTENDEE" is its own row. */
    val bookingMix: List<BookingMixRow> = emptyList(),
    val attendeeStats: AttendeeBookingStats = AttendeeBookingStats()
)

data class BookingMixRow(
    val key: String,
    val label: String,
    val requests: Int,
    val accepted: Int,
    val acceptedRevenueUsd: Double
)

data class AttendeeBookingStats(
    val requests: Int = 0,
    val accepted: Int = 0,
    val acceptedAttendees: Int = 0,
    val avgGroupSize: Double = 0.0
)
