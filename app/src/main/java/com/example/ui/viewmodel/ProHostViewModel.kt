package com.example.ui.viewmodel

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
    object PackageLimitReached : ListingCreateResult()
    object Failed : ListingCreateResult()
    // PAY_AS_YOU_GO tier only: no purchased slot exists for this listing's category —
    // CreateListingDialog's Publish button already greys out for this case, but the
    // check is repeated here since it's the real gate (client UI is a convenience,
    // not the source of truth).
    data class PaygCategoryCreditRequired(val categoryId: String?) : ListingCreateResult()
}

class ProHostViewModel(
    val repository: ProHostRepository = ProHostRepository.getInstance()
) : ViewModel() {

    // Used by the Whish payment functions and sendPaymentReminder below — kept here
    // (not moved to AuthViewModel with the rest of the FirebaseFunctionsClient calls)
    // since those are cross-cutting, multi-screen actions, unlike the auth flow.
    private val functionsClient = com.example.data.auth.FirebaseFunctionsClient()

    val pricingState: StateFlow<AdminPricingState> = repository.pricingState
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

    // Owner spaces
    val ownerSpaces: StateFlow<List<SpaceListing>> = combine(spaces, currentUser) { list, user ->
        if (user == null) emptyList()
        else list.filter { it.ownerName.contains(user.fullName, ignoreCase = true) || it.ownerPhone == user.phone || user.role == UserRole.ADMIN }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Practitioner active & past bookings
    val practitionerBookings: StateFlow<List<RentalBookingRequest>> = combine(bookingRequests, currentUser) { list, user ->
        if (user == null) emptyList()
        else list.filter { it.practitionerId == user.id || it.practitionerEmail.equals(user.email, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Owner incoming booking requests
    val ownerIncomingRequests: StateFlow<List<RentalBookingRequest>> = combine(bookingRequests, currentUser, spaces) { requests, user, allSpaces ->
        if (user == null) emptyList()
        else if (user.role == UserRole.ADMIN) requests
        else {
            val mySpaceIds = allSpaces.filter { it.ownerName.contains(user.fullName, ignoreCase = true) || it.ownerPhone == user.phone }.map { it.id }.toSet()
            requests.filter { it.ownerId == user.id || mySpaceIds.contains(it.spaceId) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Financial Metrics
    val activeMrr: Double get() = repository.calculateActiveMrr()
    val potentialMrr: Double get() = repository.calculatePotentialCapacityMrr()
    val projectedArr: Double get() = repository.calculateProjectedArr()
    val totalSettlementVolume: Double get() = repository.calculateTotalSettlementVolume()

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

    // --- Whish Pay Settlement ---
    // All four flows below used to build a "SUCCESS" WhishTransaction locally and grant
    // the entitlement immediately — the client both set the price and self-reported
    // success, with no actual payment required. They now call initiateWhishPayment
    // (Cloud Function), which computes the real amount server-side and returns a
    // collectUrl to open; nothing is granted until whishWebhook/checkWhishStatus
    // independently confirms success with Whish itself. See
    // functions/src/payments/initiateWhishPayment.ts.

    // Guards against a double-submit launching two separate Whish transactions for
    // the same purchase (e.g. a rapid double-tap on "Go to Whish Pay" before the
    // confirmation dialog closes) — each would be a real, independently-charged
    // order server-side, not a harmless duplicate click. Exposed so the buttons
    // that call payOwnerPackageViaWhish/payPaygListingViaWhish can grey out while
    // one is already in flight, on top of the hard guard below.
    private val _isWhishCheckoutInFlight = MutableStateFlow(false)
    val isWhishCheckoutInFlight: StateFlow<Boolean> = _isWhishCheckoutInFlight.asStateFlow()

    private fun launchWhishCheckout(
        purpose: String,
        targetId: String,
        payerName: String,
        payerPhone: String,
        context: Context,
        draftListingId: String? = null
    ) {
        if (_isWhishCheckoutInFlight.value) return
        _isWhishCheckoutInFlight.value = true
        viewModelScope.launch {
            val result = try {
                functionsClient.initiateWhishPayment(purpose, targetId, payerName, payerPhone, draftListingId)
            } finally {
                _isWhishCheckoutInFlight.value = false
            }
            result.onSuccess { init ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(init.collectUrl)))
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open the payment page.", Toast.LENGTH_LONG).show()
                }
                Toast.makeText(
                    context,
                    if (draftListingId != null) {
                        "Complete your payment in the browser. Your pending Draft will publish automatically once Whish settles it."
                    } else {
                        "Complete your payment in the browser. We'll confirm automatically once Whish settles it."
                    },
                    Toast.LENGTH_LONG
                ).show()
                // The correlation is now recorded server-side on the transaction
                // itself (see entitlements.ts's autoPublishDraftIfNeeded) — clearing
                // it here just stops OwnerSubscriptionsScreen's banner from re-firing
                // a second, unrelated purchase against the same draft.
                if (draftListingId != null) clearPendingAutoPublishDraft()
                pollWhishPaymentStatus(init.txId, purpose, context)
            }.onFailure { e ->
                Toast.makeText(context, "Could not start payment: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Set by OwnerHubScreen right before redirecting to Subscriptions after a
    // PackageLimitReached/PaygCategoryCreditRequired Publish rejection — the id of
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
     * once a package/PAYG_LISTING payment is confirmed settled, so a SPECIALIST who
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

    /** Bounded polling fallback in case the server-to-server webhook is slow/missed. */
    private fun pollWhishPaymentStatus(txId: String, purpose: String, context: Context) {
        viewModelScope.launch {
            repeat(24) {
                kotlinx.coroutines.delay(5000)
                val status = functionsClient.checkWhishStatus(txId).getOrNull()
                if (status == "SUCCESS") {
                    if (purpose == "OWNER_PACKAGE" || purpose == "PAYG_LISTING") {
                        refreshCurrentUserRoleAfterEntitlement()
                    }
                    Toast.makeText(context, "Payment confirmed! Your entitlement is now active.", Toast.LENGTH_LONG).show()
                    return@launch
                } else if (status == "FAILED") {
                    Toast.makeText(context, "Whish reported this payment did not complete.", Toast.LENGTH_LONG).show()
                    return@launch
                }
            }
        }
    }

    /** Manually triggered re-check, e.g. from a "Verify Payment" button in the UI. */
    fun checkWhishPaymentStatus(txId: String, purpose: String, context: Context) {
        viewModelScope.launch {
            val status = functionsClient.checkWhishStatus(txId).getOrNull()
            if (status == "SUCCESS" && (purpose == "OWNER_PACKAGE" || purpose == "PAYG_LISTING")) {
                refreshCurrentUserRoleAfterEntitlement()
            }
            val message = when (status) {
                "SUCCESS" -> "Payment confirmed! Your entitlement is now active."
                "FAILED" -> "Whish reported this payment did not complete."
                else -> "Still waiting for Whish to confirm this payment."
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun paySubscriptionViaWhish(spaceId: String, payerName: String, payerPhone: String, context: Context) {
        launchWhishCheckout("SUBSCRIPTION", spaceId, payerName, payerPhone, context)
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

    // --- Space Owner Listing Creation ---
    suspend fun createNewSpaceListing(listing: SpaceListing): ListingCreateResult {
        val user = currentUser.value
        val tier = user?.ownerPackageTier ?: OwnerPackageTier.PAY_AS_YOU_GO
        val limit = pricingState.value.package2Limit

        if (tier == OwnerPackageTier.LIMITED_3_TIER && (user?.activeListingCount ?: 0) >= limit) {
            repository.addAuditLog(
                actionType = "LISTING_BLOCKED_PACKAGE_LIMIT",
                details = "Owner reached Package 2 limit ($limit listings max). Upgrade to Package 3 Unlimited required.",
                severity = "WARN"
            )
            return ListingCreateResult.PackageLimitReached
        }

        // PAY_AS_YOU_GO is per-listing, per-category billing (spec: "PAYG-locked"
        // categories) — publishing straight to ACTIVE requires a purchased slot for
        // this exact category; saving as a Draft is exempt (nothing to consume yet).
        if (tier == OwnerPackageTier.PAY_AS_YOU_GO && listing.status == ListingStatus.ACTIVE) {
            val categoryId = listing.spaceCategoryId
            val hasCredit = categoryId != null && (user?.paygCategoryCredits?.get(categoryId) ?: 0) > 0
            if (!hasCredit) {
                repository.addAuditLog(
                    actionType = "LISTING_BLOCKED_PAYG_CREDIT",
                    details = "Owner attempted to publish category '${listing.spaceCategoryName ?: categoryId}' with no purchased PAYG slot.",
                    severity = "WARN"
                )
                return ListingCreateResult.PaygCategoryCreditRequired(categoryId)
            }
        }

        // Reports the real Firestore result now — this used to return an
        // unconditional true for a listing that was never actually persisted.
        // paygCategoryCredits is server-protected (firestore.rules), the same way
        // activeListingCount is — the client can't decrement it directly. Consuming
        // the credit happens server-side, in the same Firestore trigger that already
        // maintains activeListingCount on this exact create (see
        // functions/src/listings/listingCountTracker.ts's consumePaygCreditIfNeeded).
        return if (repository.addSpaceListing(listing)) ListingCreateResult.Success else ListingCreateResult.Failed
    }

    /** Fast, synchronous check for the "Add New Workspace Listing" card — lets
     * OwnerHubScreen grey out/redirect that entry point before the host spends
     * time on a multi-step wizard that [createNewSpaceListing] will just reject. */
    fun isAtListingLimit(): Boolean {
        val user = currentUser.value ?: return false
        return user.ownerPackageTier == OwnerPackageTier.LIMITED_3_TIER &&
            user.activeListingCount >= pricingState.value.package2Limit
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

    fun payOwnerPackageViaWhish(
        tier: OwnerPackageTier,
        payerName: String,
        payerPhone: String,
        // A SchemaItem.id from the admin-defined Space Category catalog (or a legacy
        // SpaceType name as fallback) — only meaningful when tier == PAY_AS_YOU_GO.
        paygCategoryId: String?,
        context: Context,
        draftListingId: String? = null
    ) {
        if (tier == OwnerPackageTier.PAY_AS_YOU_GO) {
            val categoryId = paygCategoryId ?: SpaceType.PRIVATE_OFFICE.name
            launchWhishCheckout("PAYG_LISTING", categoryId, payerName, payerPhone, context, draftListingId)
        } else {
            launchWhishCheckout("OWNER_PACKAGE", tier.name, payerName, payerPhone, context, draftListingId)
        }
    }

    // categoryId is a SchemaItem.id from spaceArchitectureSchema.spaceTypes (or, for
    // the fallback synthetic list when the schema is empty, a legacy SpaceType name)
    // — the server looks up its real price via getPaygFeeForCategory rather than
    // trusting a client-supplied amount.
    fun payPaygListingViaWhish(
        categoryId: String,
        payerName: String,
        payerPhone: String,
        context: Context,
        draftListingId: String? = null
    ) {
        launchWhishCheckout("PAYG_LISTING", categoryId, payerName, payerPhone, context, draftListingId)
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

    fun logout() {
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        repository.logout()
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
            repository.logout()
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
            val cleanPhone = space.ownerPhone.replace("+", "").replace(" ", "").replace("-", "")
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
            val cleanPhone = request.practitionerPhone.replace("+", "").replace(" ", "").replace("-", "")
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
        selectedStartHour: String = "",
        selectedEndHour: String = "",
        selectedShift: String = "",
        calculatedTotalUsd: Double = 0.0,
        subdivisionId: String? = null,
        subdivisionName: String? = null,
        replacesBookingId: String? = null
    ) {
        val user = currentUser.value
        if (user == null) {
            Toast.makeText(context, "Please log in to submit a rental request", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            val (request, synced) = repository.createBookingRequest(
                space = space,
                formula = formula,
                practitioner = user,
                startDate = startDate,
                durationMonths = durationMonths,
                notes = notes,
                selectedDays = selectedDays,
                selectedStartHour = selectedStartHour,
                selectedEndHour = selectedEndHour,
                selectedShift = selectedShift,
                calculatedTotalUsd = calculatedTotalUsd,
                subdivisionId = subdivisionId,
                subdivisionName = subdivisionName,
                replacesBookingId = replacesBookingId
            )

            Toast.makeText(
                context,
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
        viewModelScope.launch {
            // Refuse before the upload: accepting this would double-book a slot an
            // ACCEPTED booking already holds. Named so the host knows which one.
            repository.findAcceptConflict(requestId)?.let { clash ->
                Toast.makeText(
                    context,
                    "Can't accept #$requestId — it overlaps accepted booking #${clash.id} (${clash.practitionerName}, ${clash.selectedDateTimeRange}).",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val storageService = com.example.data.storage.FirebaseStorageService.getInstance()
            val ext = guessFileExtension(context, agreementUri, "pdf")
            val agreementUrl = storageService.uploadBookingAgreement(requestId, agreementUri, ext)
            if (agreementUrl == null) {
                Toast.makeText(context, "Could not upload the agreement. Please try again.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val success = repository.acceptBookingRequest(requestId, agreementUrl)
            if (success) {
                Toast.makeText(context, "Booking Request #$requestId ACCEPTED! Agreement saved.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Could not finalize acceptance. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Real cross-device push reminder (functions/src/notifications/sendPaymentReminder.ts)
     * — this used to just call [postNotificationAlert], which only ever updated the
     * host's own device's alert tray and never reached the specialist at all.
     */
    fun sendPaymentReminder(bookingId: String, practitionerName: String, context: Context) {
        viewModelScope.launch {
            val result = functionsClient.sendPaymentReminder(bookingId)
            if (result.isSuccess) {
                Toast.makeText(context, "Payment Reminder Sent to $practitionerName!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Could not send the reminder. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun rejectBookingRequest(requestId: String, note: String? = null, context: Context) {
        val success = repository.rejectBookingRequest(requestId, note)
        if (success) {
            Toast.makeText(context, "Booking Request #${requestId} Declined. Space hours remain available.", Toast.LENGTH_SHORT).show()
        }
    }

    fun cancelBookingRequest(requestId: String, context: Context) {
        val success = repository.cancelBookingRequest(requestId)
        if (success) {
            Toast.makeText(context, "Booking Request #${requestId} Cancelled", Toast.LENGTH_SHORT).show()
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
        viewModelScope.launch {
            val success = repository.cancelAcceptedBooking(
                requestId = requestId,
                reasonCode = reasonCode,
                note = note,
                cancelledByUid = user.id,
                cancelledByRole = user.role.name
            )
            Toast.makeText(
                context,
                if (success) "Booking cancelled. The other party has been notified." else "Could not cancel this booking — please try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** "Mark as Paid" — record-keeping only; asHost decides which side's own flag gets set. */
    fun acknowledgePayment(requestId: String, asHost: Boolean, context: Context) {
        viewModelScope.launch {
            val success = repository.acknowledgePayment(requestId, asHost)
            if (!success) {
                Toast.makeText(context, "Couldn't save that — please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Saves whichever verification document the host just uploaded and requests
     * the Listing Verified badge (functions/src/admin/listings.ts's
     * requestListingVerification, auto-granted — no manual review). Optional;
     * this is never called as part of publishing a listing.
     */
    fun requestListingVerification(
        spaceId: String,
        docUrl: String,
        docType: ListingVerificationDocType,
        context: Context
    ) {
        viewModelScope.launch {
            val success = repository.requestOwnListingVerification(spaceId, docUrl, docType)
            Toast.makeText(
                context,
                if (success) "Listing Verified badge earned!" else "Couldn't verify this listing — please try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

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
        viewModelScope.launch {
            val success = repository.setListingStatus(spaceId, status)
            if (!success) {
                Toast.makeText(context, "Couldn't update this listing — please try again.", Toast.LENGTH_SHORT).show()
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
        viewModelScope.launch {
            val success = repository.addBlackoutSlot(spaceId, slot)
            Toast.makeText(
                context,
                if (success) "$dayOfWeek $startTime - $endTime is no longer offered" else "Couldn't switch that slot off — please try again",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun removeBlackoutSlot(spaceId: String, slotId: String, context: Context) {
        viewModelScope.launch {
            val success = repository.removeBlackoutSlot(spaceId, slotId)
            Toast.makeText(
                context,
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
        viewModelScope.launch {
            val success = repository.updateSpaceSchedule(spaceId, updatedSchedule)
            Toast.makeText(
                context,
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
        viewModelScope.launch {
            val success = repository.addRentalFormula(spaceId, formula)
            Toast.makeText(
                context,
                if (success) "New formula '${type.displayName}' added!" else "Failed to add formula — please try again",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun deleteFormula(spaceId: String, formulaId: String, context: Context) {
        viewModelScope.launch {
            val success = repository.deleteRentalFormula(spaceId, formulaId)
            Toast.makeText(
                context,
                if (success) "Rental formula deleted" else "Failed to delete formula — please try again",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** Adds one room/desk to an already-published listing — see SubdivisionEditorSection. */
    fun addSubdivision(spaceId: String, subdivision: Subdivision, context: Context) {
        viewModelScope.launch {
            val success = repository.addSubdivision(spaceId, subdivision)
            if (!success) {
                Toast.makeText(context, "Couldn't add this room — please try again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeSubdivision(spaceId: String, subdivisionId: String, context: Context) {
        viewModelScope.launch {
            val success = repository.removeSubdivision(spaceId, subdivisionId)
            if (!success) {
                Toast.makeText(context, "Couldn't remove this room — please try again", Toast.LENGTH_SHORT).show()
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
}
