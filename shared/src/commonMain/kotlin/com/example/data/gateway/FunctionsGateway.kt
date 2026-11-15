package com.example.data.gateway

/**
 * The app's Cloud Functions calls (admin calls excluded: those live in the web console). Android implements it with
 * `FirebaseFunctionsClient` (Firebase Android SDK); iOS gets a Swift-backed implementation. Payloads and callable names are the
 * contract with `functions/src/index.ts` and must stay byte-identical across platforms.
 */
interface FunctionsGateway {
    /** assignInitialRole: returns the role the server granted. */
    suspend fun ensureInitialRole(registrationDraft: Map<String, Any?>? = null, integrityToken: String? = null): Result<String>
    suspend fun recordAuditLog(actionType: String, details: String, severity: String = "INFO"): Result<Unit>
    /** Google Play purchase → server verification; returns the plan expiry (millis). */
    suspend fun verifyAndRestorePurchase(purchaseToken: String, productId: String, fromCheckout: Boolean = false): Result<Long>
    suspend fun sendPaymentReminder(bookingId: String): Result<Unit>
    suspend fun changeSlotPrice(
        spaceId: String, scopeId: String, kind: String, key: String, newPrice: Double, decisions: Map<String, String>
    ): Result<SlotPriceChangeResult>
    suspend fun requestListingVerification(spaceId: String): Result<Unit>
    suspend fun reportContent(payload: Map<String, Any?>): Result<Unit>
    suspend fun blockUser(targetUid: String?, listingId: String?): Result<Unit>
    suspend fun unblockUser(targetUid: String): Result<Unit>
    suspend fun deleteOwnAccount(): Result<Unit>
    suspend fun sendPasswordResetLink(email: String): Result<Unit>
    suspend fun sendVerificationEmailLink(): Result<Unit>
}

/** What changeSlotPrice did. */
data class SlotPriceChangeResult(val affected: Int, val now: Int, val nextTerm: Int, val keep: Int)
