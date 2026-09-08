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
    val transactions: StateFlow<List<WhishTransaction>> = repository.transactions
    val users: StateFlow<List<AppUser>> = repository.users
    val currentUser: StateFlow<AppUser?> = repository.currentUser
    val auditLogs: StateFlow<List<AuditSecurityLog>> = repository.auditLogs
    val bookingRequests: StateFlow<List<RentalBookingRequest>> = repository.bookingRequests
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
    // drawer dialog (a fully unreachable duplicate of AdminConsoleScreen's already-correct
    // Dynamic Pricing Engine tab), silently discarding the Boolean result with no success/
    // failure feedback of any kind. Both the dialog and these wrappers are removed; use
    // AdminViewModel's checked equivalents instead.

    // --- Whish Pay Settlement ---
    // All four flows below used to build a "SUCCESS" WhishTransaction locally and grant
    // the entitlement immediately — the client both set the price and self-reported
    // success, with no actual payment required. They now call initiateWhishPayment
    // (Cloud Function), which computes the real amount server-side and returns a
    // collectUrl to open; nothing is granted until whishWebhook/checkWhishStatus
    // independently confirms success with Whish itself. See
    // functions/src/payments/initiateWhishPayment.ts.

    private fun launchWhishCheckout(
        purpose: String,
        targetId: String,
        payerName: String,
        payerPhone: String,
        context: Context
    ) {
        viewModelScope.launch {
            val result = functionsClient.initiateWhishPayment(purpose, targetId, payerName, payerPhone)
            result.onSuccess { init ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(init.collectUrl)))
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open the payment page.", Toast.LENGTH_LONG).show()
                }
                Toast.makeText(
                    context,
                    "Complete your payment in the browser. We'll confirm automatically once Whish settles it.",
                    Toast.LENGTH_LONG
                ).show()
                pollWhishPaymentStatus(init.txId, purpose, context)
            }.onFailure { e ->
                Toast.makeText(context, "Could not start payment: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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

    // --- Space Owner Listing Creation ---
    fun createNewSpaceListing(listing: SpaceListing): Boolean {
        val user = currentUser.value
        val tier = user?.ownerPackageTier ?: OwnerPackageTier.PAY_AS_YOU_GO
        val ownerSpaces = spaces.value.filter { it.ownerId == listing.ownerId || it.ownerEmail.equals(listing.ownerEmail, ignoreCase = true) }

        if (tier == OwnerPackageTier.LIMITED_3_TIER && ownerSpaces.size >= 3) {
            repository.addAuditLog(
                actionType = "LISTING_BLOCKED_PACKAGE_LIMIT",
                details = "Owner reached Package 2 limit (3 listings max). Upgrade to Package 3 Unlimited required.",
                severity = "WARN"
            )
            return false
        }

        repository.addSpaceListing(listing)
        return true
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
        spaceTypeForPayg: SpaceType?,
        context: Context
    ) {
        if (tier == OwnerPackageTier.PAY_AS_YOU_GO) {
            val type = spaceTypeForPayg ?: SpaceType.PRIVATE_OFFICE
            launchWhishCheckout("PAYG_LISTING", type.name, payerName, payerPhone, context)
        } else {
            launchWhishCheckout("OWNER_PACKAGE", tier.name, payerName, payerPhone, context)
        }
    }

    fun payPaygListingViaWhish(
        spaceType: SpaceType,
        payerName: String,
        payerPhone: String,
        context: Context
    ) {
        launchWhishCheckout("PAYG_LISTING", spaceType.name, payerName, payerPhone, context)
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
        selectedStrategy: String? = null,
        replacesBookingId: String? = null
    ): RentalBookingRequest? {
        val user = currentUser.value
        if (user == null) {
            Toast.makeText(context, "Please log in to submit a rental request", Toast.LENGTH_SHORT).show()
            return null
        }

        val request = repository.createBookingRequest(
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
            selectedStrategy = selectedStrategy,
            replacesBookingId = replacesBookingId
        )

        Toast.makeText(
            context,
            if (replacesBookingId != null) {
                "Edit Request #${request.id} Sent! Your current booking stays active until the host approves this change."
            } else {
                "Rental Request #${request.id} Sent! Space hours remain open until owner approval."
            },
            Toast.LENGTH_LONG
        ).show()

        if (alsoOpenWhatsApp) {
            launchWhatsAppInquiry(context, space, formula, request)
        }

        return request
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

    // --- Schedule & Blackout Management ---
    fun addBlackoutSlot(spaceId: String, dayOfWeek: String, startTime: String, endTime: String, reason: String, context: Context) {
        val slot = BlackoutSlot(
            id = "BLK-" + (100..999).random(),
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            endTime = endTime,
            reason = reason
        )
        viewModelScope.launch {
            val success = repository.addBlackoutSlot(spaceId, slot)
            Toast.makeText(
                context,
                if (success) "Blackout hour added: $dayOfWeek ($startTime - $endTime)" else "Failed to add blackout hour — please try again",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun removeBlackoutSlot(spaceId: String, slotId: String, context: Context) {
        viewModelScope.launch {
            val success = repository.removeBlackoutSlot(spaceId, slotId)
            Toast.makeText(
                context,
                if (success) "Blackout slot removed" else "Failed to remove blackout slot — please try again",
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
        val currentSpace = spaces.value.find { it.id == spaceId } ?: return
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
