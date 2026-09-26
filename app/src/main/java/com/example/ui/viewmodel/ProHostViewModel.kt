package com.example.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ProHostRepository
import com.example.util.guessFileExtension
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
    // Covers both "no active package at all" and "at the current package's listing
    // cap" — either way the fix is the same: upgrade to a package with room. See
    // createNewSpaceListing's own doc comment.
    object PackageLimitReached : ListingCreateResult()
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

    // Used by the Whish payment functions and sendPaymentReminder below — kept here
    // (not moved to AuthViewModel with the rest of the FirebaseFunctionsClient calls)
    // since those are cross-cutting, multi-screen actions, unlike the auth flow.
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
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
    )
    val isRestoringSession: StateFlow<Boolean> = _isRestoringSession.asStateFlow()
    private val _sessionRestoreError = MutableStateFlow<String?>(null)
    val sessionRestoreError: StateFlow<String?> = _sessionRestoreError.asStateFlow()

    // Set to true by MainActivity.onResume when the app was in the background for >60s
    // with a signed-in user — gates the UI behind PIN entry (H1).
    private val _pinReauthRequired = MutableStateFlow(false)
    val pinReauthRequired: StateFlow<Boolean> = _pinReauthRequired.asStateFlow()

    // Set during session restore when we detect the user has no PIN yet (registered before
    // PIN was introduced). Gates the app behind PIN creation, same pattern as pinReauthRequired.
    private val _requiresPinSetup = MutableStateFlow(false)
    val requiresPinSetup: StateFlow<Boolean> = _requiresPinSetup.asStateFlow()

    fun clearRequiresPinSetup() {
        _requiresPinSetup.value = false
    }

    fun requestPinReauth() {
        // Skip for ADMIN accounts — they have no phone-OTP registration and cannot verify PIN (NF1).
        val user = currentUser.value ?: return
        if (user.role == com.example.data.model.UserRole.ADMIN) return
        _pinReauthRequired.value = true
    }

    fun clearPinReauth() {
        _pinReauthRequired.value = false
    }

    // Set when cold-start restoration finds a Firebase-Auth-verified session whose
    // registration was never actually completed (app killed between OTP
    // verification and submitting the registration form) — see the init block
    // below and ProHostRepository.discardIncompleteSession's doc comment for the
    // full story. ProHostAppRoot routes to LoginAuthScreen's registration form
    // directly (skipping phone/OTP entry, since this session is already verified)
    // instead of either silently completing a broken "login" or bouncing the user
    // to a plain phone-entry screen with no memory of which number this was.
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
                    // A real, previously-completed account always has a real phone on
                    // file (registerMember always writes one) — ADMIN is the one
                    // legitimate exception, created via bootstrapSuperAdmin/
                    // grantAdminRole, which never goes through registerMember at all.
                    // Anything else with a blank phone here is exactly the stranded
                    // mid-registration case, not a coincidence.
                    if (user.role != UserRole.ADMIN && user.phone.isBlank()) {
                        repository.discardIncompleteSession()
                        _pendingRegistrationPhone.value = firebaseUser.phoneNumber
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
    // Admin-managed, purchasable Pro Host packages — see PackagePlan/PackagePlanCatalog.
    val packagePlans: StateFlow<PackagePlanCatalog> = repository.packagePlans
    val spaces: StateFlow<List<SpaceListing>> = repository.spaces
    val subscriptionFormulas: StateFlow<List<SubscriptionFormula>> = repository.subscriptionFormulas
    val isCloudConnected: StateFlow<Boolean> = repository.isCloudConnected
    // Admin-managed facility/category catalog — previously read only by AdminConsoleScreen
    // itself, so an admin's additions in "Schema Architecture" never reached the
    // create-listing wizard or Discovery's filter sheet, both of which used a
    // hardcoded FacilityCatalog.standard object instead. Exposed here so both can
    // read the same live, admin-editable list.
    val spaceArchitectureSchema: StateFlow<SpaceArchitectureSchema> = repository.spaceArchitectureSchema
    val transactions: StateFlow<List<WhishTransaction>> = repository.transactions
    val users: StateFlow<List<AppUser>> = repository.users
    val currentUser: StateFlow<AppUser?> = repository.currentUser
    val auditLogs: StateFlow<List<AuditSecurityLog>> = repository.auditLogs
    val bookingRequests: StateFlow<List<RentalBookingRequest>> = repository.bookingRequests
    val hasLoadedBookingsOnce: StateFlow<Boolean> = repository.hasLoadedBookingsOnce
    val fcmAlerts: StateFlow<List<FCMAlert>> = repository.fcmAlerts
    val isOfflineMode: StateFlow<Boolean> = repository.isOfflineMode
    val syncStatusMessage: StateFlow<String?> = repository.syncStatusMessage
    val pendingOfflineTransactions: StateFlow<List<WhishTransaction>> = repository.pendingOfflineTransactions

    fun retryOfflineSync() {
        repository.retryOfflineTransactions()
        repository.startRealtimeSync()
    }

    fun markAlertAsRead(alertId: String) {
        repository.markAlertAsRead(alertId)
    }

    // Owner spaces — ownerId is the real, authoritative identity match (stamped at
    // create time and rules-enforced to equal the creating uid). This used to also
    // match on "ownerName contains fullName" as a fallback, which is a genuine
    // cross-tenant privacy bug: any host whose name is a substring of another
    // host's listed owner name (e.g. "Sara" inside "Sara Khalil Clinic") would see
    // that other host's real listings merged into their own dashboard.
    val ownerSpaces: StateFlow<List<SpaceListing>> = combine(spaces, currentUser) { list, user ->
        if (user == null) emptyList()
        else if (user.role == UserRole.ADMIN) list
        else list.filter { it.ownerId == user.id }
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
    val playPurchaseHistory = MutableStateFlow<List<com.android.billingclient.api.PurchaseHistoryRecord>>(emptyList())

    // Holds a deferred launch when billing was not yet connected at the time the user tapped
    // "Subscribe via Google Play". Cleared and retried once products arrive from Play.
    private var _pendingRetryProductId: String? = null
    private var _pendingRetryActivity: java.lang.ref.WeakReference<android.app.Activity>? = null

    fun initPlayBilling(context: Context) {
        if (playBillingManager == null) {
            val manager = com.example.data.billing.PlayBillingManager(context.applicationContext, viewModelScope)
            playBillingManager = manager
            viewModelScope.launch {
                manager.isConnected.collect { connected ->
                    playBillingConnected.value = connected
                }
            }
            viewModelScope.launch {
                manager.productDetailsList.collect { products ->
                    playBillingProducts.value = products
                    // Retry a deferred billing launch once the target product is available
                    val retryId = _pendingRetryProductId
                    val retryActivity = _pendingRetryActivity?.get()
                    if (retryId != null && retryActivity != null && products.any { it.productId == retryId }) {
                        _pendingRetryProductId = null
                        _pendingRetryActivity = null
                        launchGooglePaySubscription(retryActivity, retryId)
                    }
                }
            }
            viewModelScope.launch {
                manager.purchaseEvents.collect { purchase ->
                    val productId = purchase.products.firstOrNull() ?: com.example.data.billing.PlayBillingManager.PRODUCT_ID_GROWTH
                    repository.recordActivePurchaseToken(productId, purchase.purchaseToken)
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
            currentUser.collectLatest { user ->
                if (user?.ownerPackageExpiryMillis != null &&
                    user.ownerPackageExpiryMillis != _billingPriorExpiryMillis.value &&
                    _billingActivationPending.value
                ) {
                    billingActivationTimeoutJob?.cancel()
                    _billingActivationPending.value = false
                    // Force-refresh the ID token so the new PRO_HOST claim takes effect
                    // immediately — without this the user sees SPECIALIST navigation for
                    // up to an hour until the token naturally expires.
                    refreshCurrentUserRoleAfterEntitlement()
                }
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

    fun resendEmailVerification(context: android.content.Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val result = functionsClient.resendEmailVerification()
            val msg = if (result.isSuccess) {
                "Verification email sent. Check your inbox."
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                when {
                    err.contains("resource-exhausted", ignoreCase = true) ||
                    err.contains("3 times", ignoreCase = true) -> "You've already requested 3 emails today. Try again tomorrow."
                    else -> "Could not send email: $err"
                }
            }
            android.widget.Toast.makeText(appContext, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun sendInquiryEmail(context: android.content.Context, spaceId: String, message: String) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val result = functionsClient.sendInquiryEmail(spaceId, message)
            val msg = if (result.isSuccess) {
                "Inquiry sent to the space owner."
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                when {
                    err.contains("resource-exhausted", ignoreCase = true) ->
                        "You've reached the daily inquiry limit (3 per day). Try again tomorrow."
                    err.contains("not-found", ignoreCase = true) -> "Listing not found."
                    else -> "Could not send inquiry: $err"
                }
            }
            android.widget.Toast.makeText(appContext, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun launchGooglePaySubscription(
        activity: android.app.Activity,
        productId: String
    ) {
        val uid = currentUser.value?.id ?: return
        // Persist the pending draft ID before the billing sheet opens so the RTDN
        // Cloud Function can auto-publish it when the subscription is confirmed.
        val draftId = _pendingAutoPublishDraftId.value
        if (draftId != null) {
            viewModelScope.launch {
                try {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("user_profiles").document(uid)
                        .update("pendingPlayPublishDraftId", draftId).await()
                } catch (_: Exception) { /* non-fatal; RTDN will skip auto-publish */ }
            }
        }

        val manager = playBillingManager ?: run {
            initPlayBilling(activity)
            playBillingManager
        }
        val product = manager?.productDetailsList?.value?.find { it.productId == productId }
        if (product != null) {
            // For upgrades/downgrades: pass the current active subscription's purchase token so
            // Play can perform a proper subscription replacement (prorated billing, immediate effect).
            val currentPlanId = currentUser.value?.ownerPackageId
            val oldPurchaseToken = if (!currentPlanId.isNullOrBlank() && currentPlanId != productId) {
                manager.activePurchases.value.firstOrNull { it.products.any { id -> id == currentPlanId } }?.purchaseToken
            } else null

            manager.launchSubscriptionPurchase(activity, product, userId = uid, oldPurchaseToken = oldPurchaseToken)
            _billingPriorExpiryMillis.value = currentUser.value?.ownerPackageExpiryMillis
            _billingActivationPending.value = true
            billingActivationTimeoutJob?.cancel()
            billingActivationTimeoutJob = viewModelScope.launch {
                kotlinx.coroutines.delay(5 * 60 * 1000L)
                _billingActivationPending.value = false
            }
        } else {
            // Store the intent and retry automatically once products load from Play
            _pendingRetryProductId = productId
            _pendingRetryActivity = java.lang.ref.WeakReference(activity)
            Toast.makeText(activity, "Connecting to Google Play Store…", Toast.LENGTH_SHORT).show()
            manager?.querySubscriptionProducts()
        }
    }

    fun openManageSubscriptions(activity: android.app.Activity, productId: String? = null) {
        playBillingManager?.openManageSubscriptions(activity, productId) ?: run {
            val uri = if (!productId.isNullOrBlank()) {
                "https://play.google.com/store/account/subscriptions?sku=$productId&package=${activity.packageName}"
            } else {
                "https://play.google.com/store/account/subscriptions?package=${activity.packageName}"
            }
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        }
    }

    fun openRedeemPromoCode(activity: android.app.Activity) {
        playBillingManager?.openRedeemPromoCode(activity) ?: run {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/redeem")))
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

    fun loadPlayHistory(context: Context) {
        val manager = playBillingManager ?: run { initPlayBilling(context); playBillingManager } ?: return
        viewModelScope.launch {
            try {
                playPurchaseHistory.value = manager.queryPurchaseHistory()
            } catch (e: Exception) {
                android.util.Log.w("ProHostViewModel", "loadPlayHistory failed: ${e.message}")
            }
        }
    }

    // Whish Pay settlement was removed — app is fully on Google Play Billing.

    // Set by OwnerHubScreen right before redirecting to Subscriptions after a
    // PackageLimitReached Publish rejection — the id of
    // the Draft that was just saved in place of the blocked Publish attempt.
    // OwnerSubscriptionsScreen reads this (same shared ViewModel instance across
    // both screens) to surface a banner and thread the id into whichever purchase
    // the host makes next, so it auto-publishes without a second trip through the
    // wizard. Purely a client-side UX convenience — the real correlation once a
    // payment is launched lives server-side on the Whish transaction itself.
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
        viewModelScope.launch { _topHashtags.value = repository.fetchTopHashtags() }
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
        viewModelScope.launch { repository.addUserSuggestedSchemaItem(item) }
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

        // Publishing straight to ACTIVE requires an active, enabled, non-expired
        // package with room under its listing limit; saving as a Draft is always
        // exempt (nothing to consume yet). "No package" and "at the package's cap"
        // both resolve to the same PackageLimitReached outcome — the fix in either
        // case is the same upgrade-to-a-package flow (firestore.rules'
        // withinListingLimit() enforces the identical rule server-side).
        if (!isAdmin && listing.status == ListingStatus.ACTIVE) {
            val plan = user?.ownerPackageId?.let { packagePlans.value.packages[it] }
            val expiry = user?.ownerPackageExpiryMillis
            val isExpired = expiry != null && expiry <= System.currentTimeMillis()
            val withinLimit = plan != null && plan.isEnabled && !isExpired &&
                (plan.listingLimit == null || (user.activeListingCount) < plan.listingLimit)
            if (!withinLimit) {
                repository.addAuditLog(
                    actionType = "LISTING_BLOCKED_PACKAGE_LIMIT",
                    details = if (plan == null) {
                        "Owner attempted to publish with no active package."
                    } else {
                        "Owner reached '${plan.name}' limit (${plan.listingLimit} listings max). Upgrade required."
                    },
                    severity = "WARN"
                )
                return ListingCreateResult.PackageLimitReached
            }
        }

        // Reports the real Firestore result now — this used to return an
        // unconditional true for a listing that was never actually persisted.
        return if (repository.addSpaceListing(listing)) ListingCreateResult.Success else ListingCreateResult.Failed
    }

    /** Fast, synchronous check for the "Add New Workspace Listing" card — lets
     * OwnerHubScreen grey out/redirect that entry point before the host spends
     * time on a multi-step wizard that [createNewSpaceListing] will just reject. */
    fun isAtListingLimit(): Boolean {
        val user = currentUser.value ?: return false
        // ADMIN never purchases/holds a real package (see createNewSpaceListing's
        // own identical bypass and its doc comment) — without this, an Admin's
        // always-null ownerPackageId fell through to the "no package" branch
        // below and read as permanently at-limit, blocking Admin from ever
        // opening the "Add New Workspace Listing" card.
        if (user.role == UserRole.ADMIN) return false
        val plan = user.ownerPackageId?.let { packagePlans.value.packages[it] } ?: return true
        val expiry = user.ownerPackageExpiryMillis
        val isExpired = expiry != null && expiry <= System.currentTimeMillis()
        if (isExpired || !plan.isEnabled) return true
        return plan.listingLimit != null && user.activeListingCount >= plan.listingLimit
    }

    // An owner had no in-app way to correct a mistake in, or take down, their own
    // published listing — updateSpaceListing/deleteSpaceListing were only ever called
    // from the Admin Console. Firestore rules already permit the owning user to update/
    // delete their own workspace_listings document directly, so this just exposes the
    // existing repository methods (which already safely preserve isVerified/
    // isActiveSubscription/subscriptionExpiryMillis/ownerId regardless of caller).
    suspend fun updateOwnerListing(updated: SpaceListing): Boolean {
        return repository.updateSpaceListing(updated)
    }

    suspend fun deleteOwnerListing(spaceId: String): Boolean {
        return repository.deleteSpaceListing(spaceId)
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
    // to PRO_HOST the moment their package/listing Whish payment settles — never a free,
    // client-invocable "upgrade" call.

    override fun onCleared() {
        super.onCleared()
        playBillingManager?.endConnection()
    }

    fun logout() {
        // repository.logout() fires the (fire-and-forget) FCM-token-clear write
        // before we invalidate the local Firebase Auth session below — reversed,
        // that write would leave with no real auth context and likely get
        // rejected by firestore.rules.
        repository.logout()
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
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

    /** Sets/replaces the signed-in user's own ID document — see
     * ProHostRepository.updateIdDocument's doc comment for why this needed to
     * exist at all (previously only ever settable once, at registration). */
    suspend fun updateIdDocument(idDocumentUrl: String): Boolean {
        return repository.updateIdDocument(idDocumentUrl)
    }

    // Fires exactly once per space-detail view (call from a LaunchedEffect(space.id),
    // not on every recomposition) — real engagement data replacing the old fixed 850
    // placeholder.
    fun registerSpaceView(spaceId: String) {
        repository.incrementSpaceViewCount(spaceId)
    }

    // --- WhatsApp Direct Connection ---
    fun launchWhatsAppInquiry(context: Context, space: SpaceListing, selectedFormula: RentalFormula?, request: RentalBookingRequest? = null) {
        val user = currentUser.value
        val professionalName = user?.fullName ?: "Specialist Member"
        val specialty = user?.specialty?.ifBlank { "Independent Specialist" } ?: "Independent Specialist"

        val formulaText = selectedFormula?.let { "${it.type.displayName} (${it.scheduleDescription} @ $${it.rateUsd}/mo)" }
            ?: "Full Practice Month ($${space.baseMonthlyRateUsd})"

        val requestSnippet = if (request != null) {
            val daysStr = if (request.selectedDays.isNotEmpty()) request.selectedDays.joinToString() else request.formula.daysOfWeek.joinToString()
            val timesStr = if (request.selectedStartHour.isNotBlank() && request.selectedEndHour.isNotBlank()) "${request.selectedStartHour} - ${request.selectedEndHour}" else "${request.formula.startHour} - ${request.formula.endHour}"
            val shiftStr = if (request.selectedShift.isNotBlank()) " (${request.selectedShift})" else ""

            "\n\n[In-App Booking Request Details]\n" +
            "• Request ID: #${request.id}\n" +
            "• Formula: ${request.formula.type.displayName} - ${request.formula.scheduleDescription}\n" +
            "• Chosen Availability: $daysStr @ $timesStr$shiftStr\n" +
            "• Start Date: ${request.startDate} (${request.durationMonths} month${if (request.durationMonths > 1) "s" else ""})\n" +
            "• Total Agreement Value: $${request.totalAmountUsd.toInt()} USD\n" +
            "• Specialist Notes: ${request.clinicalNotes}\n" +
            "• In-App Status: PENDING HOST APPROVAL"
        } else ""

        val rawMessage = "Hello ${space.ownerName},\n\n" +
                "I am ${professionalName} (${specialty}).\n\n" +
                "I am contacting you regarding your space \"${space.title}\" located in ${space.district}, ${space.governorate.displayName} on ProHost.\n" +
                "Selected Formula: ${formulaText}$requestSnippet\n\n" +
                "I would like to finalize payment and walk-through details.\n" +
                "Listing Ref: ProHost #LB-${space.id}"

        try {
            val encoded = URLEncoder.encode(rawMessage, "UTF-8")
            val cleanPhone = formatWhatsAppNumber(space.ownerPhone)
            val url = "https://wa.me/$cleanPhone?text=$encoded"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
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
        val timesStr = if (request.selectedStartHour.isNotBlank() && request.selectedEndHour.isNotBlank()) "${request.selectedStartHour} - ${request.selectedEndHour}" else "${request.formula.startHour} - ${request.formula.endHour}"

        val rawMessage = "Hello ${request.practitionerName},\n\n" +
                "I am $ownerName regarding your booking request (#${request.id}) for space \"${request.spaceTitle}\".\n" +
                "Requested Schedule: $daysStr @ $timesStr (${request.formula.type.displayName}).\n" +
                "Status: ${request.status.displayName}\n\n" +
                "Let's discuss onboarding and walk-through details."

        try {
            val encoded = URLEncoder.encode(rawMessage, "UTF-8")
            val cleanPhone = formatWhatsAppNumber(request.practitionerPhone)
            val url = "https://wa.me/$cleanPhone?text=$encoded"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
            repository.addAuditLog(
                actionType = "WHATSAPP_INQUIRY_HOST_TO_SPECIALIST",
                details = "$ownerName contacted specialist ${request.practitionerName} via WhatsApp about booking #${request.id} ('${request.spaceTitle}')",
                severity = "INFO"
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch WhatsApp.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatWhatsAppNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
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
        return digits.ifBlank { "9613000000" }
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
    fun submitBookingRequest(
        space: SpaceListing,
        formula: RentalFormula,
        startDate: String,
        durationMonths: Int,
        notes: String,
        context: Context,
        alsoOpenWhatsApp: Boolean = false,
        selectedDays: List<String> = emptyList(),
        selectedCalendarDates: List<String> = emptyList(),
        selectedStartHour: String = "",
        selectedEndHour: String = "",
        selectedShift: String = "",
        calculatedTotalUsd: Double = 0.0,
        subdivisionId: String? = null,
        subdivisionName: String? = null,
        replacesBookingId: String? = null,
        attendeeCount: Int = 0,
        selectedAttendeePackageId: String? = null,
        attendeePackageName: String? = null,
        attendeePackagePriceUsd: Double = 0.0
    ) {
        val user = currentUser.value
        if (user == null) {
            Toast.makeText(context, "Please log in to submit a rental request", Toast.LENGTH_SHORT).show()
            return
        }
        // B4: KYC gate — require at least a profile picture before booking
        if (user.kycLevel < 1) {
            Toast.makeText(context, "Please add a profile picture before making booking requests.", Toast.LENGTH_LONG).show()
            return
        }

        val appContext = context.applicationContext
        viewModelScope.launch {
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

            Toast.makeText(
                appContext,
                if (synced) {
                    if (replacesBookingId != null) {
                        "Edit Request #${request.id} Sent! Your current booking stays active until the host approves this change."
                    } else {
                        "Rental Request #${request.id} Sent! Space hours remain open until owner approval."
                    }
                } else {
                    "Couldn't reach the server to send your request — check your connection and try again. The host has not been notified."
                },
                Toast.LENGTH_LONG
            ).show()

            if (alsoOpenWhatsApp) {
                launchWhatsAppInquiry(context, space, formula, request)
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
    fun acceptBookingRequest(context: Context, requestId: String, agreementUri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            // Refuse before the upload: accepting this would double-book a slot an
            // ACCEPTED booking already holds. Named so the host knows which one.
            repository.findAcceptConflict(requestId)?.let { clash ->
                Toast.makeText(
                    appContext,
                    "Can't accept #$requestId — it overlaps accepted booking #${clash.id} (${clash.practitionerName}, ${clash.selectedDateTimeRange}).",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val storageService = com.example.data.storage.FirebaseStorageService.getInstance()
            val ext = guessFileExtension(context, agreementUri, "pdf")
            val agreementUrl = storageService.uploadBookingAgreement(requestId, agreementUri, ext)
            if (agreementUrl == null) {
                Toast.makeText(appContext, "Could not upload the agreement. Please try again.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val success = repository.acceptBookingRequest(requestId, agreementUrl)
            if (success) {
                Toast.makeText(appContext, "Booking Request #$requestId ACCEPTED! Agreement saved.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(appContext, "Could not finalize acceptance. Please try again.", Toast.LENGTH_LONG).show()
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
            val result = functionsClient.sendPaymentReminder(bookingId)
            if (result.isSuccess) {
                Toast.makeText(appContext, "Payment Reminder Sent to $practitionerName!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(appContext, "Could not send the reminder. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun rejectBookingRequest(requestId: String, note: String? = null, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val success = repository.rejectBookingRequest(requestId, note)
            if (success) {
                Toast.makeText(appContext, "Booking Request #${requestId} Declined. Space hours remain available.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(appContext, "Could not decline the request — check your connection and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun cancelBookingRequest(requestId: String, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val success = repository.cancelBookingRequest(requestId)
            if (success) {
                Toast.makeText(appContext, "Booking Request #${requestId} Cancelled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(appContext, "Could not cancel the request — check your connection and try again.", Toast.LENGTH_LONG).show()
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
            val success = repository.cancelAcceptedBooking(
                requestId = requestId,
                reasonCode = reasonCode,
                note = note,
                cancelledByUid = user.id,
                cancelledByRole = user.role.name
            )
            Toast.makeText(
                appContext,
                if (success) "Booking cancelled. The other party has been notified." else "Could not cancel this booking — please try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** "Mark as Paid" — record-keeping only; asHost decides which side's own flag gets set. */
    fun acknowledgePayment(requestId: String, asHost: Boolean, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val success = repository.acknowledgePayment(requestId, asHost)
            if (!success) {
                Toast.makeText(appContext, "Couldn't save that — please try again.", Toast.LENGTH_SHORT).show()
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
    ): Boolean = repository.requestOwnListingVerification(spaceId, docUrl, docType)

    /** Toggles [spaceId] in the current user's personal saved/favorites list. */
    fun toggleSavedSpace(spaceId: String) {
        viewModelScope.launch {
            repository.toggleSavedSpace(spaceId)
        }
    }

    /** Saves (or re-saves) a Draft listing — never blocked by the package listing limit. */
    suspend fun saveListingDraft(listing: SpaceListing): Boolean {
        return repository.saveListingDraft(listing)
    }

    /** Pause/Resume a published listing, or publish a Draft — see setListingStatus's doc comment. */
    fun setListingStatus(spaceId: String, status: ListingStatus, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val success = repository.setListingStatus(spaceId, status)
            if (!success) {
                Toast.makeText(appContext, "Couldn't update this listing — please try again.", Toast.LENGTH_SHORT).show()
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
            val success = repository.addBlackoutSlot(spaceId, slot)
            Toast.makeText(
                appContext,
                if (success) "$dayOfWeek $startTime - $endTime is no longer offered" else "Couldn't switch that slot off — please try again",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun removeBlackoutSlot(spaceId: String, slotId: String, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val success = repository.removeBlackoutSlot(spaceId, slotId)
            Toast.makeText(
                appContext,
                if (success) "Slot is back on offer" else "Couldn't switch that slot on — please try again",
                Toast.LENGTH_SHORT
            ).show()
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
            val success = repository.updateSpaceSchedule(spaceId, updatedSchedule)
            Toast.makeText(
                appContext,
                if (success) "Operating schedule updated!" else "Failed to update schedule — please try again",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun addCustomFormula(
        spaceId: String,
        type: RentalFormulaType,
        rateUsd: Double,
        description: String,
        daysOfWeek: List<String>,
        startHour: String,
        endHour: String,
        weeklyHours: Int,
        daysCountRequired: Int = 1,
        minHours: Int = 2,
        shiftName: String = "Shift",
        context: Context
    ) {
        val formula = RentalFormula(
            id = "FRM-" + (1000..9999).random(),
            type = type,
            rateUsd = rateUsd,
            scheduleDescription = description,
            daysOfWeek = daysOfWeek,
            startHour = startHour,
            endHour = endHour,
            totalWeeklyHours = weeklyHours,
            daysCountRequired = daysCountRequired,
            minHours = minHours,
            shiftName = shiftName
        )
        val appContext = context.applicationContext
        viewModelScope.launch {
            val success = repository.addRentalFormula(spaceId, formula)
            Toast.makeText(
                appContext,
                if (success) "New formula '${type.displayName}' added!" else "Failed to add formula — please try again",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun deleteFormula(spaceId: String, formulaId: String, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val success = repository.deleteRentalFormula(spaceId, formulaId)
            Toast.makeText(
                appContext,
                if (success) "Rental formula deleted" else "Failed to delete formula — please try again",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** Adds one room/desk to an already-published listing — see SubdivisionEditorSection. */
    fun addSubdivision(spaceId: String, subdivision: Subdivision, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val success = repository.addSubdivision(spaceId, subdivision)
            if (!success) {
                Toast.makeText(appContext, "Couldn't add this room — please try again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeSubdivision(spaceId: String, subdivisionId: String, context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val success = repository.removeSubdivision(spaceId, subdivisionId)
            if (!success) {
                Toast.makeText(appContext, "Couldn't remove this room — please try again", Toast.LENGTH_SHORT).show()
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
    fun getAuditTextExport(): String = repository.exportToAuditText()

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
    fun shareListing(context: Context, title: String, content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "Share Listing"))
    }

    /** Copies a listing's public share link (com.example.util.ShareLinks) to the clipboard. */
    fun copyListingLink(context: Context, url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ProHost Listing Link", url))
        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
