package com.example.data.gateway

import com.example.util.resumeOnce
import com.example.data.auth.AccountSuspendedException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** The answer to one callable: [data] on success, else the Firebase error code name (e.g. `PERMISSION_DENIED`) and its message. */
data class CallableReply(val data: Map<String, Any?>?, val errorCode: String?, val errorMessage: String?)

/**
 * Runs one Cloud Functions callable (region europe-west1) on the platform's Firebase SDK. A plain callback, so a Swift class
 * can implement it directly (iOS: `FirebaseCallableTransport.swift`). [timeoutSeconds] 0 = the SDK default.
 */
interface CallableTransport {
    fun call(name: String, data: Map<String, Any?>?, timeoutSeconds: Int, completion: (CallableReply) -> Unit)
}

/** A callable failed; [code] is the Firebase error code name. */
class CallableException(val code: String, message: String) : Exception(message)

/**
 * [FunctionsGateway] over a [CallableTransport], with exactly the payloads and result reading of the Android client
 * (`app/.../data/auth/FirebaseFunctionsClient.kt`; `CallableFunctionsGatewayTest` keeps both identical). Used by iOS.
 */
class CallableFunctionsGateway(private val transport: CallableTransport) : FunctionsGateway {

    private suspend fun call(name: String, data: Map<String, Any?>?, timeoutSeconds: Int = 0): Map<String, Any?> =
        suspendCancellableCoroutine { cont ->
            transport.call(name, data, timeoutSeconds) { reply -> cont.resumeOnce(reply) }
        }.let { reply ->
            if (reply.errorCode != null) throw CallableException(reply.errorCode, reply.errorMessage ?: "")
            reply.data ?: emptyMap()
        }

    private suspend fun unit(name: String, data: Map<String, Any?>?, timeoutSeconds: Int = 0): Result<Unit> =
        runCatching { call(name, data, timeoutSeconds); Unit }

    override suspend fun ensureInitialRole(registrationDraft: Map<String, Any?>?, integrityToken: String?): Result<String> = try {
        val payload: Map<String, Any?>? =
            if (registrationDraft != null || integrityToken != null) mapOf("registration" to registrationDraft, "integrityToken" to integrityToken)
            else null
        val role = call("assignInitialRole", payload)["role"] as? String
        if (role == null) Result.failure(IllegalStateException("assignInitialRole returned no role.")) else Result.success(role)
    } catch (e: CallableException) {
        val isSuspension = e.code == "PERMISSION_DENIED" && e.message?.contains("suspended", ignoreCase = true) == true
        Result.failure(if (isSuspension) AccountSuspendedException(e.message ?: "This account has been suspended.") else e)
    }

    override suspend fun recordAuditLog(actionType: String, details: String, severity: String): Result<Unit> =
        unit("recordClientAuditLog", mapOf("actionType" to actionType, "details" to details, "severity" to severity))

    override suspend fun verifyAndRestorePurchase(purchaseToken: String, productId: String, fromCheckout: Boolean): Result<Long> = runCatching {
        val data = call("verifyAndRestorePurchase", mapOf("purchaseToken" to purchaseToken, "productId" to productId, "fromCheckout" to fromCheckout))
        (data["expiryMillis"] as? Number)?.toLong() ?: throw IllegalStateException("verifyAndRestorePurchase returned no expiryMillis.")
    }

    override suspend fun verifyAppleTransaction(signedTransaction: String, signedRenewalInfo: String?): Result<Boolean> = runCatching {
        val payload = buildMap<String, Any?> {
            put("signedTransaction", signedTransaction)
            if (!signedRenewalInfo.isNullOrBlank()) put("signedRenewalInfo", signedRenewalInfo)
        }
        call("verifyAppleTransaction", payload)["hasAccess"] == true
    }

    override suspend fun sendPaymentReminder(bookingId: String): Result<Unit> = unit("sendPaymentReminder", mapOf("bookingId" to bookingId))

    override suspend fun changeSlotPrice(
        spaceId: String, scopeId: String, kind: String, key: String, newPrice: Double, decisions: Map<String, String>
    ): Result<SlotPriceChangeResult> = runCatching {
        val data = call(
            "changeSlotPrice",
            mapOf(
                "spaceId" to spaceId,
                "ref" to mapOf("scopeId" to scopeId, "kind" to kind, "key" to key),
                "newPrice" to newPrice,
                "decisions" to decisions
            )
        )
        fun count(k: String) = (data[k] as? Number)?.toInt() ?: 0
        SlotPriceChangeResult(count("affected"), count("now"), count("nextTerm"), count("keep"))
    }

    override suspend fun requestListingVerification(spaceId: String): Result<Unit> =
        unit("requestListingVerification", mapOf("spaceId" to spaceId))

    override suspend fun updatePricing(fields: Map<String, Any>): Result<Unit> = unit("updatePricing", fields)

    override suspend fun setListingVerification(spaceId: String, verified: Boolean): Result<Unit> =
        unit("setListingVerification", mapOf("spaceId" to spaceId, "verified" to verified))

    override suspend fun setListingSubscriptionActive(spaceId: String, active: Boolean): Result<Unit> =
        unit("setListingSubscriptionActive", mapOf("spaceId" to spaceId, "active" to active))

    override suspend fun reportContent(payload: Map<String, Any?>): Result<Unit> = unit("reportContent", payload)

    override suspend fun blockUser(targetUid: String?, listingId: String?): Result<Unit> =
        unit("blockUser", mapOf("targetUid" to targetUid, "listingId" to listingId).filterValues { it != null })

    override suspend fun unblockUser(targetUid: String): Result<Unit> = unit("unblockUser", mapOf("targetUid" to targetUid))

    // Cleanup of listings and files can take a while (same 180 s as Android).
    override suspend fun deleteOwnAccount(): Result<Unit> = unit("deleteOwnAccount", null, timeoutSeconds = 180)

    override suspend fun sendPasswordResetLink(email: String): Result<Unit> = unit("sendPasswordResetLink", mapOf("email" to email))

    override suspend fun sendVerificationEmailLink(): Result<Unit> = unit("sendVerificationEmailLink", null)
}
