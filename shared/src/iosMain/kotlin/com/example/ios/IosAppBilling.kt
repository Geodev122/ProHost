package com.example.ios

import com.example.data.billing.AppStoreKind
import com.example.data.billing.ManageRoute
import com.example.data.billing.StoreBrand
import com.example.analytics.AnalyticsTracker
import com.example.data.auth.SessionAuth
import com.example.data.auth.toUserMessage
import com.example.data.billing.AppleCatalog
import com.example.data.billing.OfferChoice
import com.example.data.billing.PlayCatalog
import com.example.data.billing.PlayProduct
import com.example.data.billing.PlayPromo
import com.example.data.model.AppUser
import com.example.data.model.UserRole
import com.example.data.repository.ProHostRepository
import com.example.ui.util.PlatformActions
import com.example.ui.util.sharedText
import com.example.ui.viewmodel.ActivePurchase
import com.example.ui.viewmodel.AppBilling
import com.example.ui.viewmodel.PurchaseHost
import com.example.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ProHost Premium on iOS (StoreKit 2), the counterpart of Android's BillingController behind the shared [AppBilling] seam.
 * Prices, periods and introductory offers come only from StoreKit ([AppleCatalog.asPlayProduct]); the app never grants:
 * each transaction is sent to `verifyAppleTransaction`, which verifies Apple's signature and sets the role. The Premium page
 * itself is the shared screen, unchanged.
 */
