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
import com.android.billingclient.api.InAppMessageParams
import com.android.billingclient.api.InAppMessageResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
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
 * Google Play Billing (Billing Library 9): connection, subscription product lookup,
 * purchase flow, acknowledgement (also done server-side by playBillingRtdn), active
 * purchase restoration, and Play Store subscription links. Entitlement itself is
 * granted server-side from Play's real-time developer notifications.
 */
class PlayBillingManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "PlayBillingManager"

        // The correct Play Store redeem-code URL. There is no "/store/" segment —
        // https://play.google.com/store/redeem is malformed and produces Google's
        // generic 400 "the server cannot process the request" page. Single source
        // of truth so this can't drift out of sync between call sites again.
        const val REDEEM_CODE_URL = "https://play.google.com/redeem"
        const val ORDER_HISTORY_URL = "https://play.google.com/store/account/orderhistory"
    }

    // Play subscription IDs to load, set from the admin-managed plan catalog.
    @Volatile
    private var catalogProductIds: List<String> = emptyList()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _productDetailsList = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetailsList: StateFlow<List<ProductDetails>> = _productDetailsList.asStateFlow()

    private val _activePurchases = MutableStateFlow<List<Purchase>>(emptyList())
    val activePurchases: StateFlow<List<Purchase>> = _activePurchases.asStateFlow()

    /** A user-facing outcome of a billing action. */
    data class BillingMessage(val text: String, val isError: Boolean)

    // Buffered so a message emitted while no screen is collecting isn't dropped.
    private val _billingMessages = MutableSharedFlow<BillingMessage>(extraBufferCapacity = 8)
    val billingMessages: SharedFlow<BillingMessage> = _billingMessages.asSharedFlow()

    // startConnection can be called again while a connection is still being set up
    // (e.g. init + an immediate product lookup); queue callers instead of starting a
    // second connection, which used to drop the second caller's callback.
    private val pendingOnConnected = mutableListOf<() -> Unit>()
    private var isConnecting = false

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
        .enableAutoServiceReconnection()
        .build()

    fun startConnection(onConnected: (() -> Unit)? = null) {
        if (billingClient.isReady) {
            _isConnected.value = true
            onConnected?.invoke()
            return
        }
        synchronized(pendingOnConnected) {
            onConnected?.let { pendingOnConnected += it }
            if (isConnecting) return
            isConnecting = true
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val callbacks = synchronized(pendingOnConnected) {
                    isConnecting = false
                    pendingOnConnected.toList().also { pendingOnConnected.clear() }
                }
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "BillingClient connected successfully")
                    _isConnected.value = true
                    querySubscriptionProducts()
                    queryActivePurchases()
                    callbacks.forEach { it() }
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage} (${billingResult.responseCode})")
                    _isConnected.value = false
                    if (callbacks.isNotEmpty()) emitMessage(userMessageFor(billingResult.responseCode))
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                synchronized(pendingOnConnected) { isConnecting = false }
                _isConnected.value = false
            }
        })
    }

    /** Updates the Play subscription IDs to load (from the plan catalog) and queries them. */
    fun setCatalogProductIds(productIds: List<String>) {
        val ids = productIds.filter { it.isNotBlank() }.distinct()
        if (ids == catalogProductIds) return
        catalogProductIds = ids
        querySubscriptionProducts()
    }

    fun querySubscriptionProducts(productIds: List<String> = catalogProductIds) {
        if (!billingClient.isReady || productIds.isEmpty()) return

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
                val list = (queryProductDetailsResult as? ProductDetailsResult)?.productDetailsList?.filterNotNull().orEmpty()
                Log.d(TAG, "Retrieved ${list.size} subscription products from Google Play")
                _productDetailsList.value = list
            } else {
                Log.e(TAG, "Error querying product details: ${billingResult.debugMessage}")
            }
        }
    }

    /** Returns true when Play's purchase sheet was actually opened. */
    @Suppress("DEPRECATION")
    fun launchSubscriptionPurchase(
        activity: Activity,
        productDetails: ProductDetails,
        userId: String,
        selectedOfferToken: String? = null,
        oldPurchaseToken: String? = null
    ): Boolean {
        val offerToken = selectedOfferToken
            ?: productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: run {
                emitMessage("\"${productDetails.title}\" has no active offer in Google Play yet. Please try again later.")
                return false
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
            emitMessage(userMessageFor(result.responseCode))
            return false
        }
        return true
    }

    private fun userMessageFor(responseCode: Int): String = when (responseCode) {
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.NETWORK_ERROR -> "Couldn't reach Google Play. Check your connection and try again."
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Google Play billing isn't available on this device or account."
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "This plan isn't available right now."
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "You already have this subscription."
        BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
            "Purchases only work in the app installed from Google Play. Please update from the Play Store."
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "Your Google Play app doesn't support subscriptions. Please update it."
        else -> "Google Play couldn't complete the purchase. Please try again."
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
                emitMessage("Purchase canceled — you weren't charged.", isError = false)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                emitMessage("You already have this subscription.")
                queryActivePurchases()
            }
            else -> {
                Log.e(TAG, "Purchases update failed: ${billingResult.debugMessage} (${billingResult.responseCode})")
                emitMessage(userMessageFor(billingResult.responseCode))
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
                emitMessage("Subscription verified and active!", isError = false)
                coroutineScope.launch { _purchaseEvents.emit(purchase) }
            }
            queryActivePurchases()
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            emitMessage("Your payment is pending — your plan activates once Google Play confirms it.", isError = false)
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully: ${purchase.orderId}")
                emitMessage("Subscription activated successfully!", isError = false)
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
                    val found = (results as? ProductDetailsResult)?.productDetailsList?.filterNotNull()?.firstOrNull()
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
        launchViewSafely(activity, uriStr)
    }

    private fun launchViewSafely(activity: Activity, uri: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (e: android.content.ActivityNotFoundException) {
            emitMessage("No app available to open Google Play on this device.")
        }
    }

    fun openOrderHistory(activity: Activity) {
        launchViewSafely(activity, ORDER_HISTORY_URL)
    }

    /**
     * Opens the Play Store redeem-code page. When [code] is provided, the code is appended as a
     * query parameter so the Play Store pre-fills it for the user. Falls back to the browser URL
     * if the Play Store app is not installed.
     */
    fun openRedeemPromoCode(activity: Activity, code: String? = null) {
        val suffix = if (!code.isNullOrBlank()) "?code=${Uri.encode(code.trim())}" else ""
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://redeem$suffix"))
        try {
            activity.startActivity(marketIntent)
        } catch (e: android.content.ActivityNotFoundException) {
            launchViewSafely(activity, "$REDEEM_CODE_URL$suffix")
        }
    }

    /**
     * Shows Play's in-app subscription messages (grace-period recovery, account hold, etc.).
     * Calls [onSubscriptionUpdated] and re-queries active purchases if the user takes action
     * (e.g. fixes a failed payment) inside the Play-managed dialog.
     */
    fun showInAppMessages(activity: Activity, onSubscriptionUpdated: (() -> Unit)? = null) {
        if (!billingClient.isReady) return
        val params = InAppMessageParams.newBuilder()
            .addInAppMessageCategoryToShow(InAppMessageParams.InAppMessageCategoryId.TRANSACTIONAL)
            .build()
        billingClient.showInAppMessages(activity, params) { result ->
            if (result.responseCode == InAppMessageResult.InAppMessageResponseCode.SUBSCRIPTION_STATUS_UPDATED) {
                queryActivePurchases()
                onSubscriptionUpdated?.invoke()
            }
        }
    }

    private fun emitMessage(msg: String, isError: Boolean = true) {
        coroutineScope.launch {
            _billingMessages.emit(BillingMessage(msg, isError))
        }
    }

    fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
