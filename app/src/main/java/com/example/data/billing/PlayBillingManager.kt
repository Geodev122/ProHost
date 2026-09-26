package com.example.data.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.QueryPurchaseHistoryParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lifecycle-aware manager for Google Play Billing (v7.1.1).
 * Manages BillingClient connection, product querying, launching Google Pay sheets,
 * acknowledgment, active purchase restoration, and Play Store subscription deep links.
 */
class PlayBillingManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "PlayBillingManager"

        // Default Subscription Product IDs configured in Google Play Console
        const val PRODUCT_ID_GROWTH = "package_growth_mrr"
        const val PRODUCT_ID_PRO = "package_pro_mrr"
        const val PRODUCT_ID_ENTERPRISE = "package_enterprise_mrr"

        val ALL_SUBSCRIPTION_PRODUCT_IDS = listOf(
            PRODUCT_ID_GROWTH,
            PRODUCT_ID_PRO,
            PRODUCT_ID_ENTERPRISE
        )
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _productDetailsList = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetailsList: StateFlow<List<ProductDetails>> = _productDetailsList.asStateFlow()

    private val _activePurchases = MutableStateFlow<List<Purchase>>(emptyList())
    val activePurchases: StateFlow<List<Purchase>> = _activePurchases.asStateFlow()

    private val _billingMessages = MutableSharedFlow<String>()
    val billingMessages: SharedFlow<String> = _billingMessages.asSharedFlow()

    private val _purchaseEvents = MutableSharedFlow<Purchase>()
    val purchaseEvents: SharedFlow<Purchase> = _purchaseEvents.asSharedFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    fun startConnection(onConnected: (() -> Unit)? = null) {
        if (billingClient.isReady) {
            _isConnected.value = true
            onConnected?.invoke()
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "BillingClient connected successfully")
                    _isConnected.value = true
                    querySubscriptionProducts()
                    queryActivePurchases()
                    onConnected?.invoke()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage} (${billingResult.responseCode})")
                    _isConnected.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                _isConnected.value = false
            }
        })
    }

    fun querySubscriptionProducts(productIds: List<String> = ALL_SUBSCRIPTION_PRODUCT_IDS) {
        if (!billingClient.isReady) {
            Log.w(TAG, "querySubscriptionProducts called before BillingClient is ready")
            return
        }

        val productList = productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val list = queryProductDetailsResult.productDetailsList
                Log.d(TAG, "Retrieved ${list.size} subscription products from Google Play")
                _productDetailsList.value = list
            } else {
                Log.e(TAG, "Error querying product details: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchSubscriptionPurchase(
        activity: Activity,
        productDetails: ProductDetails,
        userId: String,
        selectedOfferToken: String? = null,
        oldPurchaseToken: String? = null
    ) {
        val offerToken = selectedOfferToken
            ?: productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: run {
                emitMessage("No valid subscription offer found for ${productDetails.title}")
                return
            }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParamsBuilder = BillingFlowParams.newBuilder()
            .setObfuscatedAccountId(userId)
            .setProductDetailsParamsList(listOf(productDetailsParams))

        // Handle upgrade/downgrade replacement mode
        if (!oldPurchaseToken.isNullOrBlank()) {
            val subscriptionUpdateParams = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(oldPurchaseToken)
                .setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE)
                .build()
            billingFlowParamsBuilder.setSubscriptionUpdateParams(subscriptionUpdateParams)
        }

        val result = billingClient.launchBillingFlow(activity, billingFlowParamsBuilder.build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "Failed to launch billing flow: ${result.debugMessage}")
            emitMessage("Unable to start purchase: ${result.debugMessage}")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "User canceled Google Play purchase flow")
                emitMessage("Purchase canceled")
            }
            else -> {
                Log.e(TAG, "Purchases update failed: ${billingResult.debugMessage} (${billingResult.responseCode})")
                emitMessage("Purchase error: ${billingResult.debugMessage}")
            }
        }
    }

    fun queryActivePurchases() {
        if (!billingClient.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Found ${purchases.size} active purchases")
                _activePurchases.value = purchases
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
            } else {
                Log.e(TAG, "Error querying active purchases: ${billingResult.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val isValid = PlayBillingSecurity.verifyPurchase(purchase.originalJson, purchase.signature)
            if (!isValid) {
                Log.e(TAG, "Purchase signature verification failed for order ${purchase.orderId}")
                emitMessage("Purchase security check failed")
                return
            }

            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            } else {
                emitMessage("Subscription verified and active!")
                coroutineScope.launch { _purchaseEvents.emit(purchase) }
            }
            queryActivePurchases()
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            emitMessage("Purchase is pending completion")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully: ${purchase.orderId}")
                emitMessage("Subscription activated successfully!")
                coroutineScope.launch { _purchaseEvents.emit(purchase) }
            } else {
                Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Queries a single subscription product by ID. Useful for fetching Play pricing on demand
     * (e.g. admin "Refresh from Play" button) without re-querying the full catalog. Merges the
     * result into [productDetailsList] so subsequent launch calls find the product immediately.
     * Connects the billing client first if it is not already ready.
     */
    fun queryProductDetailsForId(productId: String, onResult: (ProductDetails?) -> Unit) {
        val doQuery = {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                )
                .build()
            billingClient.queryProductDetailsAsync(params) { billingResult, results ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val found = results.productDetailsList.firstOrNull()
                    // Merge into the main list so future launch calls don't need a re-query
                    if (found != null) {
                        val merged = _productDetailsList.value.filter { it.productId != productId } + found
                        _productDetailsList.value = merged
                    }
                    onResult(found)
                } else {
                    Log.e(TAG, "queryProductDetailsForId failed for $productId: ${billingResult.debugMessage}")
                    onResult(null)
                }
            }
        }
        if (billingClient.isReady) {
            doQuery()
        } else {
            startConnection { doQuery() }
        }
    }

    fun openManageSubscriptions(activity: Activity, productId: String? = null) {
        val uriStr = if (!productId.isNullOrBlank()) {
            "https://play.google.com/store/account/subscriptions?sku=$productId&package=${context.packageName}"
        } else {
            "https://play.google.com/store/account/subscriptions?package=${context.packageName}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))
        activity.startActivity(intent)
    }

    fun openRedeemPromoCode(activity: Activity) {
        // Use the Play Billing in-app redemption sheet (v4+).
        // Falls back to the market:// deep-link if the billing client isn't ready.
        // launchRedeemPromoCode was removed in billing v7; use deep-link directly.
        // Fallback: market:// opens Play Store redeem page directly without browser redirect
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://redeem"))
        if (marketIntent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(marketIntent)
        } else {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/redeem")))
        }
    }

    private fun emitMessage(msg: String) {
        coroutineScope.launch {
            _billingMessages.emit(msg)
        }
    }

    suspend fun queryPurchaseHistory(): List<PurchaseHistoryRecord> = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        if (!billingClient.isReady) {
            continuation.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            continuation.resume(emptyList())
        }
    }

    fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
