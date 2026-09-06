package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ProSpaceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URLEncoder

data class SearchFilterState(
    val query: String = "",
    val selectedGovernorate: Governorate? = null,
    val selectedSpaceType: SpaceType? = null,
    val selectedFormulaType: RentalFormulaType? = null,
    val selectedFacility: String? = null,
    val selectedEquipmentCategory: EquipmentCategory? = null,
    val maxPriceUsd: Double = 1500.0,
    val onlyVerified: Boolean = false,
    val onlyActiveSubscribed: Boolean = true
)

class ProSpaceViewModel(
    val repository: ProSpaceRepository = ProSpaceRepository.getInstance()
) : ViewModel() {

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

    fun postNotificationAlert(
        title: String,
        body: String,
        category: String,
        context: android.content.Context,
        targetTab: String? = null,
        bookingId: String? = null,
        whatsAppPhone: String? = null,
        whatsAppMessage: String? = null
    ) {
        val alert = FCMAlert(title = title, body = body, category = category)
        repository.addFCMAlert(alert)
        com.example.service.ProSpaceMessagingService.showPhysicalNotification(
            context = context,
            title = title,
            body = body,
            targetTab = targetTab,
            bookingId = bookingId,
            whatsAppPhone = whatsAppPhone,
            whatsAppMessage = whatsAppMessage
        )
    }

    private val _searchFilter = MutableStateFlow(SearchFilterState())
    val searchFilter: StateFlow<SearchFilterState> = _searchFilter.asStateFlow()

    private val _selectedSpace = MutableStateFlow<SpaceListing?>(null)
    val selectedSpace: StateFlow<SpaceListing?> = _selectedSpace.asStateFlow()

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

    // Filtered spaces flow
    val filteredSpaces: StateFlow<List<SpaceListing>> = combine(spaces, searchFilter) { list, filter ->
        list.filter { space ->
            val matchesQuery = filter.query.isBlank() ||
                    space.title.contains(filter.query, ignoreCase = true) ||
                    space.district.contains(filter.query, ignoreCase = true) ||
                    space.complementarySpecialties.any { it.contains(filter.query, ignoreCase = true) } ||
                    space.equipment.any { it.name.contains(filter.query, ignoreCase = true) } ||
                    space.spaceType.displayName.contains(filter.query, ignoreCase = true)

            val matchesGov = filter.selectedGovernorate == null || space.governorate == filter.selectedGovernorate
            val matchesType = filter.selectedSpaceType == null || space.spaceType == filter.selectedSpaceType
            val matchesFormula = filter.selectedFormulaType == null || space.rentalFormulas.any { it.type == filter.selectedFormulaType }
            val matchesFacility = filter.selectedFacility == null || space.essentialFacilities.contains(filter.selectedFacility)
            val matchesEquip = filter.selectedEquipmentCategory == null || space.equipment.any { it.category == filter.selectedEquipmentCategory }
            val matchesPrice = space.baseMonthlyRateUsd <= filter.maxPriceUsd
            val matchesVerified = !filter.onlyVerified || space.isVerified
            val matchesSub = !filter.onlyActiveSubscribed || space.isActiveSubscription

            matchesQuery && matchesGov && matchesType && matchesFormula && matchesFacility && matchesEquip && matchesPrice && matchesVerified && matchesSub
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Financial Metrics
    val activeMrr: Double get() = repository.calculateActiveMrr()
    val potentialMrr: Double get() = repository.calculatePotentialCapacityMrr()
    val projectedArr: Double get() = repository.calculateProjectedArr()
    val totalSettlementVolume: Double get() = repository.calculateTotalSettlementVolume()

    // --- Search & Filter Actions ---
    fun updateSearchQuery(query: String) {
        _searchFilter.value = _searchFilter.value.copy(query = query)
    }

    fun setGovernorateFilter(gov: Governorate?) {
        _searchFilter.value = _searchFilter.value.copy(selectedGovernorate = gov)
    }

    fun setSpaceTypeFilter(type: SpaceType?) {
        _searchFilter.value = _searchFilter.value.copy(selectedSpaceType = type)
    }

    fun setFormulaFilter(formula: RentalFormulaType?) {
        _searchFilter.value = _searchFilter.value.copy(selectedFormulaType = formula)
    }

    fun setFacilityFilter(facility: String?) {
        _searchFilter.value = _searchFilter.value.copy(selectedFacility = facility)
    }

    fun setMaxPrice(price: Double) {
        _searchFilter.value = _searchFilter.value.copy(maxPriceUsd = price)
    }

    fun resetFilters() {
        _searchFilter.value = SearchFilterState()
    }

    fun selectSpace(space: SpaceListing?) {
        _selectedSpace.value = space
    }

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

    fun payBookingViaWhish(bookingId: String, payerName: String, payerPhone: String, context: Context) {
        launchWhishCheckout("BOOKING", bookingId, payerName, payerPhone, context)
    }

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

    fun exportRevenueCsv(startDateMillis: Long?, endDateMillis: Long?): String {
        return repository.exportTransactionsToCsv(startDateMillis, endDateMillis)
    }

    // --- Firebase Auth & Google Credential Manager States ---
    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    private val _authSuccessMessage = MutableStateFlow<String?>(null)
    val authSuccessMessage: StateFlow<String?> = _authSuccessMessage.asStateFlow()

    fun clearAuthMessages() {
        _authErrorMessage.value = null
        _authSuccessMessage.value = null
    }

    // --- Authentication & Member Registration ---
    // Role is NEVER taken from the client here. Sign-in resolves the caller's role from
    // their Firebase Auth ID token's custom claim (assigned server-side by the
    // assignInitialRole/grantAdminRole Cloud Functions, or by grantEntitlement() the
    // moment a package/listing payment settles) — see
    // com.example.data.auth.completeVerifiedLogin / completeVerifiedRegistration.
    private val functionsClient = com.example.data.auth.FirebaseFunctionsClient()

    /**
     * Everything the registration form collects, submitted only AFTER the phone number
     * is already verified (see [startPhoneVerification]/[submitPhoneVerificationCode] —
     * this app has exactly one entry point, phone-first: verify, then — only for a
     * brand-new number — fill in the rest of the profile). Passed to
     * [completePendingRegistration].
     */
    data class PendingPhoneRegistration(
        val fullName: String,
        val email: String,
        val phoneE164: String,
        val specialty: String,
        val country: String,
        val governorate: String,
        val city: String,
        val profilePictureUri: Uri?,
        val idDocumentUri: Uri?
    )

    private var pendingVerificationId: String? = null
    private var pendingIsLinkingGoogleAccount = false

    private fun guessFileExtension(activity: Activity, uri: Uri, fallback: String): String {
        val mime = activity.contentResolver.getType(uri)
        val fromMime = mime?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if (!fromMime.isNullOrBlank()) return fromMime
        val path = uri.lastPathSegment ?: return fallback
        return path.substringAfterLast('.', fallback)
    }

    /**
     * Step 1 of the ONE sign-in/registration entry point this app has: send an SMS OTP
     * to [e164Phone]. There is no separate "Sign In" vs "Register" form anymore — every
     * account, new or returning, starts here with nothing but a phone number. What
     * happens after the code is verified — sign the caller straight into an existing
     * account, or ask them to fill in the rest of a brand-new profile — is decided in
     * [submitPhoneVerificationCode] purely from Firebase's own `isNewUser` signal, never
     * guessed or asked up front. Pass [isLinkingExistingAccount] = true only when
     * completing a Google Sign-In account that has no phone number yet (see
     * [signInWithGoogleCredentialManager]) — that links the phone to the already-signed-in
     * Google identity instead of resolving/creating a separate phone-identified account.
     */
    fun startPhoneVerification(
        activity: Activity,
        e164Phone: String,
        isLinkingExistingAccount: Boolean = false,
        onCodeSent: () -> Unit,
        onVerified: (needsRegistration: Boolean) -> Unit
    ) {
        pendingIsLinkingGoogleAccount = isLinkingExistingAccount
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        authService.sendPhoneVerificationCode(
            activity = activity,
            e164PhoneNumber = e164Phone,
            onCodeSent = { verificationId ->
                pendingVerificationId = verificationId
                _isAuthenticating.value = false
                onCodeSent()
            },
            onAutoVerified = { credential ->
                viewModelScope.launch { finishPhoneVerification(activity, credential, onVerified) }
            },
            onError = { message ->
                _isAuthenticating.value = false
                _authErrorMessage.value = message
            }
        )
    }

    /** Step 2: verifies the SMS code the user typed in, then routes per [finishPhoneVerification]. */
    fun submitPhoneVerificationCode(activity: Activity, smsCode: String, onVerified: (needsRegistration: Boolean) -> Unit) {
        val verificationId = pendingVerificationId
        if (verificationId == null) {
            _authErrorMessage.value = "Please request a verification code first."
            return
        }
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        val credential = authService.buildPhoneAuthCredential(verificationId, smsCode)
        viewModelScope.launch { finishPhoneVerification(activity, credential, onVerified) }
    }

    /**
     * Resolves the verified phone credential and decides what the caller sees next:
     * an existing account (or a Google account being linked) is never routed back
     * through a registration form — only a genuinely brand-new phone number is.
     */
    private suspend fun finishPhoneVerification(
        activity: Activity,
        credential: com.google.firebase.auth.PhoneAuthCredential,
        onVerified: (needsRegistration: Boolean) -> Unit
    ) {
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        val result = if (pendingIsLinkingGoogleAccount) {
            authService.linkPhoneCredential(credential)
        } else {
            authService.signInWithPhoneCredential(credential)
        }
        when (result) {
            is com.example.data.auth.AuthResult.Success -> {
                val firebaseUser = result.firebaseUser
                if (firebaseUser == null) {
                    _isAuthenticating.value = false
                    _authErrorMessage.value = "Phone verification did not return a valid session. Please try again."
                    return
                }
                pendingVerificationId = null
                _isAuthenticating.value = false
                if (!pendingIsLinkingGoogleAccount && !result.isNewUser) {
                    // This exact phone number already had an account — sign the caller
                    // straight into it, no registration form, nothing to overwrite.
                    val user = com.example.data.auth.completeVerifiedLogin(repository, functionsClient, firebaseUser)
                    _authSuccessMessage.value = "Welcome back, ${user.fullName}!"
                    onVerified(false)
                } else {
                    // Brand-new phone number (or a Google account still missing one) —
                    // Firebase Auth already has a signed-in session for it; the caller
                    // just needs to fill in the rest of their profile now.
                    onVerified(true)
                }
            }
            is com.example.data.auth.AuthResult.Error -> {
                _isAuthenticating.value = false
                _authErrorMessage.value = result.message
            }
            com.example.data.auth.AuthResult.Cancelled -> {
                _isAuthenticating.value = false
            }
        }
    }

    /**
     * Step 3 (brand-new accounts only): the phone number is already verified and
     * Firebase Auth already has a signed-in session for it (from
     * [finishPhoneVerification]) — this just uploads the picked files and writes the
     * rest of the profile. No further OTP step; verification already happened.
     */
    fun completePendingRegistration(
        activity: Activity,
        registration: PendingPhoneRegistration,
        onSuccess: () -> Unit
    ) {
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            _authErrorMessage.value = "Your verified session expired — please verify your phone number again."
            return
        }
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        viewModelScope.launch {
            val storageService = com.example.data.storage.FirebaseStorageService.getInstance()
            val profilePictureUrl = registration.profilePictureUri?.let { uri ->
                storageService.uploadProfilePicture(firebaseUser.uid, uri, guessFileExtension(activity, uri, "jpg"))
            }
            val idDocumentUrl = registration.idDocumentUri?.let { uri ->
                storageService.uploadIdDocument(firebaseUser.uid, uri, guessFileExtension(activity, uri, "pdf"))
            }
            val user = com.example.data.auth.completeVerifiedRegistration(
                repository = repository,
                functionsClient = functionsClient,
                firebaseUser = firebaseUser,
                fullName = registration.fullName,
                email = registration.email,
                phone = registration.phoneE164,
                specialty = registration.specialty,
                profilePictureUrl = profilePictureUrl,
                idDocumentUrl = idDocumentUrl,
                country = registration.country,
                governorate = registration.governorate,
                city = registration.city
            )
            pendingIsLinkingGoogleAccount = false
            _isAuthenticating.value = false
            _authSuccessMessage.value = "Account created successfully for ${user.fullName}!"
            onSuccess()
        }
    }

    /**
     * Google Sign-In — a convenience alt path, never a substitute for phone
     * verification. [onNeedsPhoneVerification] fires instead of [onSuccess] when this
     * Google identity has no verified phone number yet (every account needs one — see
     * [startPhoneVerification] with `isLinkingExistingAccount = true` for how the
     * caller should complete that).
     */
    fun signInWithGoogleCredentialManager(
        activityContext: Context,
        onSuccess: () -> Unit,
        onNeedsPhoneVerification: (fullName: String, email: String) -> Unit
    ) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authErrorMessage.value = null
            val authService = com.example.data.auth.FirebaseAuthService(activityContext)
            when (val result = authService.signInWithGoogleCredentialManager(activityContext)) {
                is com.example.data.auth.AuthResult.Success -> {
                    val firebaseUser = result.firebaseUser
                    if (firebaseUser == null) {
                        _isAuthenticating.value = false
                        _authErrorMessage.value = "Google sign-in did not return a valid session. Please try again."
                        return@launch
                    }
                    val user = com.example.data.auth.completeVerifiedLogin(repository, functionsClient, firebaseUser)
                    _isAuthenticating.value = false
                    if (firebaseUser.phoneNumber.isNullOrBlank()) {
                        _authSuccessMessage.value = "Signed in as ${user.fullName} with Google — just need to verify your phone number."
                        onNeedsPhoneVerification(result.displayName ?: user.fullName, result.email)
                    } else {
                        _authSuccessMessage.value = "Google identity verified: ${user.fullName}"
                        onSuccess()
                    }
                }
                is com.example.data.auth.AuthResult.Error -> {
                    _isAuthenticating.value = false
                    _authErrorMessage.value = result.message
                }
                com.example.data.auth.AuthResult.Cancelled -> {
                    _isAuthenticating.value = false
                }
            }
        }
    }

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
        selectedStrategy: String? = null
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
            selectedStrategy = selectedStrategy
        )

        Toast.makeText(
            context,
            "Rental Request #${request.id} Sent! Space hours remain open until owner approval.",
            Toast.LENGTH_LONG
        ).show()

        if (alsoOpenWhatsApp) {
            launchWhatsAppInquiry(context, space, formula, request)
        }

        return request
    }

    fun acceptBookingRequest(requestId: String, context: Context) {
        val request = bookingRequests.value.find { it.id == requestId }
        val success = repository.acceptBookingRequest(requestId)
        if (success) {
            Toast.makeText(context, "Booking Request #${requestId} ACCEPTED! Space schedule is now updated.", Toast.LENGTH_LONG).show()
            if (request != null) {
                postNotificationAlert(
                    title = "Booking Approved! 🎉",
                    body = "Your request for '${request.spaceTitle}' was accepted by host ${request.ownerName}.",
                    category = "BOOKING_ACCEPTANCE",
                    context = context
                )
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
    fun getCsvExport(): String = repository.exportToCsv()
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
