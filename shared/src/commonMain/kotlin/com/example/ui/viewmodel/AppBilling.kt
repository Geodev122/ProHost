package com.example.ui.viewmodel

import com.example.data.billing.OfferChoice
import com.example.data.billing.PlayProduct
import com.example.data.billing.PlayPromo
import com.example.data.model.AppUser
import com.example.data.repository.ProHostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile

/** What the platform's store needs to show its own purchase UI (Android: the current Activity). Opaque to shared code. */
class PurchaseHost(val platform: Any)

/** A purchase the store reports for this device's account, as far as the Premium page reads it. */
data class ActivePurchase(val products: List<String>, val isAutoRenewing: Boolean)

/**
 * The platform's subscription store as [ProHostViewModel] owns it (`platformBilling`). Android: BillingController (Google Play),
 * also reachable by the app as `viewModel.billing`. iOS gets its StoreKit 2 implementation with the Premium wave; until then it
 * has none ([NoBilling]), so every member has a do-nothing default.
 */
interface AppBilling {
    fun endConnection()

    val playProducts: StateFlow<List<PlayProduct>> get() = NoBillingState.products
    val playBillingConnected: StateFlow<Boolean> get() = NoBillingState.falseFlow
    val activePurchases: StateFlow<List<ActivePurchase>> get() = NoBillingState.purchases
    val billingError: StateFlow<String?> get() = NoBillingState.noText
    val billingSuccess: StateFlow<String?> get() = NoBillingState.noText
    val billingActivationPending: StateFlow<Boolean> get() = NoBillingState.falseFlow
    val kycPromptAfterActivation: StateFlow<Boolean> get() = NoBillingState.falseFlow

    fun clearBillingMessages() {}
    fun dismissBillingActivationPending() {}
    fun consumeKycPromptAfterActivation() {}

    fun launchSubscription(host: PurchaseHost, basePlanId: String, offerChoice: OfferChoice = OfferChoice.Preferred) {}
    fun launchPromoCheckout(host: PurchaseHost, promo: PlayPromo.PromoOffer) {}
    fun openManageSubscriptions(host: PurchaseHost, productId: String? = null) {}
    fun openPlayOrderHistory(host: PurchaseHost) {}
    fun openRedeemPromoCode(host: PurchaseHost, code: String? = null) {}
    fun showBillingInAppMessages(host: PurchaseHost) {}
    /** Re-reads the store's purchases ([host] may be null when no screen is attached). */
    fun refreshPurchases(host: PurchaseHost?, userInitiated: Boolean = false, forceServerSync: Boolean = false) {}
    fun retryBillingQuery(host: PurchaseHost?) {}
}

private object NoBillingState {
    val products: StateFlow<List<PlayProduct>> = MutableStateFlow(emptyList())
    val purchases: StateFlow<List<ActivePurchase>> = MutableStateFlow(emptyList())
    val falseFlow: StateFlow<Boolean> = MutableStateFlow(false)
    val noText: StateFlow<String?> = MutableStateFlow(null)
}

object NoBilling : AppBilling {
    override fun endConnection() {}
}

object BillingDefaults {
    @Volatile
    var create: (scope: CoroutineScope, repository: ProHostRepository, currentUser: StateFlow<AppUser?>, pendingAutoPublishDraftId: StateFlow<String?>) -> AppBilling =
        { _, _, _, _ -> NoBilling }
}
