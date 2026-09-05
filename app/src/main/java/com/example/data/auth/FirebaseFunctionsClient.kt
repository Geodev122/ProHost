package com.example.data.auth

import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around the server-verified role Cloud Functions
 * (functions/src/roles/*.ts). This — and reading the signed-in user's own ID
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
     * (PROFESSIONAL) server-side on first call. Safe/idempotent to call on
     * every sign-in.
     */
    suspend fun ensureInitialRole(): Result<String> {
        return try {
            val result = functions.getHttpsCallable("assignInitialRole").call().await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val role = data?.get("role") as? String
                ?: return Result.failure(IllegalStateException("assignInitialRole returned no role."))
            Result.success(role)
        } catch (e: Exception) {
            Log.e(tag, "ensureInitialRole failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** The only self-service role change available — SPACE_OWNER only, never ADMIN. */
    suspend fun requestSpaceOwnerUpgrade(): Result<String> {
        return try {
            val result = functions.getHttpsCallable("requestRoleUpgrade")
                .call(mapOf("targetRole" to "SPACE_OWNER"))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val role = data?.get("role") as? String
                ?: return Result.failure(IllegalStateException("requestRoleUpgrade returned no role."))
            Result.success(role)
        } catch (e: FirebaseFunctionsException) {
            Log.e(tag, "requestSpaceOwnerUpgrade denied: ${e.code} ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "requestSpaceOwnerUpgrade failed: ${e.message}", e)
            Result.failure(e)
        }
    }

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

    data class WhishPaymentInit(val collectUrl: String, val txId: String, val orderId: String)

    /**
     * Starts a Whish payment (functions/src/payments/initiateWhishPayment.ts). The
     * server looks up the real amount itself from [purpose]/[targetId] — this call
     * never sends an amount, and the client can't influence what gets charged.
     *
     * @param purpose one of SUBSCRIPTION, OWNER_PACKAGE, PAYG_LISTING, BOOKING
     * @param targetId spaceId / OwnerPackageTier name / SpaceType name / bookingId, matching [purpose]
     */
    suspend fun initiateWhishPayment(
        purpose: String,
        targetId: String,
        payerName: String,
        payerPhone: String
    ): Result<WhishPaymentInit> {
        return try {
            val result = functions.getHttpsCallable("initiateWhishPayment")
                .call(
                    mapOf(
                        "purpose" to purpose,
                        "targetId" to targetId,
                        "payerName" to payerName,
                        "payerPhone" to payerPhone
                    )
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

    /** Self-service: ask for the caller's own uploaded credential documents to be reviewed. */
    suspend fun submitVerificationForReview(): Result<Unit> {
        return try {
            functions.getHttpsCallable("submitVerificationForReview").call().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "submitVerificationForReview failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Admin-only: approve or reject a credential document (functions/src/admin/verification.ts). */
    suspend fun reviewCredentialDocument(
        documentId: String,
        approve: Boolean,
        reviewerNotes: String? = null,
        rejectionReason: String? = null
    ): Result<Unit> {
        return try {
            val payload = mutableMapOf<String, Any>(
                "documentId" to documentId,
                "decision" to if (approve) "APPROVE" else "REJECT"
            )
            reviewerNotes?.let { payload["reviewerNotes"] = it }
            rejectionReason?.let { payload["rejectionReason"] = it }
            functions.getHttpsCallable("reviewCredentialDocument").call(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "reviewCredentialDocument failed: ${e.message}", e)
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
