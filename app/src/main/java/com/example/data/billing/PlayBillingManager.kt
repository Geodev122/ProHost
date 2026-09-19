package com.example.data.billing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin wrapper around BillingClient for subscription purchases.
 *
 * Lifecycle: create one instance per ViewModel, call [endConnection] in
 * ViewModel.onCleared(). Creating a new instance for every purchase
 * (instead of caching one) is safe — BillingClient handles the Service
 * connection internally — but sharing across multiple ViewModels is not.
 *
 * Product IDs passed to [launchBillingFlow] MUST match the subscription
 * product IDs configured in Google Play Console, which in turn MUST match
 * the package_plans Firestore document IDs so the playBillingRtdn Cloud
 * Function can identify the right plan from an RTDN notification.
 *
 * The RTDN handler (functions/src/billing/playBillingRtdn.ts) is the
 * authoritative path for granting entitlements — this class only initiates
 * the purchase flow and acknowledges the token. Never grant entitlements
 * client-side based on PurchasesUpdatedListener alone.
 */
class PlayBillingManager(
    private val activity: Activity,
    private val userId: String,
) {
    private val tag = "PlayBillingManager"

    private var onPurchaseResult: ((BillingResult, List<Purchase>?) -> Unit)? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        onPurchaseResult?.invoke(result, purchases)
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                acknowledgePurchaseAsync(purchase)
            }
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.w(tag, "PurchasesUpdated error: ${result.responseCode} — ${result.debugMessage}")
        }
    }

    val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    // -------------------------------------------------------------------------
    // Connection
    // -------------------------------------------------------------------------

    /** Connects to the Play Billing service. Safe to call if already connected. */
    fun startConnection(onReady: () -> Unit, onFailed: (String) -> Unit) {
        if (billingClient.isReady) { onReady(); return }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    onReady()
                } else {
                    Log.e(tag, "Billing setup failed: ${result.debugMessage}")
                    onFailed(result.debugMessage)
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w(tag, "Billing service disconnected")
            }
        })
    }

    /** Must be called in ViewModel.onCleared(). */
    fun endConnection() {
        if (billingClient.isReady) billingClient.endConnection()
    }

    // -------------------------------------------------------------------------
    // Product query
    // -------------------------------------------------------------------------

    /**
     * Queries Play for the subscription product matching [planId].
     * Returns null if the product is not found or the query fails.
     */
    suspend fun querySubscriptionProduct(planId: String): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(planId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        return suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { result, productDetails ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    cont.resume(productDetails.firstOrNull())
                } else {
                    Log.e(tag, "queryProductDetails failed for $planId: ${result.debugMessage}")
                    cont.resume(null)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Launch
    // -------------------------------------------------------------------------

    /**
     * Launches the Google Play subscription sheet for [productDetails].
     * [onResult] is called back synchronously on the main thread with the
     * BillingResult. Actual entitlement is granted server-side by the RTDN
     * handler — do not trust the purchase object for that.
     */
    fun launchBillingFlow(
        productDetails: ProductDetails,
        onResult: (BillingResult, List<Purchase>?) -> Unit,
    ): BillingResult {
        onPurchaseResult = onResult

        val offerToken = productDetails.subscriptionOfferDetails
            ?.minByOrNull { it.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros ?: Long.MAX_VALUE }
            ?.offerToken ?: ""

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            // Lets the RTDN Cloud Function identify which user made this purchase
            // without requiring the client to send the purchase token separately.
            .setObfuscatedAccountId(userId)
            .build()

        return billingClient.launchBillingFlow(activity, params)
    }

    // -------------------------------------------------------------------------
    // Acknowledgement
    // -------------------------------------------------------------------------

    /**
     * Acknowledges a purchase. Google requires acknowledgement within 3 days
     * or it will refund the subscription. The entitlement is granted server-
     * side regardless — this is purely a Play Store housekeeping call.
     */
    private fun acknowledgePurchaseAsync(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (purchase.isAcknowledged) return

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(tag, "acknowledgePurchase failed: ${result.debugMessage}")
            }
        }
    }
}
