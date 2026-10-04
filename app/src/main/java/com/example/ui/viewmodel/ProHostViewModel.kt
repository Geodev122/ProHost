package com.example.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.toUserMessage
import com.example.data.model.*
import com.example.data.repository.ProHostRepository
import com.example.util.guessFileExtension
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder

/**
 * Distinguishes *why* a listing-create attempt failed, so the caller can show a
 * specific toast instead of one generic "check your connection or your package
 * limit" sentence that reads the same for both causes.
 */
sealed class ListingCreateResult {
    object Success : ListingCreateResult()
    // Publishing needs an active (non-expired) subscription; there is no listing limit.
    object NoActivePackage : ListingCreateResult()
    object Failed : ListingCreateResult()
}

/**
 * Bounds cold-start session restoration (see ProHostViewModel's init block) so a
 * slow — not fully offline — connection can't leave the splash screen up for
 * however long the underlying network call takes (the Firebase Functions SDK's
 * own default timeout is up to 70s, which reads as "hung" on a splash screen).
 */
private const val SESSION_RESTORE_TIMEOUT_MS = 15_000L

class ProHostViewModel(
    val repository: ProHostRepository = ProHostRepository.getInstance()
) : ViewModel() {

    // Cross-cutting callables (e.g. sendPaymentReminder) used from several screens.
    private val functionsClient = com.example.data.auth.FirebaseFunctionsClient()

    // Cold-start session restoration — the actual fix for "signed out whenever the app
    // is closed." repository.currentUser previously started (and, on a fresh process,
    // always stayed) null until the explicit phone-OTP flow set it; nothing anywhere
    // ever checked whether a Firebase Auth session already existed on disk and
    // rehydrated it. Firebase's own token persistence was never the problem — this
    // ViewModel's cold-start behavior simply never looked at it. Reuses
    // completeVerifiedLogin exactly as a fresh sign-in already does (resolve role via
    // the custom claim, then repository.login) rather than inventing a second way to
    // build an AppUser.
    private val _isRestoringSession = MutableStateFlow(
        runCatching { FirebaseAuth.getInstance().currentUser != null }.getOrDefault(false)
    )
    val isRestoringSession: StateFlow<Boolean> = _isRestoringSession.asStateFlow()
    private val _sessionRestoreError = MutableStateFlow<String?>(null)
    val sessionRestoreError: StateFlow<String?> = _sessionRestoreError.asStateFlow()

    // Non-null when cold-start restoration finds a verified Firebase session (any
    // provider — email, Google or phone) whose registration was never completed.
    // ProHostAppRoot then opens LoginAuthScreen directly on the registration form.
    // The value is the verified phone for a phone sign-in, or "" for email/Google.
    private val _pendingRegistrationPhone = MutableStateFlow<String?>(null)
    val pendingRegistrationPhone: StateFlow<String?> = _pendingRegistrationPhone.asStateFlow()

    fun clearPendingRegistrationPhone() {
        _pendingRegistrationPhone.value = null
    }

    init {
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null) {
            viewModelScope.launch {
                try {
                    // Force refresh ID token to instantly pick up any server-side custom claim changes (e.g. ADMIN role promotion)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            firebaseUser.getIdToken(true).await()
                        } catch (e: Exception) {
                            // Non-fatal if offline
                        }
                    }

                    val user = kotlinx.coroutines.withTimeout(SESSION_RESTORE_TIMEOUT_MS) {
                        com.example.data.auth.completeVerifiedLogin(repository, functionsClient, firebaseUser)
                    }
                    // Same completeness rule every sign-in path uses. Email/Google users have
                    // no phone until KYC, so a phone check here used to sign them out on
                    // every cold start.
                    if (!user.isProfileComplete()) {
                        repository.discardIncompleteSession()
                        _pendingRegistrationPhone.value = firebaseUser.phoneNumber.orEmpty()
                    } else {
                        runCatching {
                            @Suppress("DEPRECATION")
                            val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                            repository.registerFcmToken(user.id, token)
                        }
                    }
                } catch (e: com.example.data.auth.AccountSuspendedException) {
                    // Same handling the explicit sign-in flow uses for this exception — the
                    // account is server-confirmed suspended, so don't leave a locally-valid
                    // Firebase session around for the next cold start to just retry.
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    // Session restore took >15s — most likely a slow connection with no
                    // cached claim. currentUser stays null → ProHostNavGraph falls through
                    // to LoginAuthScreen, but we surface a banner so the user knows why.
                    _sessionRestoreError.value = "Session restore timed out. Please sign in again."
                } catch (e: Exception) {
                    // Offline with no prior custom claim, or other transient failure.
                    // repository.currentUser stays null — LoginAuthScreen handles it.
                } finally {
                    _isRestoringSession.value = false
                }
            }
        }
    }

    val pricingState: StateFlow<AdminPricingState> = repository.pricingState
    val spaces: StateFlow<List<SpaceListing>> = repository.spaces
    val isCloudConnected: StateFlow<Boolean> = repository.isCloudConnected
    // Admin-managed facility/category catalog — previously read only by AdminConsoleScreen
    // itself, so an admin's additions in "Schema Architecture" never reached the
    // create-listing wizard or Discovery's filter sheet, both of which used a
    // hardcoded FacilityCatalog.standard object instead. Exposed here so both can
    // read the same live, admin-editable list.
    val spaceArchitectureSchema: StateFlow<SpaceArchitectureSchema> = repository.spaceArchitectureSchema
    val users: StateFlow<List<AppUser>> = repository.users
    val currentUser: StateFlow<AppUser?> = repository.currentUser
    val auditLogs: StateFlow<List<AuditSecurityLog>> = repository.auditLogs
    val bookingRequests: StateFlow<List<RentalBookingRequest>> = repository.bookingRequests
    val hasLoadedBookingsOnce: StateFlow<Boolean> = repository.hasLoadedBookingsOnce
    val fcmAlerts: StateFlow<List<FCMAlert>> = repository.fcmAlerts
    val isOfflineMode: StateFlow<Boolean> = repository.isOfflineMode
    val syncStatusMessage: StateFlow<String?> = repository.syncStatusMessage

    fun markAlertAsRead(alertId: String) {
        repository.markAlertAsRead(alertId)
    }

    // Owner spaces — ownerId is the real, authoritative identity match (stamped at
    // create time and rules-enforced to equal the creating uid). This used to also
    // match on "ownerName contains fullName" as a fallback, which is a genuine
    // cross-tenant privacy bug: any host whose name is a substring of another
    // host's listed owner name (e.g. "Sara" inside "Sara Khalil Clinic") would see
    // that other host's real listings merged into their own dashboard.
    private val _pendingDeletionIds = mutableSetOf<String>()

    val ownerSpaces: StateFlow<List<SpaceListing>> = combine(spaces, currentUser) { list, user ->
        if (user == null) emptyList()
        else if (user.role == UserRole.ADMIN) list.filter { it.id !in _pendingDeletionIds }
        else list.filter { it.ownerId == user.id && it.id !in _pendingDeletionIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Practitioner active & past bookings
    val practitionerBookings: StateFlow<List<RentalBookingRequest>> = combine(bookingRequests, currentUser) { list, user ->
        if (user == null) emptyList()
        else list.filter { it.practitionerId == user.id || it.practitionerEmail.equals(user.email, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Owner incoming booking requests — same ownerId-only fix as ownerSpaces above;
    // mySpaceIds no longer needs the substring/phone fallback since it now only
    // ever contains listings this exact user genuinely owns.
    val ownerIncomingRequests: StateFlow<List<RentalBookingRequest>> = combine(bookingRequests, currentUser, spaces) { requests, user, allSpaces ->
        if (user == null) emptyList()
        else if (user.role == UserRole.ADMIN) requests
        else {
            val mySpaceIds = allSpaces.filter { it.ownerId == user.id }.map { it.id }.toSet()
            requests.filter { it.ownerId == user.id || mySpaceIds.contains(it.spaceId) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search/filter state (query, governorate, space type, formula, price, etc.) and
    // the resulting filtered-spaces list used to be duplicated here — an independently
    // maintained copy of exactly what DiscoveryViewModel already did, since
    // DiscoveryScreen (this block's only real caller) was never actually wired onto
    // DiscoveryViewModel. It now is (see DiscoveryScreen.kt) — this whole block is
    // gone, not just left dead, per the ViewModel-split effort.

    // Admin pricing/listing governance (setSubscriptionFee, resetSubscriptionFeeBaseline,
    // toggleListingVerification, toggleListingActive) used to be duplicated here — dead
    // leftovers from before AdminViewModel existed, still called from the "admin_forecast"
    // drawer dialog (a fully unreachable duplicate of AdminConsoleScreen's own Revenue &
    // Run-Rate tab), silently discarding the Boolean result with no success/failure
    // feedback of any kind. Both the dialog and these wrappers are removed; use
    // AdminViewModel's checked equivalents instead.

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

    fun initPlayBilling(context: Context) {
        if (playBillingManager == null) {
            val manager = com.example.data.billing.PlayBillingManager(context.applicationContext, viewModelScope)
            playBillingManager = manager
            viewModelScope.launch {
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
            viewModelScope.launch {
                try {
                    manager.productDetailsList.collect { products ->
                        playBillingProducts.value = products
                        // Retry a deferred billing launch once the target product is available
                        val retryPlan = _pendingRetryBasePlanId
                        val retryActivity = _pendingRetryActivity?.get()
                        if (retryPlan != null && retryActivity != null &&
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
            viewModelScope.launch {
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
            viewModelScope.launch {
                try {
                    manager.activePurchases.collect { playActivePurchases.value = it }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("ProHostVM", "Operation failed", e)
                }
            }
            viewModelScope.launch {
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

    // Clears billingActivationPending once the Firestore listener reflects the RTDN
    // grant — i.e. ownerPackageExpiryMillis changed from what it was at launch time.
    init {
        viewModelScope.launch {
            try {
                currentUser.collectLatest { user ->
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

    // Mirrors the device's analytics choice onto the profile: server-side Measurement
    // Protocol events (functions/src/lib/ga4.ts) only send for GRANTED, and need the
    // app-instance id to join the same GA4 user.
    init {
        viewModelScope.launch {
          try {
            var lastWritten: String? = null
            combine(
                currentUser.map { it?.id }.distinctUntilChanged(),
                com.example.analytics.AnalyticsConsent.state
            ) { uid, consent -> uid to consent }.collectLatest { (uid, consent) ->
                if (uid == null || consent == com.example.analytics.ConsentState.UNKNOWN) return@collectLatest
                val instanceId = if (consent == com.example.analytics.ConsentState.GRANTED) {
                    kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                        com.example.analytics.AnalyticsTracker.fetchAppInstanceId { id -> if (cont.isActive) cont.resumeWith(Result.success(id)) }
                    }
                } else null
                val key = "$uid|$consent|$instanceId"
                if (key == lastWritten) return@collectLatest
                try {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("user_profiles").document(uid)
                        .update(
                            mapOf(
                                "analyticsConsent" to consent.name,
                                "analyticsConsentAtMillis" to com.example.analytics.AnalyticsConsent.decidedAtMillis,
                                "gaAppInstanceId" to instanceId
                            )
                        ).await()
                    lastWritten = key
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("ProHostVM", "Analytics consent mirror failed", e)
                }
            }
          } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
          } catch (e: Exception) {
            android.util.Log.w("ProHostVM", "Analytics consent observer stopped", e)
          }
        }
    }

    // currentUser.role follows user_profiles live, but firestore.rules checks that read
    // the token's role claim only see an admin grant/revocation after a token refresh.
    init {
        viewModelScope.launch {
            var lastRole: UserRole? = null
            currentUser.collectLatest { user ->
                val role = user?.role
                if (role != null && lastRole != null && role != lastRole) {
                    try {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("ProHostVM", "Token refresh after role change failed: ${e.message}")
                    }
                }
                lastRole = role
            }
        }
    }

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
        billingActivationTimeoutJob = viewModelScope.launch {
            runCatching { kotlinx.coroutines.delay(2 * 60 * 1000L) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            _billingActivationPending.value = false
        }
    }

    // A checkout whose server activation failed (it is parked and retried server-side):
    // the profile listener completes the role upgrade when the grant arrives.
    private var awaitingServerActivation = false

    private val _kycPromptAfterActivation = MutableStateFlow(false)
    /** True once a plan activates for someone whose KYC is incomplete (e.g. a promo redemption). */
    val kycPromptAfterActivation: StateFlow<Boolean> = _kycPromptAfterActivation.asStateFlow()
    fun consumeKycPromptAfterActivation() { _kycPromptAfterActivation.value = false }

    private suspend fun onPlanActivated(message: String) {
        awaitingServerActivation = false
        dismissBillingActivationPending()
        _billingError.value = null
        _billingSuccess.value = message
        // Force-refresh the ID token so the new PRO_HOST claim takes effect immediately.
        refreshCurrentUserRoleAfterEntitlement()
        if (currentUser.value?.isKycComplete == false) _kycPromptAfterActivation.value = true
    }

    fun resendEmailVerification(context: android.content.Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val result = functionsClient.resendEmailVerification()
                val msg = if (result.isSuccess) {
                    "Verification email sent. Check your inbox."
                } else {
                    result.exceptionOrNull()?.toUserMessage("Couldn't send the verification email. Please try again.")
                        ?: "Couldn't send the verification email. Please try again."
                }
                android.widget.Toast.makeText(appContext, msg, android.widget.Toast.LENGTH_LONG).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(appContext, e, "Couldn't send the verification email. Please try again.")
            }
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
        val draftId = _pendingAutoPublishDraftId.value
        if (draftId != null) {
            viewModelScope.launch {
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
            val currentPlan = currentUser.value?.ownerPackageId
            val oldPurchaseToken = if (currentPlan != null && currentPlan != basePlanId &&
                currentPlan in com.example.data.billing.PlayCatalog.BASE_PLANS) {
                manager.activePurchases.value.firstOrNull { productId in it.products }?.purchaseToken
            } else null

            _billingPriorExpiryMillis.value = currentUser.value?.ownerPackageExpiryMillis
            // The "Activating" banner waits for Play's PURCHASED result (purchaseEvents).
            manager.launchSubscriptionPurchase(activity, product, userId = uid, basePlanId = basePlanId, oldPurchaseToken = oldPurchaseToken)
        } else {
            // Store the intent and retry automatically once products load from Play
            _pendingRetryBasePlanId = basePlanId
            _pendingRetryActivity = java.lang.ref.WeakReference(activity)
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
        playBillingManager?.showInAppMessages(activity) {
            refreshPlayPurchases(activity)
        }
    }

    private fun openUriOrToast(activity: android.app.Activity, uri: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(activity, "No app available to open this link.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Fetches a single Play subscription product by its Play Console product ID. Used by the
     * Admin Console "Refresh from Play" button to auto-populate plan name and price from the
     * live Play Store catalog. Initialises the billing client if not already connected.
     * Callback is invoked on the main thread.
     */
    fun fetchPlayProductDetails(context: Context, productId: String, onResult: (com.android.billingclient.api.ProductDetails?) -> Unit) {
        val manager = playBillingManager ?: run { initPlayBilling(context); playBillingManager }
        manager?.queryProductDetailsForId(productId, onResult) ?: onResult(null)
    }

    private var restoreCheckJob: kotlinx.coroutines.Job? = null
    // Purchase tokens already sent for server activation this session.
    private val processedPurchaseTokens = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** [userInitiated] (the Restore button) reports every outcome; the silent on-open check only reports a restore. */
    fun refreshPlayPurchases(context: Context, userInitiated: Boolean = false) {
        val manager = playBillingManager ?: run { initPlayBilling(context); playBillingManager } ?: return
        // If the user already has a Firestore entitlement, just refresh the local
        // purchases cache — no server call needed.
        if (currentUser.value?.ownerPackageId != null) {
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
        restoreCheckJob = viewModelScope.launch {
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
                if (!processedPurchaseTokens.add(activePurchase.purchaseToken) && !userInitiated) return@runCatching
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


    // Set by OwnerHubScreen right before redirecting to Subscriptions after a
    // NoActivePackage Publish rejection — the id of
    // the Draft that was just saved in place of the blocked Publish attempt.
    // OwnerSubscriptionsScreen reads this (same shared ViewModel instance across
    // both screens) to surface a banner and thread the id into whichever purchase
    // the host makes next, so it auto-publishes without a second trip through the
    // wizard. Purely a client-side UX convenience — the real correlation once a
    // payment is launched is pendingPlayPublishDraftId, read by playBillingRtdn.
    private val _pendingAutoPublishDraftId = MutableStateFlow<String?>(null)
    val pendingAutoPublishDraftId: StateFlow<String?> = _pendingAutoPublishDraftId.asStateFlow()

    fun setPendingAutoPublishDraft(draftId: String?) {
        _pendingAutoPublishDraftId.value = draftId
    }

    private fun clearPendingAutoPublishDraft() {
        _pendingAutoPublishDraftId.value = null
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

    // payBookingViaWhish (booking rent settlement inside the app) is gone —
    // Specialist and Pro Host settle rent entirely outside the app now. Host
    // acceptance (see acceptBookingRequest below) records the deal instead: a
    // signed leasing agreement uploaded to Storage, not a payment flag.

    private val _topHashtags = MutableStateFlow<List<String>>(emptyList())
    val topHashtags: StateFlow<List<String>> = _topHashtags.asStateFlow()

    /** Called once by OwnerHubScreen right before opening CreateListingDialog — a
     *  fixed snapshot for that session of the wizard, not a live subscription. */
    fun refreshTopHashtags() {
        viewModelScope.launch {
            try {
                _topHashtags.value = repository.fetchTopHashtags()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ProHostVM", "Operation failed", e)
            }
        }
    }

    fun addUserSuggestedSchemaItem(
        category: String,
        name: String,
        scopedToIds: List<String> = emptyList(),
        amenityGroup: String = ""
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val item = SchemaItem(
            id = "USR-" + category.take(3) + "-" + System.currentTimeMillis().toString().takeLast(6),
            name = trimmed,
            category = category,
            isSystemDefault = false,
            isEnabled = true,
            scopedToIds = scopedToIds,
            amenityGroup = amenityGroup
        )
        viewModelScope.launch {
            try {
                repository.addUserSuggestedSchemaItem(item)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ProHostVM", "Operation failed", e)
            }
        }
    }

    // --- Space Owner Listing Creation ---
    suspend fun createNewSpaceListing(listing: SpaceListing): ListingCreateResult {
        val user = currentUser.value
        // ADMIN never purchases/holds a real package at all (grantAdminRole/
        // bootstrapSuperAdmin only ever set role) — matching OwnerHubScreen's own
        // unconditional "unlimited access" treatment for Admin rather than the gate
        // below, which exists to meter a real host's purchased package, not to block
        // an account that never has one.
        val isAdmin = user?.role == UserRole.ADMIN

        // Publishing straight to ACTIVE requires an active, non-expired subscription
        // (firestore.rules' hasActivePackage() enforces the same rule); Drafts are exempt.
        if (!isAdmin && listing.status == ListingStatus.ACTIVE && !hasActivePackage(user)) {
            repository.addAuditLog(
                actionType = "LISTING_BLOCKED_NO_PACKAGE",
                details = "Owner attempted to publish without an active subscription.",
                severity = "WARN"
            )
            com.example.analytics.AnalyticsTracker.listingPublishBlocked("no_active_package")
            return ListingCreateResult.NoActivePackage
        }

        // Reports the real Firestore result now — this used to return an
        // unconditional true for a listing that was never actually persisted.
        val saved = repository.addSpaceListing(listing)
        if (saved) {
            if (listing.status == ListingStatus.ACTIVE) com.example.analytics.AnalyticsTracker.listingPublish(listing)
            else com.example.analytics.AnalyticsTracker.listingDraftSaved(listing)
        } else {
            com.example.analytics.AnalyticsTracker.appError("listing_publish", "write_failed")
        }
        return if (saved) ListingCreateResult.Success else ListingCreateResult.Failed
    }

    /** True when the signed-in host can't publish (no active subscription). Admins never need one. */
    fun needsActivePackage(): Boolean {
        val user = currentUser.value ?: return false
        if (user.role == UserRole.ADMIN) return false
        return !hasActivePackage(user)
    }

    private fun hasActivePackage(user: AppUser?): Boolean {
        if (user?.ownerPackageId == null) return false
        val expiry = user.ownerPackageExpiryMillis
        return expiry == null || expiry > System.currentTimeMillis()
    }

    // An owner had no in-app way to correct a mistake in, or take down, their own
    // published listing — updateSpaceListing/deleteSpaceListing were only ever called
    // from the Admin Console. Firestore rules already permit the owning user to update/
    // delete their own workspace_listings document directly, so this just exposes the
    // existing repository methods (which already safely preserve isVerified/
    // isActiveSubscription/subscriptionExpiryMillis/ownerId regardless of caller).
    suspend fun updateOwnerListing(updated: SpaceListing): Boolean {
        return repository.updateSpaceListing(updated).also { ok ->
            if (ok) com.example.analytics.AnalyticsTracker.listingUpdate(updated)
        }
    }

    suspend fun deleteOwnerListing(spaceId: String): Boolean {
        _pendingDeletionIds.add(spaceId)
        val result = repository.deleteSpaceListing(spaceId)
        if (!result) _pendingDeletionIds.remove(spaceId) else com.example.analytics.AnalyticsTracker.listingDelete()
        return result
    }

    // Sign-in/registration (phone OTP + Google Sign-In) moved to AuthViewModel —
    // see its doc comment. Every method/state field there had exactly one caller
    // (LoginAuthScreen) before this move.

    // registerMember(...)/login(...) synchronous wrappers were removed here — both let a
    // caller hand in an arbitrary role with zero server verification (the exact bug this
    // whole auth rewrite exists to close). Registration/sign-in now only ever happens
    // through the phone-verification flow above (or Google Sign-In, itself gated on
    // completing that same phone verification), which resolve role via Firebase Auth +
    // Cloud Functions custom claims.
    //
    // switchUserRole(...) was also removed — it let any already-logged-in user instantly
    // become ADMIN locally with no server check. A real role change now only happens via
    // grantAdminRole() (Admin-to-Admin grants) or grantEntitlement() promoting a SPECIALIST
    // to PRO_HOST the moment their Google Play subscription activates — never a free,
    // client-invocable "upgrade" call.

    override fun onCleared() {
        super.onCleared()
        playBillingManager?.endConnection()
    }

    fun logout() {
        com.example.analytics.AnalyticsTracker.logout()
        // Run in viewModelScope so repository.logout() (suspend) completes the
        // FCM-token-clear write *before* signOut() invalidates the auth context —
        // previously repository.logout() launched a fire-and-forget coroutine that
        // raced against signOut() and the write often arrived with no auth.
        viewModelScope.launch {
            runCatching {
                repository.logout()
            }.onFailure { e ->
                android.util.Log.w("ProHostViewModel", "logout error: ${e.message}")
            }
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        }
    }

    /**
     * Permanently deletes the signed-in user's own account (functions/src/roles/
     * deleteOwnAccount.ts) — their owned listings, uploaded documents, Firestore
     * profile, and Firebase Auth account itself. Only signs the client out
     * locally once the server confirms the account is actually gone; on failure
     * the caller is still signed in and nothing has changed, so it's safe to
     * show an error and let the user retry.
     */
    suspend fun deleteAccount(): Result<Unit> {
        val result = functionsClient.deleteOwnAccount()
        if (result.isSuccess) {
            com.example.analytics.AnalyticsTracker.accountDeleted()
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            // The profile doc (and its fcmToken field) is already gone server-side —
            // skip the token-clear write here, since it would otherwise just
            // resurrect a stub user_profiles/{uid} doc post-deletion.
            repository.logout(clearRemotePushToken = false)
        }
        return result
    }

    fun logSecurityAction(actionType: String, details: String, severity: String = "INFO") {
        repository.addAuditLog(actionType, details, severity)
    }

    suspend fun updateProfile(
        name: String,
        specialty: String,
        phone: String,
        country: String,
        governorate: String,
        city: String,
        profilePictureUrl: String? = null
    ): Boolean {
        return repository.updateCurrentUserProfile(name, specialty, phone, country, governorate, city, profilePictureUrl)
    }

    // Fires exactly once per space-detail view (call from a LaunchedEffect(space.id),
    // not on every recomposition) — real engagement data replacing the old fixed 850
    // placeholder.
    fun registerSpaceView(spaceId: String, subdivisionId: String? = null) {
        repository.incrementSpaceViewCount(spaceId)
        spaces.value.firstOrNull { it.id == spaceId }?.let { space ->
            com.example.analytics.AnalyticsTracker.viewItem(space, space.subdivisions.firstOrNull { it.id == subdivisionId })
        }
    }

    // --- WhatsApp Direct Connection ---
    fun launchWhatsAppInquiry(
        context: Context,
        space: SpaceListing,
        selectedFormula: RentalFormula? = null,
        request: RentalBookingRequest? = null,
        subdivision: Subdivision? = null
    ) {
        val user = currentUser.value
        val professionalName = user?.fullName?.takeIf { it.isNotBlank() } ?: "a ProHost specialist"
        val specialty = user?.specialty?.takeIf { it.isNotBlank() } ?: "Independent Specialist"
        val cleanPhone = formatWhatsAppNumber(space.ownerPhone)
        if (cleanPhone.isBlank()) {
            Toast.makeText(context, "This host hasn't added a WhatsApp number yet.", Toast.LENGTH_SHORT).show()
            return
        }

        val attendeeLine = request?.let { com.example.ui.util.AttendeePricing.bookingSummary(it) }
        val priceLabel = when {
            attendeeLine != null -> "Per attendee · $attendeeLine"
            subdivision != null -> {
                val price = com.example.ui.util.SpaceCalculationUtils.findLowestPriceForSubdivision(subdivision)
                if (price.amount > 0.0) "From \$${price.amount.toInt()}${price.unitLabel}" else subdivision.pricing.strategyType.displayName
            }
            selectedFormula != null -> {
                val unit = com.example.ui.util.SpaceCalculationUtils.rateUnitLabel(selectedFormula.type)
                "\$${selectedFormula.rateUsd.toInt()}$unit"
            }
            else -> {
                val price = com.example.ui.util.SpaceCalculationUtils.findLowestConfiguredPrice(space)
                "From \$${price.amount.toInt()}${price.unitLabel}"
            }
        }
        val roomLabel = subdivision?.name ?: selectedFormula?.scheduleDescription?.takeIf { it.isNotBlank() }

        val bookingDetails = if (request != null) {
            val daysStr = request.selectedDays.takeIf { it.isNotEmpty() }?.joinToString() ?: request.formula.daysOfWeek.joinToString()
            val timesStr = if (request.selectedStartHour.isNotBlank() && request.selectedEndHour.isNotBlank())
                "${request.selectedStartHour}–${request.selectedEndHour}"
            else "${request.formula.startHour}–${request.formula.endHour}"
            val shiftStr = request.selectedShift.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
            val durationStr = if (request.formula.type == RentalFormulaType.FULL_MONTH)
                " · ${request.durationMonths} month${if (request.durationMonths > 1) "s" else ""}" else ""
            buildString {
                appendLine()
                appendLine()
                appendLine("*Booking details:*")
                if (request.displayCode.isNotBlank()) appendLine("• Request: ${request.displayCode}")
                appendLine("• Schedule: $daysStr @ $timesStr$shiftStr")
                appendLine("• Start: ${request.startDate}$durationStr")
                if (attendeeLine != null) appendLine("• Attendees: $attendeeLine")
                appendLine("• Total: \$${request.totalAmountUsd.toInt()} USD")
                if (request.clinicalNotes.isNotBlank()) appendLine("• Notes: ${request.clinicalNotes}")
            }.trimEnd()
        } else ""

        val rawMessage = buildString {
            appendLine("Hi ${space.ownerName} 👋")
            appendLine()
            appendLine("I'm *$professionalName* ($specialty) and I found your space on ProHost.")
            appendLine()
            appendLine("*Space:* ${space.title}")
            appendLine("*Location:* ${space.district}, ${space.governorate.displayName}")
            if (roomLabel != null) appendLine("*Room / Area:* $roomLabel")
            appendLine("*Price:* $priceLabel")
            append(bookingDetails)
            appendLine()
            appendLine()
            append("I'd love to discuss availability and next steps. Listing ref: ProHost ${space.publicCode}")
        }

        try {
            val encoded = URLEncoder.encode(rawMessage, "UTF-8")
            val url = "https://wa.me/$cleanPhone?text=$encoded"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
            com.example.analytics.AnalyticsTracker.generateLead(space, subdivision)
            repository.incrementSpaceInquiryCount(space.id)
            repository.addAuditLog(
                actionType = "WHATSAPP_INQUIRY_SPECIALIST_TO_HOST",
                details = "${professionalName} contacted host ${space.ownerName} via WhatsApp about listing '${space.title}' (${space.id})" +
                    (request?.let { " regarding booking #${it.id}" } ?: ""),
                severity = "INFO"
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch WhatsApp. Showing copied message.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchWhatsAppToPractitioner(context: Context, request: RentalBookingRequest) {
        val user = currentUser.value
        val ownerName = user?.fullName ?: "Workspace Host"
        val daysStr = if (request.selectedDays.isNotEmpty()) request.selectedDays.joinToString() else request.formula.daysOfWeek.joinToString()
        val timesStr = if (request.selectedStartHour.isNotBlank() && request.selectedEndHour.isNotBlank()) {
            "${request.selectedStartHour} - ${request.selectedEndHour}"
        } else {
            "${request.formula.startHour} - ${request.formula.endHour}"
        }

        val rawMessage = "Hello ${request.practitionerName},\n\n" +
                "I am $ownerName regarding your booking request (${request.publicCode}) for space \"${request.spaceTitle}\".\n" +
                "Requested Schedule: $daysStr @ $timesStr (${request.formula.type.displayName}).\n" +
                "Status: ${request.status.displayName}\n\n" +
                "Let's discuss onboarding and walk-through details."

        val cleanPhone = formatWhatsAppNumber(request.practitionerPhone)
        if (cleanPhone.isBlank()) {
            Toast.makeText(context, "This professional hasn't added a WhatsApp number yet.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val encoded = URLEncoder.encode(rawMessage, "UTF-8")
            val url = "https://wa.me/$cleanPhone?text=$encoded"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
            com.example.analytics.AnalyticsTracker.contactSpecialist()
            repository.addAuditLog(
                actionType = "WHATSAPP_INQUIRY_HOST_TO_SPECIALIST",
                details = "$ownerName contacted specialist ${request.practitionerName} via WhatsApp about booking #${request.id} ('${request.spaceTitle}')",
                severity = "INFO"
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch WhatsApp.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Digits for a wa.me link, or "" when there is no usable number. */
    fun formatWhatsAppNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 7) return ""
        // Already international (E.164 "+…" or "00…" prefix) — use as-is.
        if (raw.trim().startsWith("+")) return digits
        if (digits.startsWith("00")) return digits.removePrefix("00")
        if (digits.startsWith("961") && digits.length >= 11) {
            return digits
        }
        if (digits.startsWith("00961") && digits.length >= 13) {
            return digits.removePrefix("00")
        }
        if (digits.startsWith("0") && digits.length > 1) {
            val trimmed = digits.dropWhile { it == '0' }
            return "961$trimmed"
        }
        if (digits.length in 7..8) {
            return "961$digits"
        }
        return digits
    }

    // --- In-App Rental Request Engine ---
    /**
     * Fire-and-forget from the caller's point of view (RentalBookingDialog calls this
     * from a plain onClick and dismisses immediately), but the actual Firestore write
     * is now awaited internally rather than launched fire-and-forget — this used to
     * show "Rental Request Sent!" unconditionally the instant the local, in-memory
     * copy was created, whatever the real sync outcome. A specialist on a bad
     * connection saw confirmed success for a request that never reached Firestore —
     * and therefore never reached the host — with no indication anything was wrong.
     * Now the toast reflects the real result: a synced request says so plainly; an
     * unsynced one is flagged as still pending and not yet visible to the host.
     */
    data class BookingSubmissionRequest(
        val formula: RentalFormula,
        val startDate: String,
        val durationMonths: Int,
        val notes: String,
        val alsoOpenWhatsApp: Boolean = false,
        val selectedDays: List<String> = emptyList(),
        val selectedCalendarDates: List<String> = emptyList(),
        val selectedStartHour: String = "",
        val selectedEndHour: String = "",
        val selectedShift: String = "",
        val calculatedTotalUsd: Double = 0.0,
        val subdivisionId: String? = null,
        val subdivisionName: String? = null,
        val replacesBookingId: String? = null,
        val attendeeCount: Int = 0,
        val selectedAttendeePackageId: String? = null,
        val attendeePackageName: String? = null,
        val attendeePackagePriceUsd: Double = 0.0
    )

    fun submitBookingRequest(
        space: SpaceListing,
        context: Context,
        submission: BookingSubmissionRequest
    ) {
        val formula = submission.formula
        val startDate = submission.startDate
        val durationMonths = submission.durationMonths
        val notes = submission.notes
        val alsoOpenWhatsApp = submission.alsoOpenWhatsApp
        val selectedDays = submission.selectedDays
        val selectedCalendarDates = submission.selectedCalendarDates
        val selectedStartHour = submission.selectedStartHour
        val selectedEndHour = submission.selectedEndHour
        val selectedShift = submission.selectedShift
        val calculatedTotalUsd = submission.calculatedTotalUsd
        val subdivisionId = submission.subdivisionId
        val subdivisionName = submission.subdivisionName
        val replacesBookingId = submission.replacesBookingId
        val attendeeCount = submission.attendeeCount
        val selectedAttendeePackageId = submission.selectedAttendeePackageId
        val attendeePackageName = submission.attendeePackageName
        val attendeePackagePriceUsd = submission.attendeePackagePriceUsd
        val user = currentUser.value
        if (user == null) {
            Toast.makeText(context, "Please log in to submit a rental request", Toast.LENGTH_SHORT).show()
            return
        }
        if (user.profilePictureUrl.isNullOrBlank()) {
            Toast.makeText(context, "Please add a profile picture before making booking requests.", Toast.LENGTH_LONG).show()
            return
        }

        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val (request, synced) = repository.createBookingRequest(
                    space = space,
                    formula = formula,
                    practitioner = user,
                    startDate = startDate,
                    durationMonths = durationMonths,
                    notes = notes,
                    selectedDays = selectedDays,
                    selectedCalendarDates = selectedCalendarDates,
                    selectedStartHour = selectedStartHour,
                    selectedEndHour = selectedEndHour,
                    selectedShift = selectedShift,
                    calculatedTotalUsd = calculatedTotalUsd,
                    subdivisionId = subdivisionId,
                    subdivisionName = subdivisionName,
                    replacesBookingId = replacesBookingId,
                    attendeeCount = attendeeCount,
                    selectedAttendeePackageId = selectedAttendeePackageId,
                    attendeePackageName = attendeePackageName,
                    attendeePackagePriceUsd = attendeePackagePriceUsd
                )
                if (synced) {
                    com.example.analytics.AnalyticsTracker.bookingRequest(
                        space = space,
                        sub = space.subdivisions.firstOrNull { it.id == subdivisionId },
                        valueUsd = calculatedTotalUsd,
                        strategy = formula.type.name,
                        attendeeCount = attendeeCount.takeIf { it > 0 },
                        isRebook = replacesBookingId != null
                    )
                } else {
                    com.example.analytics.AnalyticsTracker.bookingRequestFailed("offline")
                }

                Toast.makeText(
                    appContext,
                    if (synced) {
                        if (replacesBookingId != null) {
                            "Edit request sent! Your current booking stays active until the host approves this change."
                        } else {
                            "Rental request sent! Space hours remain open until owner approval."
                        }
                    } else {
                        "Couldn't reach the server to send your request — check your connection and try again. The host has not been notified."
                    },
                    Toast.LENGTH_LONG
                ).show()

                if (alsoOpenWhatsApp) {
                    launchWhatsAppInquiry(context, space, formula, request)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ProHostVM", "submitBookingRequest error", e)
                com.example.analytics.AnalyticsTracker.bookingRequestFailed(com.example.analytics.AnalyticsTracker.errorCode(e))
                Toast.makeText(appContext, "Failed to send request — please check your connection and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Finalizes host acceptance: uploads the signed agreement the host just picked
     * ([agreementUri]) to Storage, then accepts the request with that URL attached
     * (see ProHostRepository.acceptBookingRequest — this is also what releases a
     * previously accepted booking this request replaces, if any). The practitioner
     * hears about it via a real server-sent push (see
     * functions/src/notifications/bookingNotifications.ts), not a local alert on
     * this device, so nothing needs to be posted here.
     */
    fun acceptBookingRequest(context: Context, requestId: String, agreementUri: Uri? = null) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                // Refuse before the upload: accepting this would double-book a slot an
                // ACCEPTED booking already holds. Named so the host knows which one.
                val requestCode = bookingRequests.value.firstOrNull { it.id == requestId }?.publicCode
                    ?: publicCode("", requestId)
                repository.findAcceptConflict(requestId)?.let { clash ->
                    Toast.makeText(
                        appContext,
                        "Can't accept $requestCode — it overlaps accepted booking ${clash.publicCode} " +
                            "(${clash.practitionerName}, ${clash.selectedDateTimeRange}).",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                val agreementUrl = if (agreementUri != null) {
                    val storageService = com.example.data.storage.FirebaseStorageService.getInstance()
                    val ext = guessFileExtension(context, agreementUri, "pdf")
                    storageService.uploadBookingAgreement(requestId, agreementUri, ext).also {
                        if (it == null) {
                            Toast.makeText(appContext, "Could not upload space rules. Please try again.", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                    }
                } else null
                val success = repository.acceptBookingRequest(requestId, agreementUrl)
                if (success) {
                    bookingRequests.value.firstOrNull { it.id == requestId }.let { b ->
                        com.example.analytics.AnalyticsTracker.bookingAccepted(b?.totalAmountUsd, b?.formula?.type?.name)
                    }
                    val msg = if (agreementUrl != null) "Booking accepted! Space rules shared." else "Booking accepted."
                    Toast.makeText(appContext, "$requestCode — $msg", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(appContext, "Could not finalize acceptance. Please try again.", Toast.LENGTH_LONG).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ProHostVM", "acceptBookingRequest error", e)
                Toast.makeText(appContext, "Failed to accept booking — please check your connection and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Real cross-device push reminder (functions/src/notifications/sendPaymentReminder.ts)
     * — this used to just call [postNotificationAlert], which only ever updated the
     * host's own device's alert tray and never reached the specialist at all.
     */
    fun sendPaymentReminder(bookingId: String, practitionerName: String, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val result = functionsClient.sendPaymentReminder(bookingId)
                if (result.isSuccess) {
                    com.example.analytics.AnalyticsTracker.paymentReminderSent()
                    Toast.makeText(appContext, "Payment Reminder Sent to $practitionerName!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(appContext, "Could not send the reminder. Please try again.", Toast.LENGTH_LONG).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ProHostVM", "sendPaymentReminder error", e)
                Toast.makeText(appContext, "Failed to send reminder — please check your connection and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun rejectBookingRequest(requestId: String, note: String? = null, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val success = repository.rejectBookingRequest(requestId, note)
                if (success) {
                    com.example.analytics.AnalyticsTracker.bookingRejected(bookingRequests.value.firstOrNull { it.id == requestId }?.formula?.type?.name)
                    Toast.makeText(appContext, "Booking Request #${requestId} Declined. Space hours remain available.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appContext, "Could not decline the request — check your connection and try again.", Toast.LENGTH_LONG).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ProHostVM", "rejectBookingRequest error", e)
                Toast.makeText(appContext, "Failed to decline request — please check your connection and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun cancelBookingRequest(requestId: String, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val success = repository.cancelBookingRequest(requestId)
                if (success) {
                    com.example.analytics.AnalyticsTracker.bookingCancelled(by = "specialist", reasonCode = "pending_withdrawn")
                    Toast.makeText(appContext, "Booking Request #${requestId} Cancelled", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appContext, "Could not cancel the request — check your connection and try again.", Toast.LENGTH_LONG).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ProHostVM", "cancelBookingRequest error", e)
                Toast.makeText(appContext, "Failed to cancel request — please check your connection and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Early termination of an already-ACCEPTED booking — see
     * ProHostRepository.cancelAcceptedBooking's doc comment. Callable from either
     * side (My Bookings for the practitioner, Renting Progress for the host); the
     * caller only needs to be signed in as one of the booking's two parties, or Admin.
     */
    fun cancelAcceptedBooking(
        requestId: String,
        reasonCode: CancellationReasonCode,
        note: String?,
        context: Context
    ) {
        val user = currentUser.value ?: return
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val success = repository.cancelAcceptedBooking(
                    requestId = requestId,
                    reasonCode = reasonCode,
                    note = note,
                    cancelledByUid = user.id,
                    cancelledByRole = user.role.name
                )
                if (success) com.example.analytics.AnalyticsTracker.bookingCancelled(
                    by = if (user.role == UserRole.SPECIALIST) "specialist" else "host",
                    reasonCode = reasonCode.name
                )
                Toast.makeText(
                    appContext,
                    if (success) "Booking cancelled. The other party has been notified." else "Could not cancel this booking — please try again.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ProHostVM", "cancelAcceptedBooking error", e)
                Toast.makeText(appContext, "Failed to cancel booking — please check your connection and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** "Mark as Paid" — record-keeping only; asHost decides which side's own flag gets set. */
    fun acknowledgePayment(requestId: String, asHost: Boolean, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val success = repository.acknowledgePayment(requestId, asHost)
                if (success) {
                    val amount = bookingRequests.value.firstOrNull { it.id == requestId }?.totalAmountUsd
                    com.example.analytics.AnalyticsTracker.paymentAcknowledged(amount)
                }
                if (!success) {
                    Toast.makeText(appContext, "Couldn't save that — please try again.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(appContext, e, "Couldn't save that — please try again.")
            }
        }
    }

    /**
     * Saves whichever verification document the host just uploaded and requests
     * the Listing Verified badge. Returns true on success; the caller is responsible
     * for dismissing the dialog (on success) or showing an inline retry error (on failure).
     */
    suspend fun requestListingVerification(
        spaceId: String,
        docUrl: String,
        docType: ListingVerificationDocType
    ): Boolean = repository.requestOwnListingVerification(spaceId, docUrl, docType).also { ok ->
        if (ok) com.example.analytics.AnalyticsTracker.listingVerificationRequest()
    }

    /** Toggles [spaceId] in the current user's personal saved/favorites list. */
    fun toggleSavedSpace(spaceId: String) {
        val wasSaved = currentUser.value?.savedSpaceIds?.contains(spaceId) == true
        viewModelScope.launch {
            try {
                repository.toggleSavedSpace(spaceId)
                spaces.value.firstOrNull { it.id == spaceId }?.let { com.example.analytics.AnalyticsTracker.wishlist(it, added = !wasSaved) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ProHostVM", "Operation failed", e)
            }
        }
    }

    /** Saves (or re-saves) a Draft listing — never blocked by the package listing limit. */
    suspend fun saveListingDraft(listing: SpaceListing): Boolean {
        return repository.saveListingDraft(listing).also { ok ->
            if (ok) com.example.analytics.AnalyticsTracker.listingDraftSaved(listing)
        }
    }

    /** Pause/Resume a published listing, or publish a Draft — see setListingStatus's doc comment. */
    fun setListingStatus(spaceId: String, status: ListingStatus, context: Context) {
        val appContext = context.applicationContext
        val previous = spaces.value.firstOrNull { it.id == spaceId }?.status
        viewModelScope.launch {
            try {
                val success = repository.setListingStatus(spaceId, status)
                if (success) com.example.analytics.AnalyticsTracker.listingStatusChange(previous?.name, status.name)
                if (!success) {
                    Toast.makeText(appContext, "Couldn't update this listing — please try again.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(appContext, e, "Couldn't update this listing — please try again.")
            }
        }
    }

    // --- Schedule & Blackout Management ---
    fun addBlackoutSlot(spaceId: String, dayOfWeek: String, startTime: String, endTime: String, reason: String, context: Context) {
        // Id left to BlackoutSlot's own UUID default: the availability editor can
        // now create one slot per hour, and a 3-digit random id would collide.
        val slot = BlackoutSlot(
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            endTime = endTime,
            reason = reason
        )
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val success = repository.addBlackoutSlot(spaceId, slot)
                if (success) com.example.analytics.AnalyticsTracker.blackoutAdd()
                Toast.makeText(
                    appContext,
                    if (success) "$dayOfWeek $startTime - $endTime is no longer offered" else "Couldn't switch that slot off — please try again",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(appContext, e, "Couldn't switch that slot off — please try again.")
            }
        }
    }

    fun removeBlackoutSlot(spaceId: String, slotId: String, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val success = repository.removeBlackoutSlot(spaceId, slotId)
                Toast.makeText(
                    appContext,
                    if (success) "Slot is back on offer" else "Couldn't switch that slot on — please try again",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(appContext, e, "Couldn't switch that slot on — please try again.")
            }
        }
    }

    fun updateSpaceOperatingSchedule(
        spaceId: String,
        openingHour: String,
        closingHour: String,
        operatingDays: List<String>,
        isSundayOperating: Boolean,
        context: Context
    ) {
        // Used to return silently here, so a missing space produced no feedback at
        // all — indistinguishable from the button doing nothing.
        val currentSpace = spaces.value.find { it.id == spaceId }
        if (currentSpace == null) {
            Toast.makeText(context, "Couldn't load this listing — reopen it and try again", Toast.LENGTH_SHORT).show()
            return
        }
        val updatedSchedule = currentSpace.schedule.copy(
            openingHour = openingHour,
            closingHour = closingHour,
            operatingDays = operatingDays,
            isSundayOperating = isSundayOperating
        )
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val success = repository.updateSpaceSchedule(spaceId, updatedSchedule)
                if (success) com.example.analytics.AnalyticsTracker.scheduleUpdate()
                Toast.makeText(
                    appContext,
                    if (success) "Operating schedule updated!" else "Failed to update schedule — please try again",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(appContext, e, "Failed to update schedule — please try again.")
            }
        }
    }

    data class CustomFormulaSpec(
        val type: RentalFormulaType,
        val rateUsd: Double,
        val description: String,
        val daysOfWeek: List<String>,
        val startHour: String,
        val endHour: String,
        val weeklyHours: Int,
        val daysCountRequired: Int = 1,
        val minHours: Int = 2,
        val shiftName: String = "Shift"
    )

    fun addCustomFormula(
        spaceId: String,
        spec: CustomFormulaSpec,
        context: Context
    ) {
        val formula = RentalFormula(
            id = "FRM-" + (1000..9999).random(),
            type = spec.type,
            rateUsd = spec.rateUsd,
            scheduleDescription = spec.description,
            daysOfWeek = spec.daysOfWeek,
            startHour = spec.startHour,
            endHour = spec.endHour,
            totalWeeklyHours = spec.weeklyHours,
            daysCountRequired = spec.daysCountRequired,
            minHours = spec.minHours,
            shiftName = spec.shiftName
        )
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val success = repository.addRentalFormula(spaceId, formula)
                Toast.makeText(
                    appContext,
                    if (success) "New formula '${spec.type.displayName}' added!" else "Failed to add formula — please try again",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(appContext, e, "Failed to add formula — please try again.")
            }
        }
    }

    fun deleteFormula(spaceId: String, formulaId: String, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val success = repository.deleteRentalFormula(spaceId, formulaId)
                Toast.makeText(
                    appContext,
                    if (success) "Rental formula deleted" else "Failed to delete formula — please try again",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(appContext, e, "Failed to delete formula — please try again.")
            }
        }
    }

    /** Adds one room/desk to an already-published listing — see SubdivisionEditorSection. */
    fun addSubdivision(spaceId: String, subdivision: Subdivision, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val success = repository.addSubdivision(spaceId, subdivision)
                if (success) com.example.analytics.AnalyticsTracker.subdivisionAdd(subdivision)
                if (!success) {
                    Toast.makeText(appContext, "Couldn't add this room — please try again", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(appContext, e, "Couldn't add this room — please try again.")
            }
        }
    }

    fun removeSubdivision(spaceId: String, subdivisionId: String, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val success = repository.removeSubdivision(spaceId, subdivisionId)
                if (success) com.example.analytics.AnalyticsTracker.subdivisionRemove()
                if (!success) {
                    Toast.makeText(appContext, "Couldn't remove this room — please try again", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(appContext, e, "Couldn't remove this room — please try again.")
            }
        }
    }

    // Availability Analytics per Space
    fun getAcceptedBookingsForSpace(spaceId: String): List<RentalBookingRequest> {
        return bookingRequests.value.filter { it.spaceId == spaceId && it.status == BookingRequestStatus.ACCEPTED }
    }

    fun getPendingBookingsForSpace(spaceId: String): List<RentalBookingRequest> {
        return bookingRequests.value.filter { it.spaceId == spaceId && it.status == BookingRequestStatus.PENDING }
    }

    // --- Data Export Hub ---
    fun getJsonExport(): String = repository.exportToJson()

    fun shareExportData(context: Context, format: String, content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ProHost - $format Export")
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "Export ProHost Data"))
    }

    /**
     * Opens the system share sheet for a listing — [content] carries the real
     * public link (see com.example.util.ShareLinks) so a link-preview-capable
     * destination (WhatsApp, Telegram, iMessage, etc.) renders a real
     * thumbnail/title/description card, sourced from
     * functions/src/listings/shareLanding.ts, not just plain text. A separate
     * function from shareExportData above — that one is for data exports and
     * uses an export-specific subject/chooser title that would read oddly
     * here.
     */
    fun shareListing(context: Context, title: String, content: String, space: SpaceListing? = null) {
        space?.let { com.example.analytics.AnalyticsTracker.share(it, "share_sheet") }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "Share Listing"))
    }

    /** Copies a listing's public share link (com.example.util.ShareLinks) to the clipboard. */
    fun copyListingLink(context: Context, url: String, space: SpaceListing? = null) {
        space?.let { com.example.analytics.AnalyticsTracker.share(it, "copy_link") }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ProHost Listing Link", url))
        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun reportFailure(appContext: Context, e: Exception, fallback: String) {
        android.util.Log.e("ProHostVM", fallback, e)
        com.example.analytics.AnalyticsTracker.appError(fallback, com.example.analytics.AnalyticsTracker.errorCode(e))
        Toast.makeText(appContext, e.toUserMessage(fallback), Toast.LENGTH_LONG).show()
    }
}
