package com.example.ios

/** One StoreKit 2 product (prices in micros, ISO periods "P1M"/"P1Y"); [introPeriod] is "" when this account has no intro offer. */
class StoreProductReply(
    val productId: String,
    val productDescription: String,
    val priceMicros: Long,
    val currency: String,
    val formattedPrice: String,
    val period: String,
    val introPriceMicros: Long,
    val introFormattedPrice: String,
    val introPeriod: String,
    val introCycles: Int,
)

/** A verified StoreKit 2 transaction: its JWS, the renewal info JWS ("" when StoreKit has none) and auto-renew state. */
class StoreTransactionReply(
    val productId: String,
    val signedTransaction: String,
    val signedRenewalInfo: String,
    val autoRenews: Boolean,
)

/** Outcome of a purchase: 0 purchased ([transaction] set), 1 cancelled, 2 pending (Ask to Buy, SCA), 3 failed ([error]). */
class StorePurchaseReply(val outcome: Int, val transaction: StoreTransactionReply?, val error: String)

/**
 * StoreKit 2, implemented in Swift (`StoreKitBridgeImpl.swift`), installed by `ProHostAppDelegate`. Callbacks only (StoreKit
 * is Swift async); method names are unique across the exported bridges. The app never grants anything: every transaction
 * goes to the `verifyAppleTransaction` callable, which checks Apple's signature and applies it ([IosAppBilling]).
 */
interface StoreKitBridge {
    /** Products for [ids]; [error] is "" on success. */
    fun loadStoreProducts(ids: List<String>, completion: (products: List<StoreProductReply>, error: String) -> Unit)

    /** Buys [productId] tagged with [appAccountToken] (a UUID string) and finishes the transaction once StoreKit verified it. */
    fun purchaseStoreProduct(productId: String, appAccountToken: String, completion: (StorePurchaseReply) -> Unit)

    /** The current, verified subscription entitlements (`Transaction.currentEntitlements`). */
    fun currentStoreEntitlements(completion: (List<StoreTransactionReply>) -> Unit)

    /** Restore: `AppStore.sync()` then the entitlements; [error] is "" on success ("cancelled" when the person closed the sign-in). */
    fun syncStorePurchases(completion: (transactions: List<StoreTransactionReply>, error: String) -> Unit)

    /** Apple's manage-subscriptions sheet. */
    fun presentManageSubscriptions()

    /** Apple's offer-code redemption sheet. */
    fun presentOfferCodeRedemption()

    /** Called for every transaction StoreKit delivers outside a purchase call (renewals, Ask to Buy approvals, other devices). */
    fun observeStoreTransactions(listener: (StoreTransactionReply) -> Unit)
}