class IosAppBilling(
    private val store: StoreKitBridge,
    private val scope: CoroutineScope,
    private val repository: ProHostRepository,
    private val currentUser: StateFlow<AppUser?>,
) : AppBilling {

    private val _products = MutableStateFlow<List<PlayProduct>>(emptyList())
    override val playProducts: StateFlow<List<PlayProduct>> = _products.asStateFlow()
    private val _connected = MutableStateFlow(false)
    override val playBillingConnected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _purchases = MutableStateFlow<List<ActivePurchase>>(emptyList())
    override val activePurchases: StateFlow<List<ActivePurchase>> = _purchases.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val billingError: StateFlow<String?> = _error.asStateFlow()
    private val _success = MutableStateFlow<String?>(null)
    override val billingSuccess: StateFlow<String?> = _success.asStateFlow()
    private val _pending = MutableStateFlow(false)
    override val billingActivationPending: StateFlow<Boolean> = _pending.asStateFlow()
    private val _kycPrompt = MutableStateFlow(false)
    override val kycPromptAfterActivation: StateFlow<Boolean> = _kycPrompt.asStateFlow()

    /** Transactions already sent to the server this session (one attempt each, like Android's purchase tokens). */
    private val verified = mutableSetOf<String>()

    init {
        store.observeStoreTransactions { tx -> scope.launch { verify(tx, fromCheckout = false, userInitiated = false) } }
        loadProducts()
    }

    private fun loadProducts() {
        store.loadStoreProducts(AppleCatalog.PRODUCT_IDS) { products, error ->
            scope.launch {
                // Unlimited and the single-listing tier, each as its own product, like the two Play products.
                val mapped = AppleCatalog.asPlayProducts(products.map { it.toStoreProduct() })
                _products.value = mapped
                _connected.value = mapped.isNotEmpty()
                if (mapped.isEmpty()) {
                    AppLog.w(TAG, "StoreKit returned no ProHost products ($error)")
                    _error.value = sharedText("app_store_plans_unavailable")
                }
            }
        }
    }

    override fun endConnection() {}

    override fun clearBillingMessages() {
        _error.value = null
        _success.value = null
    }

    override fun dismissBillingActivationPending() {
        _pending.value = false
    }

    override fun consumeKycPromptAfterActivation() {
        _kycPrompt.value = false
    }

    override fun launchSubscription(host: PurchaseHost, basePlanId: String, offerChoice: OfferChoice) {
        val uid = currentUser.value?.id ?: SessionAuth.currentUser()?.uid
        if (uid.isNullOrBlank()) {
            _error.value = sharedText("your_session_expired_please_sign_in_again")
            return
        }
        if (!AppleCatalog.isAppleProduct(basePlanId)) {
            _error.value = sharedText("app_store_plans_unavailable")
            return
        }
        val plan = PlayCatalog.planInterval(basePlanId) ?: basePlanId
        val regular = _products.value.flatMap { it.offers }.firstOrNull { it.basePlanId == basePlanId && it.offerId == null }
            ?.phases?.lastOrNull()
        AnalyticsTracker.premiumCheckoutStarted(plan, regular?.micros, regular?.currency)
        store.purchaseStoreProduct(basePlanId, AppleCatalog.appAccountTokenFor(uid)) { reply ->
            scope.launch {
                when (reply.outcome) {
                    0 -> reply.transaction?.let { verify(it, fromCheckout = true, userInitiated = true) }
                    1 -> Unit
                    2 -> {
                        AnalyticsTracker.premiumPurchasePending(plan)
                        _success.value = sharedText("app_store_purchase_pending")
                    }
                    else -> {
                        AppLog.w(TAG, "Purchase failed: ${reply.error}")
                        AnalyticsTracker.premiumPurchaseFailed(plan, "app_store")
                        _error.value = sharedText("app_store_purchase_failed")
                    }
                }
            }
        }
    }

    /** No promo-code offers on iOS (Play config only); App Store offer codes go through [openRedeemPromoCode]. */
    override fun launchPromoCheckout(host: PurchaseHost, promo: PlayPromo.PromoOffer) {}

    /** The store that bills the plan decides (24 Nov 2026): App Store plans open Apple's sheet, Google Play plans Play's web page. */
    override fun openManageSubscriptions(host: PurchaseHost, productId: String?) {
        AnalyticsTracker.manageSubscriptionOpen()
        when (StoreBrand.manageRoute(currentUser.value?.entitlementSource, AppStoreKind.APP_STORE)) {
            ManageRoute.STORE_SHEET -> store.presentManageSubscriptions()
            ManageRoute.PLAY_WEB -> PlatformActions.current?.openUrl(StoreBrand.PLAY_SUBSCRIPTIONS_URL)
            ManageRoute.APP_STORE_WEB -> PlatformActions.current?.openUrl(StoreBrand.APP_STORE_SUBSCRIPTIONS_URL)
            ManageRoute.NONE -> Unit
        }
    }

    override fun openPlayOrderHistory(host: PurchaseHost) {
        val playPlan = StoreBrand.manageRoute(currentUser.value?.entitlementSource, AppStoreKind.APP_STORE) == ManageRoute.PLAY_WEB
        PlatformActions.current?.openUrl(if (playPlan) StoreBrand.PLAY_ORDER_HISTORY_URL else ORDER_HISTORY_URL)
    }

    override fun openRedeemPromoCode(host: PurchaseHost, code: String?) = store.presentOfferCodeRedemption()

    override fun refreshPurchases(host: PurchaseHost?, userInitiated: Boolean, forceServerSync: Boolean) {
        if (userInitiated) {
            store.syncStorePurchases { transactions, error ->
                scope.launch {
                    if (error.isNotEmpty()) {
                        if (error != "cancelled") _error.value = sharedText("app_store_restore_failed")
                        AnalyticsTracker.premiumRestoreResult("error")
                        return@launch
                    }
                    applyEntitlements(transactions, userInitiated = true, force = true)
                }
            }
        } else {
            store.currentStoreEntitlements { transactions ->
                scope.launch { applyEntitlements(transactions, userInitiated = false, force = forceServerSync) }
            }
        }
    }

    override fun retryBillingQuery(host: PurchaseHost?) = loadProducts()

    private suspend fun applyEntitlements(transactions: List<StoreTransactionReply>, userInitiated: Boolean, force: Boolean) {
        val ours = transactions.filter { AppleCatalog.isAppleProduct(it.productId) }
        _purchases.value = ours.map {
            ActivePurchase(listOf(PlayCatalog.productOf(PlayCatalog.tierOf(it.productId)), it.productId), it.autoRenews)
        }
        if (userInitiated && ours.isEmpty()) {
            _error.value = sharedText("app_store_nothing_to_restore")
            AnalyticsTracker.premiumRestoreResult("none")
            return
        }
        if (force) verified.clear()
        ours.forEach { verify(it, fromCheckout = false, userInitiated = userInitiated) }
    }

    /** Sends [tx] to the server once per session; the server's answer, not StoreKit's, decides access. */
    private suspend fun verify(tx: StoreTransactionReply, fromCheckout: Boolean, userInitiated: Boolean) {
        if (!AppleCatalog.isAppleProduct(tx.productId)) return
        if (!verified.add(tx.signedTransaction)) return
        if (fromCheckout) _pending.value = true
        val result = try {
            repository.functionsClient.verifyAppleTransaction(tx.signedTransaction, tx.signedRenewalInfo.ifBlank { null })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result.onSuccess { hasAccess ->
            if (hasAccess && (fromCheckout || userInitiated)) {
                if (userInitiated && !fromCheckout) AnalyticsTracker.premiumRestoreResult("restored")
                onPlanActivated(sharedText(if (fromCheckout) "your_pro_host_plan_is_active" else "purchase_restored_your_pro_host_plan_is_ac"))
            } else {
                _pending.value = false
                if (hasAccess) refreshRole()
            }
        }.onFailure { e ->
            verified.remove(tx.signedTransaction)
            _pending.value = false
            AppLog.w(TAG, "verifyAppleTransaction failed: ${e.message}")
            if (fromCheckout) {
                AnalyticsTracker.premiumPurchaseFailed(null, "server_activation")
                _error.value = e.toUserMessage(sharedText("app_store_activation_failed"))
            } else if (userInitiated) {
                AnalyticsTracker.premiumRestoreResult("error")
                _error.value = e.toUserMessage(sharedText("app_store_restore_failed"))
            }
        }
    }

    private suspend fun onPlanActivated(message: String) {
        _pending.value = false
        _error.value = null
        _success.value = message
        refreshRole()
        if (currentUser.value?.canHost() == false) _kycPrompt.value = true
    }

    /** Fresh ID token so the PRO_HOST claim applies at once (as Android's refreshCurrentUserRoleAfterEntitlement). */
    private suspend fun refreshRole() {
        try {
            val user = SessionAuth.currentUser() ?: return
            val claim = user.readRoleClaim(true) ?: return
            val role = UserRole.entries.firstOrNull { it.name == claim } ?: return
            repository.login(uid = user.uid, email = user.email ?: "", verifiedRole = role)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Role refresh after activation failed: ${e.message}")
        }
    }

    private fun StoreProductReply.toStoreProduct() = AppleCatalog.StoreProduct(
        productId, productDescription, priceMicros, currency, formattedPrice, period,
        introPriceMicros, introFormattedPrice, introPeriod, introCycles,
    )

    private companion object {
        const val TAG = "IosAppBilling"
        const val ORDER_HISTORY_URL = "https://reportaproblem.apple.com"
    }
}

