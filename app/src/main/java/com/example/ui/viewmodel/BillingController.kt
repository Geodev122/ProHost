package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.auth.toUserMessage
import com.example.data.model.*
import com.example.data.repository.ProHostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Google Play Billing for ProHost Premium, owned by [ProHostViewModel] (`viewModel.billing`):
 * connection, products, checkout, plan switches, restore, the activation banner and every
 * billing message. Google Play is the only billing authority — this never grants anything;
 * purchases go to the server (verifyAndRestorePurchase) and the role follows the profile.
 */
class BillingController(
    private val scope: CoroutineScope,
    private val repository: ProHostRepository,
    private val currentUser: StateFlow<AppUser?>,
    private val pendingAutoPublishDraftId: StateFlow<String?>
) {

    // --- Google Play Billing & Google Pay Integration ---
    private var playBillingManager: com.example.data.billing.PlayBillingManager? = null

    val playBillingProducts = MutableStateFlow<List<com.android.billingclient.api.ProductDetails>>(emptyList())
    val playBillingConnected = MutableStateFlow(false)
    val playActivePurchases = MutableStateFlow<List<com.android.billingclient.api.Purchase>>(emptyList())

    // Holds a deferred launch when billing was not yet connected at the time the user tapped
    // "Subscribe via Google Play". Cleared and retried once products arrive from Play.
    // The base plan ("pro-montly"/"pro-yearly") the user tapped before products loaded.
    private var _pendingRetryBasePlanId: String? = null
    private var _pendingRetryActivity: java.lang.ref.WeakReference<android.app.Activity>? = null
    // A deferred launch is only honoured shortly after the tap; a later products load (e.g. the
    // next Premium visit after a failed connection) must never open Play's sheet by itself.
    private var _pendingRetryAtMillis = 0L

    fun initPlayBilling(context: Context) {
        if (playBillingManager == null) {
            val manager = com.example.data.billing.PlayBillingManager(context.applicationContext, scope)
            playBillingManager = manager
            scope.launch {
                try {
                    manager.isConnected.collect { connected ->
                        playBillingConnected.value = connected
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("ProHostVM", "Operation failed", e)
                }
            }
            scope.launch {
                try {
                    manager.productDetailsList.collect { products ->
                        playBillingProducts.value = products
                        // Retry a deferred billing launch once the target product is available
                        val retryPlan = _pendingRetryBasePlanId
                        val retryActivity = _pendingRetryActivity?.get()
                        val retryFresh = System.currentTimeMillis() - _pendingRetryAtMillis < PENDING_RETRY_WINDOW_MS
                        if (retryPlan != null && (retryActivity == null || !retryFresh)) {
                            _pendingRetryBasePlanId = null
                            _pendingRetryActivity = null
                        } else if (retryPlan != null && retryActivity != null &&
                            products.any { it.productId == com.example.data.billing.PlayCatalog.PRODUCT_ID }) {
                            _pendingRetryBasePlanId = null
                            _pendingRetryActivity = null
                            launchGooglePaySubscription(retryActivity, retryPlan)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("ProHostVM", "Product details collection failed", e)
                    _billingError.value = "Couldn't load Google Play plans. Please close and reopen Subscriptions."
                }
            }
            // Every billing outcome (no offer, launch failure, cancel, success) reaches
            // the Subscriptions screen banners; before, nothing collected these, so a
            // failed tap on a plan looked like nothing happened.
            scope.launch {
                try {
                    manager.billingMessages.collect { message ->
                        // Cancel, pending payment, already owned and errors all mean no paid
                        // purchase is being activated: never leave the "Activating" banner up.
                        dismissBillingActivationPending()
                        if (message.isError) {
                            _billingSuccess.value = null
                            _billingError.value = message.text
                        } else {
                            _billingError.value = null
                            _billingSuccess.value = message.text
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("ProHostVM", "Billing message collection failed", e)
                }
            }
            scope.launch {
                try {
                    manager.activePurchases.collect { playActivePurchases.value = it }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("ProHostVM", "Operation failed", e)
                }
            }
            scope.launch {
                try {
                    // Play's "verify on your backend, grant, then acknowledge": the server
                    // (verifyAndRestorePurchase) checks the token with Google, grants the plan
                    // and acknowledges it. One attempt per token per session.
                    manager.purchaseEvents.collect { event ->
                        val purchase = event.purchase
                        val productId = purchase.products.firstOrNull() ?: return@collect
                        if (!processedPurchaseTokens.add(purchase.purchaseToken)) return@collect
                        // Play confirmed payment: only now is there something to activate.
                        if (event.fromCheckout) showBillingActivationPending()
                        val result = try {
                            repository.verifyAndRestorePlayPurchase(purchase.purchaseToken, productId, fromCheckout = event.fromCheckout)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                        result.onSuccess {
                            onPlanActivated("Your Pro Host plan is active.")
                        }.onFailure { e ->
                            dismissBillingActivationPending()
                            // The server parks a paid purchase it couldn't confirm and retries it;
                            // the profile listener below finishes the upgrade when that lands.
                            awaitingServerActivation = true
                            // Let a later resume retry it; RTDN also processes it server-side.
                            processedPurchaseTokens.remove(purchase.purchaseToken)
                            android.util.Log.w("ProHostVM", "Server activation failed: ${e.message}")
                            if (event.fromCheckout) {
                                com.example.analytics.AnalyticsTracker.premiumPurchaseFailed(null, "server_activation")
                            }
                            if (event.fromCheckout) {
                                _billingError.value = e.toUserMessage(
                                    "Your payment went through, but we couldn't activate your plan yet. " +
                                        "Reopen Subscriptions in a moment, or tap Restore Purchases."
                                )
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("ProHostVM", "Operation failed", e)
                }
            }
            manager.startConnection()
        }
    }

    private val _billingError = MutableStateFlow<String?>(null)
    val billingError: StateFlow<String?> = _billingError.asStateFlow()

    private val _billingSuccess = MutableStateFlow<String?>(null)
    val billingSuccess: StateFlow<String?> = _billingSuccess.asStateFlow()

    private val _billingActivationPending = MutableStateFlow(false)
    val billingActivationPending: StateFlow<Boolean> = _billingActivationPending.asStateFlow()
    private var billingActivationTimeoutJob: kotlinx.coroutines.Job? = null

    // Tracks the expiry the user had BEFORE launching a Play billing flow so the
    // collectLatest observer can distinguish "RTDN updated the expiry" from
    // "profile updated for some other reason (existing subscriber)".
    private val _billingPriorExpiryMillis = MutableStateFlow<Long?>(null)

    fun clearBillingMessages() {
        _billingError.value = null
        _billingSuccess.value = null
    }

    fun dismissBillingActivationPending() {
        billingActivationTimeoutJob?.cancel()
        _billingActivationPending.value = false
    }

    // Shown only between Play's PURCHASED result and the server's answer — never while the
    // Play sheet is open, so backing out of checkout can't leave it spinning.
    private fun showBillingActivationPending() {
        _billingActivationPending.value = true
        billingActivationTimeoutJob?.cancel()
        billingActivationTimeoutJob = scope.launch {
            runCatching { kotlinx.coroutines.delay(2 * 60 * 1000L) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            _billingActivationPending.value = false
        }
    }

    // A checkout whose server activation failed (it is parked and retried server-side):
    // the profile listener completes the role upgrade when the grant arrives.
    private var awaitingServerActivation = false

    private val _kycPromptAfterActivation = MutableStateFlow(false)
    /** True once a plan activates for someone who can't host yet (e.g. a promo redemption). */
    val kycPromptAfterActivation: StateFlow<Boolean> = _kycPromptAfterActivation.asStateFlow()
    fun consumeKycPromptAfterActivation() { _kycPromptAfterActivation.value = false }

    private suspend fun onPlanActivated(message: String) {
        awaitingServerActivation = false
        dismissBillingActivationPending()
        _billingError.value = null
        _billingSuccess.value = message
        // Force-refresh the ID token so the new PRO_HOST claim takes effect immediately. A
        // failure here (offline) must not end the purchase/profile collectors that called us:
        // the profile listener and the next foreground pick the role up anyway.
        try {
            refreshCurrentUserRoleAfterEntitlement()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("BillingController", "Role refresh after activation failed; will catch up on the next sync", e)
        }
        if (currentUser.value?.canHost(com.example.data.auth.PhoneLink.isLinked()) == false) {
            _kycPromptAfterActivation.value = true
        }
    }

    /**
     * Opens Google Play's purchase sheet for ProHost Premium's [basePlanId]
     * ([PlayCatalog.BASE_PLAN_MONTHLY] / [PlayCatalog.BASE_PLAN_YEARLY]). The app never
     * grants anything itself: the result goes to the server (purchaseEvents →
     * verifyAndRestorePurchase), which verifies with Google and assigns the role.
     */
    fun launchGooglePaySubscription(
        activity: android.app.Activity,
        basePlanId: String
    ) {
        val uid = currentUser.value?.id ?: run {
            _billingError.value = "Your session expired — please sign in again to subscribe."
            return
        }
        clearBillingMessages()
        // Persist the pending draft ID before the billing sheet opens so the server can
        // auto-publish it when the subscription is confirmed.
        val draftId = pendingAutoPublishDraftId.value
        if (draftId != null) {
            scope.launch {
                try {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("user_profiles").document(uid)
                        .update("pendingPlayPublishDraftId", draftId).await()
                } catch (_: Exception) { /* non-fatal; the server will skip auto-publish */ }
            }
        }

        val manager = playBillingManager ?: run {
            initPlayBilling(activity)
            playBillingManager
        }
        val productId = com.example.data.billing.PlayCatalog.PRODUCT_ID
        val product = manager?.productDetailsList?.value?.find { it.productId == productId }
        if (product != null) {
            // Monthly ↔ yearly is a replacement of the same subscription: pass the current
            // purchase token so Play switches plans instead of selling a second subscription.
            val currentPlan = currentUser.value?.activePlanId()
            val oldPurchaseToken = if (currentPlan != null && currentPlan != basePlanId &&
                currentPlan in com.example.data.billing.PlayCatalog.BASE_PLANS) {
                manager.activePurchases.value.firstOrNull { productId in it.products }?.purchaseToken
            } else null
            // Switching plans without the current purchase token would sell a SECOND
            // subscription (double charge). That happens when this device's Play account
            // isn't the one that subscribed, or Play hasn't returned purchases yet.
            if (currentPlan != null && currentPlan != basePlanId &&
                currentPlan in com.example.data.billing.PlayCatalog.BASE_PLANS && oldPurchaseToken == null) {
                scope.launch { runCatching { manager.fetchActivePurchases() } }
                _billingError.value = "To switch plans, open the Play Store with the Google account that subscribed " +
                    "(or use Manage subscription). We couldn't find your current subscription on this device."
                return
            }

            _billingPriorExpiryMillis.value = currentUser.value?.ownerPackageExpiryMillis
            // The "Activating" banner waits for Play's PURCHASED result (purchaseEvents).
            // Play's recommended replacement modes: an upgrade (to yearly) applies now with credit
            // for unused time; a downgrade (to monthly) starts at the next renewal date.
            val replacementMode = if (basePlanId == com.example.data.billing.PlayCatalog.BASE_PLAN_YEARLY) {
                com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
            } else {
                com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED
            }
            manager.launchSubscriptionPurchase(
                activity, product, userId = uid, basePlanId = basePlanId,
                oldPurchaseToken = oldPurchaseToken, replacementMode = replacementMode
            )
        } else {
            // Store the intent and retry automatically once products load from Play
            _pendingRetryBasePlanId = basePlanId
            _pendingRetryActivity = java.lang.ref.WeakReference(activity)
            _pendingRetryAtMillis = System.currentTimeMillis()
            Toast.makeText(activity, "Connecting to Google Play Store…", Toast.LENGTH_SHORT).show()
            // Merges into productDetailsList, whose collector above retries this launch.
            manager?.queryProductDetailsForId(productId) { found ->
                if (found == null) {
                    _pendingRetryBasePlanId = null
                    _pendingRetryActivity = null
                    // Billing callbacks may arrive off the main thread: use state, not a Toast.
                    _billingError.value = "ProHost Premium isn't available in Google Play right now. Please try again later."
                }
            }
        }
    }

    fun openManageSubscriptions(activity: android.app.Activity, productId: String? = null) {
        com.example.analytics.AnalyticsTracker.manageSubscriptionOpen()
        playBillingManager?.openManageSubscriptions(activity, productId) ?: run {
            val uri = if (!productId.isNullOrBlank()) {
                "https://play.google.com/store/account/subscriptions?sku=$productId&package=${activity.packageName}"
            } else {
                "https://play.google.com/store/account/subscriptions?package=${activity.packageName}"
            }
            openUriOrToast(activity, uri)
        }
    }

    fun openRedeemPromoCode(activity: android.app.Activity, code: String? = null) {
        com.example.analytics.AnalyticsTracker.redeemCodeOpen()
        playBillingManager?.openRedeemPromoCode(activity, code) ?: run {
            val suffix = if (!code.isNullOrBlank()) "?code=${Uri.encode(code.trim())}" else ""
            openUriOrToast(activity, "${com.example.data.billing.PlayBillingManager.REDEEM_CODE_URL}$suffix")
        }
    }

    fun showBillingInAppMessages(activity: android.app.Activity) {
        val manager = playBillingManager ?: run { initPlayBilling(activity); playBillingManager } ?: return
        manager.showInAppMessages(activity) {
            // The person fixed their payment in Play's message: re-sync with the server now
            // rather than waiting for the notification.
            refreshPlayPurchases(activity, forceServerSync = true)
        }
    }

    private fun openUriOrToast(activity: android.app.Activity, uri: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(activity, "No app available to open this link.", Toast.LENGTH_SHORT).show()
        }
    }

    private var restoreCheckJob: kotlinx.coroutines.Job? = null
    // Purchase tokens already sent for server activation this session.
    private val processedPurchaseTokens = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** [userInitiated] (the Restore button) reports every outcome; the silent on-open check only reports a restore. */
    fun refreshPlayPurchases(context: Context, userInitiated: Boolean = false, forceServerSync: Boolean = false) {
        val manager = playBillingManager ?: run { initPlayBilling(context); playBillingManager } ?: return
        // If the user already has a Firestore entitlement, just refresh the local
        // purchases cache — no server call needed (unless Play just told us the status changed).
        if (currentUser.value?.activePlanId() != null && !forceServerSync) {
            manager.queryActivePurchases()
            if (userInitiated) {
                com.example.analytics.AnalyticsTracker.premiumRestoreResult("already_active")
                _billingSuccess.value = "Your plan is already active on this account."
            }
            return
        }
        // No entitlement yet — query Play and, if an active purchase is found,
        // verify it server-side to restore the entitlement (handles dropped RTDNs and
        // promo codes redeemed in the Play Store). One silent check at a time: the app
        // root and the Subscriptions screen both trigger it on resume.
        if (!userInitiated && restoreCheckJob?.isActive == true) return
        restoreCheckJob = scope.launch {
            runCatching {
                // A fresh query result, not the next StateFlow emission: an unchanged
                // purchase list never re-emits, which left Restore waiting forever.
                val purchases = manager.fetchActivePurchases() ?: run {
                    if (userInitiated) _billingError.value = "Couldn't reach Google Play. Please try again."
                    return@runCatching
                }
                val activePurchase = purchases.firstOrNull {
                    it.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PURCHASED && !it.isSuspended
                }
                val productId = activePurchase?.products?.firstOrNull()
                if (activePurchase == null || productId == null) {
                    if (userInitiated) {
                        com.example.analytics.AnalyticsTracker.premiumRestoreResult("none_found")
                        _billingError.value = "No active Google Play subscription was found for this Google account."
                    }
                    return@runCatching
                }
                // Unprocessed purchases are already on their way through purchaseEvents;
                // don't send the same token twice (the Restore button always retries).
                // A purchase Play still holds as unacknowledged (e.g. a checkout that just
                // finished) belongs to the purchaseEvents path, which carries fromCheckout and
                // shows the "Activating" banner and its errors — the silent check must not
                // claim it first.
                if (!userInitiated && !forceServerSync && !activePurchase.isAcknowledged) return@runCatching
                if (!processedPurchaseTokens.add(activePurchase.purchaseToken) && !userInitiated && !forceServerSync) return@runCatching
                repository.verifyAndRestorePlayPurchase(activePurchase.purchaseToken, productId)
                    .onSuccess {
                        com.example.analytics.AnalyticsTracker.premiumRestoreResult("restored")
                        onPlanActivated("Purchase restored — your Pro Host plan is active.")
                    }
                    .onFailure {
                        processedPurchaseTokens.remove(activePurchase.purchaseToken)
                        awaitingServerActivation = true
                        android.util.Log.w("ProHostViewModel", "verifyAndRestorePurchase failed: ${it.message}")
                        com.example.analytics.AnalyticsTracker.premiumRestoreResult("failed")
                        // The silent on-open check stays quiet; the Restore button reports why.
                        if (userInitiated) {
                            _billingError.value = it.toUserMessage("Couldn't restore your purchase. Please try again.")
                        }
                    }
            }.onFailure { e ->
                android.util.Log.e("ProHostViewModel", "restorePlayPurchases error: ${e.message}")
                if (userInitiated) _billingError.value = "Couldn't check Google Play for purchases. Please try again."
            }
        }
    }

    fun openPlayOrderHistory(activity: android.app.Activity) {
        com.example.analytics.AnalyticsTracker.orderHistoryOpen()
        playBillingManager?.openOrderHistory(activity)
            ?: openUriOrToast(activity, com.example.data.billing.PlayBillingManager.ORDER_HISTORY_URL)
    }

    fun retryBillingQuery(context: android.content.Context) {
        val manager = playBillingManager ?: run { initPlayBilling(context); playBillingManager } ?: return
        manager.querySubscriptionProducts()
    }


    /**
     * Re-reads the signed-in user's role from a force-refreshed Firebase Auth ID
     * token and reflects it into currentUser — the client-side counterpart of
     * grantEntitlement()'s grantProHostRoleIfNeeded() (see entitlements.ts). Called
     * once a package payment is confirmed settled, so a SPECIALIST who
     * just got promoted to PRO_HOST sees Pro Host navigation immediately, without
     * needing to sign out and back in. Mirrors how AuthFlow.resolveVerifiedRole()
     * already resolves role at sign-in — role always comes from the custom claim,
     * never trusted from Firestore's user_profiles.role mirror field alone.
     */
    private suspend fun refreshCurrentUserRoleAfterEntitlement() {
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
        val claim = com.example.data.auth.FirebaseFunctionsClient.readRoleClaim(firebaseUser, forceRefresh = true) ?: return
        val role = runCatching { UserRole.valueOf(claim) }.getOrNull() ?: return
        repository.login(uid = firebaseUser.uid, email = firebaseUser.email ?: "", verifiedRole = role)
    }

    // Clears billingActivationPending once the Firestore listener reflects the RTDN
    // grant — i.e. ownerPackageExpiryMillis changed from what it was at launch time.
    init {
        scope.launch {
            try {
                // collect, not collectLatest: a profile emission mid-activation must not cancel
                // onPlanActivated (token refresh, role reload, KYC prompt) halfway.
                currentUser.collect { user ->
                    if (user?.ownerPackageExpiryMillis != null &&
                        user.ownerPackageExpiryMillis != _billingPriorExpiryMillis.value &&
                        (_billingActivationPending.value || awaitingServerActivation)
                    ) {
                        // The grant arrived by RTDN or the server retry job.
                        onPlanActivated("Your Pro Host plan is active.")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ProHostVM", "Operation failed", e)
            }
        }
    }

    fun endConnection() {
        playBillingManager?.endConnection()
    }
}

private const val PENDING_RETRY_WINDOW_MS = 30_000L

/** The plan that still grants today: an expired one (before the hourly sweep clears it) is no plan. */
private fun AppUser.activePlanId(): String? {
    val plan = ownerPackageId ?: return null
    val expiry = ownerPackageExpiryMillis
    return plan.takeIf { expiry == null || expiry == 0L || expiry > System.currentTimeMillis() }
}
