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
class FirebaseFunctionsClient {

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
    // SPECIALIST's OWNER_PACKAGE or PAYG_LISTING Whish payment actually settles
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

    data class WhishPaymentInit(val collectUrl: String, val txId: String, val orderId: String)

    /**
     * Starts a Whish payment (functions/src/payments/initiateWhishPayment.ts). The
     * server looks up the real amount itself from [purpose]/[targetId] — this call
     * never sends an amount, and the client can't influence what gets charged.
     *
     * @param purpose always OWNER_PACKAGE
     * @param targetId the PackagePlan id being purchased/renewed
     * @param draftListingId set only when this payment is resolving a package-limit
     *  block that CreateListingDialog's Publish hit — the specific Draft to
     *  auto-publish once this settles (see entitlements.ts's autoPublishDraftIfNeeded)
     */
    suspend fun initiateWhishPayment(
        purpose: String,
        targetId: String? = null,
        payerName: String,
        payerPhone: String,
        draftListingId: String? = null
    ): Result<WhishPaymentInit> {
        return try {
            val result = functions.getHttpsCallable("initiateWhishPayment")
                .call(
                    buildMap {
                        put("purpose", purpose)
                        if (targetId != null) put("targetId", targetId)
                        put("payerName", payerName)
                        put("payerPhone", payerPhone)
                        if (draftListingId != null) put("draftListingId", draftListingId)
                    }
                )
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val collectUrl = data?.get("collectUrl") as? String
            val txId = data?.get("txId") as? String
            val orderId = data?.get("orderId") as? String
            if (collectUrl == null || txId == null || orderId == null) {
                return Result.failure(IllegalStateException("initiateWhishPayment returned an incomplete response."))
            }
            Result.success(WhishPaymentInit(collectUrl, txId, orderId))
        } catch (e: Exception) {
            Log.e(tag, "initiateWhishPayment failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Asks the server to independently re-check payment status with Whish
     * (functions/src/payments/checkWhishStatus.ts) and grant the entitlement if it
     * just succeeded. Returns "PENDING", "SUCCESS", or "FAILED".
     */
    suspend fun checkWhishStatus(txId: String): Result<String> {
        return try {
            val result = functions.getHttpsCallable("checkWhishStatus")
                .call(mapOf("txId" to txId))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val status = data?.get("status") as? String
                ?: return Result.failure(IllegalStateException("checkWhishStatus returned no status."))
            Result.success(status)
        } catch (e: Exception) {
            Log.e(tag, "checkWhishStatus failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Routine audit log entry (functions/src/audit/recordClientAuditLog.ts) — the
     * only remaining path for non-admin audit events now that audit_security_logs
     * denies every direct client write. actorEmail is never accepted from the
     * client; the server always uses the caller's own verified token email.
     */
    suspend fun recordAuditLog(actionType: String, details: String, severity: String = "INFO"): Result<Unit> {
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
    suspend fun setListingVerification(spaceId: String, verified: Boolean): Result<Unit> {
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

    // ----- PIN Auth (functions/src/auth/pinAuth.ts) -----

    data class PhoneCheckResult(val isRegistered: Boolean, val hasPinSet: Boolean)

    /**
     * Checks whether a phone number is registered and has a PIN. Used by
     * LoginAuthScreen to decide which path to show: PIN entry for returning users,
     * OTP for new signups or accounts without a PIN yet.
     */
    suspend fun checkPhoneRegistered(phone: String): Result<PhoneCheckResult> {
        return try {
            val result = functions.getHttpsCallable("checkPhoneRegistered")
                .call(mapOf("phone" to phone))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            Result.success(
                PhoneCheckResult(
                    isRegistered = data?.get("isRegistered") as? Boolean ?: false,
                    hasPinSet = data?.get("hasPinSet") as? Boolean ?: false
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "checkPhoneRegistered failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Verifies [pin] against the server-stored hash for [phone] and returns a
     * Firebase custom auth token. The caller immediately calls
     * FirebaseAuthService.signInWithCustomToken() with the returned token.
     */
    suspend fun verifyPinAndIssueToken(phone: String, pin: String): Result<String> {
        return try {
            val result = functions.getHttpsCallable("verifyPinAndIssueToken")
                .call(mapOf("phone" to phone, "pin" to pin))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val token = data?.get("token") as? String
                ?: return Result.failure(IllegalStateException("verifyPinAndIssueToken returned no token."))
            Result.success(token)
        } catch (e: Exception) {
            Log.e(tag, "verifyPinAndIssueToken failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Stores a hashed PIN for the authenticated caller (functions/src/auth/pinAuth.ts).
     * Called after OTP sign-in during initial signup and after OTP reset during
     * forgot-PIN. Requires an active Firebase Auth session.
     */
    suspend fun setUserPin(pin: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("setUserPin").call(mapOf("pin" to pin)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "setUserPin failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Submit the caller's own ID document for admin review (functions/src/users/submitIdDocument.ts). */
    suspend fun submitIdDocument(storageUrl: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("submitIdDocument")
                .call(mapOf("storageUrl" to storageUrl))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "submitIdDocument failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Admin-only: approve or reject an ID document submission (functions/src/users/reviewIdDocument.ts). */
    suspend fun reviewIdDocument(userId: String, decision: String, reason: String? = null): Result<Unit> {
        return try {
            val payload = buildMap<String, Any?> {
                put("userId", userId)
                put("decision", decision)
                if (reason != null) put("reason", reason)
            }
            functions.getHttpsCallable("reviewIdDocument").call(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "reviewIdDocument failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Request a new email verification link (functions/src/auth/emailVerification.ts). */
    suspend fun resendEmailVerification(): Result<Unit> {
        return try {
            functions.getHttpsCallable("resendEmailVerification").call(null).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "resendEmailVerification failed: ${e.message}", e)
            Result.failure(e)
        }
    }

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
