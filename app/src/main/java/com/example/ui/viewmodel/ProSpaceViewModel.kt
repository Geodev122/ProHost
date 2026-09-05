package com.example.ui.viewmodel

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
    val credentialDocuments: StateFlow<List<CredentialDocument>> = repository.credentialDocuments
    val auditLogs: StateFlow<List<AuditSecurityLog>> = repository.auditLogs
    val avatarCampaigns: StateFlow<List<AvatarCampaign>> = repository.avatarCampaigns
    val bookingRequests: StateFlow<List<RentalBookingRequest>> = repository.bookingRequests
    val fcmAlerts: StateFlow<List<FCMAlert>> = repository.fcmAlerts
    val isOfflineMode: StateFlow<Boolean> = repository.isOfflineMode
    val syncStatusMessage: StateFlow<String?> = repository.syncStatusMessage
    val pendingOfflineTransactions: StateFlow<List<WhishTransaction>> = repository.pendingOfflineTransactions

    val currentUserDocuments: StateFlow<List<CredentialDocument>> = combine(credentialDocuments, currentUser) { docs, user ->
        if (user == null) emptyList()
        else docs.filter { it.userId == user.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // --- Admin Pricing Governance ---
    fun setSubscriptionFee(fee: Double) {
        repository.updateMonthlySubscriptionFee(fee)
    }

    fun resetSubscriptionFeeBaseline() {
        repository.resetMonthlySubscriptionFee()
    }

    fun toggleListingVerification(spaceId: String) {
        repository.toggleListingVerification(spaceId)
    }

    fun toggleListingActive(spaceId: String) {
        repository.toggleListingActive(spaceId)
    }

    // --- Whish Pay Settlement ---
    fun paySubscriptionViaWhish(spaceId: String, payerName: String, payerPhone: String, context: Context) {
        val tx = repository.processWhishPaySubscription(spaceId, payerName, payerPhone)
        Toast.makeText(
            context,
            "Whish Pay Confirmed! Order #${tx.orderId} • 30-Day Listing Entitlement Active",
            Toast.LENGTH_LONG
        ).show()
    }

    fun payBookingViaWhish(bookingId: String, payerName: String, payerPhone: String, txId: String, signature: String, context: Context) {
        val success = repository.processWhishPayBooking(bookingId, payerName, payerPhone, txId, signature)
        if (success) {
            Toast.makeText(
                context,
                "Whish Pay Settled! Booking #${bookingId} is now ACCEPTED and fully reserved.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(context, "Payment processing failed.", Toast.LENGTH_SHORT).show()
        }
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

    fun payOwnerPackageViaWhish(
        tier: OwnerPackageTier,
        payerName: String,
        payerPhone: String,
        spaceTypeForPayg: SpaceType?,
        context: Context
    ) {
        val tx = repository.processOwnerPackagePayment(tier, payerName, payerPhone, spaceTypeForPayg)
        Toast.makeText(
            context,
            "Whish Pay Settled! Package ${tier.title} activated successfully. Order: ${tx.orderId}",
            Toast.LENGTH_LONG
        ).show()
    }

    fun payPaygListingViaWhish(
        spaceType: SpaceType,
        payerName: String,
        payerPhone: String,
        context: Context
    ) {
        val tx = repository.processPaygListingPayment(spaceType, payerName, payerPhone)
        Toast.makeText(
            context,
            "Whish Pay Settled! PAYG listing slot for ${spaceType.displayName} purchased. Order: ${tx.orderId}",
            Toast.LENGTH_LONG
        ).show()
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

    // --- Authentication, Member Registration & Role Switching ---
    fun signInWithEmailAndPassword(
        context: Context,
        email: String,
        password: String,
        desiredRole: UserRole,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authErrorMessage.value = null
            val authService = com.example.data.auth.FirebaseAuthService(context)
            when (val result = authService.signInWithEmail(email, password)) {
                is com.example.data.auth.AuthResult.Success -> {
                    val user = repository.login(result.email, desiredRole)
                    _isAuthenticating.value = false
                    _authSuccessMessage.value = "Signed in successfully as ${user.fullName}"
                    onSuccess()
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

    fun registerMemberWithFirebase(
        context: Context,
        fullName: String,
        email: String,
        password: String,
        phone: String,
        role: UserRole,
        specialty: String,
        syndicateNumber: String,
        affiliation: String,
        governorate: Governorate,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authErrorMessage.value = null
            val authService = com.example.data.auth.FirebaseAuthService(context)
            when (val result = authService.registerWithEmail(email, password, fullName)) {
                is com.example.data.auth.AuthResult.Success -> {
                    val user = repository.registerMember(
                        fullName = fullName,
                        email = result.email,
                        phone = phone,
                        role = role,
                        specialty = specialty,
                        syndicateNumber = syndicateNumber,
                        affiliation = affiliation,
                        governorate = governorate
                    )
                    _isAuthenticating.value = false
                    _authSuccessMessage.value = "Account created successfully for ${user.fullName}!"
                    onSuccess()
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

    fun signInWithGoogleCredentialManager(
        activityContext: Context,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authErrorMessage.value = null
            val authService = com.example.data.auth.FirebaseAuthService(activityContext)
            when (val result = authService.signInWithGoogleCredentialManager(activityContext)) {
                is com.example.data.auth.AuthResult.Success -> {
                    val user = repository.login(result.email, null)
                    _isAuthenticating.value = false
                    _authSuccessMessage.value = "Google identity verified: ${user.fullName}"
                    onSuccess()
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

    fun sendPasswordReset(context: Context, email: String) {
        viewModelScope.launch {
            val trimmed = email.trim()
            if (trimmed.isEmpty() || !trimmed.contains("@")) {
                _authErrorMessage.value = "Please provide a valid email address to reset password."
                return@launch
            }
            val authService = com.example.data.auth.FirebaseAuthService(context)
            val result = authService.sendPasswordResetEmail(trimmed)
            if (result.isSuccess) {
                _authSuccessMessage.value = "Password reset instructions sent to $trimmed"
                Toast.makeText(context, "Password reset email sent to $trimmed", Toast.LENGTH_LONG).show()
            } else {
                _authErrorMessage.value = "Failed to send reset email: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun registerMember(
        fullName: String,
        email: String,
        phone: String,
        role: UserRole,
        specialty: String,
        syndicateNumber: String,
        affiliation: String,
        governorate: Governorate
    ): AppUser {
        return repository.registerMember(
            fullName = fullName,
            email = email,
            phone = phone,
            role = role,
            specialty = specialty,
            syndicateNumber = syndicateNumber,
            affiliation = affiliation,
            governorate = governorate
        )
    }

    fun login(email: String, desiredRole: UserRole? = null): AppUser {
        return repository.login(email, desiredRole)
    }

    fun logout() {
        repository.logout()
    }

    fun switchUserRole(role: UserRole) {
        repository.switchRole(role)
    }

    fun logSecurityAction(actionType: String, details: String, severity: String = "INFO") {
        repository.addAuditLog(actionType, details, severity)
    }

    fun updateProfile(
        name: String,
        specialty: String,
        phone: String,
        affiliation: String,
        syndicateNumber: String,
        governorate: Governorate
    ) {
        repository.updateCurrentUserProfile(name, specialty, phone, affiliation, syndicateNumber, governorate)
    }

    // --- Credential Document Operations ---

    fun uploadCredentialDocument(
        type: DocumentType,
        fileName: String,
        fileSizeKb: Int,
        documentNumber: String,
        issuingAuthority: String,
        expiryDate: String,
        fileUri: String? = null
    ): CredentialDocument? {
        val user = currentUser.value ?: return null
        return repository.uploadCredentialDocument(
            userId = user.id,
            type = type,
            fileName = fileName,
            fileSizeKb = fileSizeKb,
            documentNumber = documentNumber,
            issuingAuthority = issuingAuthority,
            expiryDate = expiryDate,
            fileUri = fileUri
        )
    }

    fun removeCredentialDocument(documentId: String) {
        repository.removeCredentialDocument(documentId)
    }

    fun submitForVerification() {
        val user = currentUser.value ?: return
        repository.submitUserVerification(user.id)
    }

    fun adminApproveDocument(documentId: String, notes: String = "Validated against Lebanese Syndicate Registry") {
        repository.adminApproveDocument(documentId, notes)
    }

    fun adminRejectDocument(documentId: String, reason: String) {
        repository.adminRejectDocument(documentId, reason)
    }

    // --- WhatsApp Direct Connection ---
    fun launchWhatsAppInquiry(context: Context, space: SpaceListing, selectedFormula: RentalFormula?, request: RentalBookingRequest? = null) {
        val user = currentUser.value
        val professionalName = user?.fullName ?: "Professional Member"
        val specialty = user?.specialty ?: "Independent Professional"
        val affiliation = user?.affiliation ?: "ProSpace Member Network"
        val syndicate = user?.syndicateNumber ?: "PRO-LB-VERIFIED"

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
            "• Professional Notes: ${request.clinicalNotes}\n" +
            "• In-App Status: PENDING HOST APPROVAL"
        } else ""

        val rawMessage = "Hello ${space.ownerName},\n\n" +
                "I am ${professionalName} (${specialty}, affiliated with ${affiliation}, ID #${syndicate}).\n\n" +
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
        repository.addBlackoutSlot(spaceId, slot)
        Toast.makeText(context, "Blackout hour added: $dayOfWeek ($startTime - $endTime)", Toast.LENGTH_SHORT).show()
    }

    fun removeBlackoutSlot(spaceId: String, slotId: String, context: Context) {
        repository.removeBlackoutSlot(spaceId, slotId)
        Toast.makeText(context, "Blackout slot removed", Toast.LENGTH_SHORT).show()
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
        repository.updateSpaceSchedule(spaceId, updatedSchedule)
        Toast.makeText(context, "Operating schedule updated!", Toast.LENGTH_SHORT).show()
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
        repository.addRentalFormula(spaceId, formula)
        Toast.makeText(context, "New formula '${type.displayName}' added!", Toast.LENGTH_SHORT).show()
    }

    fun deleteFormula(spaceId: String, formulaId: String, context: Context) {
        repository.deleteRentalFormula(spaceId, formulaId)
        Toast.makeText(context, "Rental formula deleted", Toast.LENGTH_SHORT).show()
    }

    // Availability Analytics per Space
    fun getAcceptedBookingsForSpace(spaceId: String): List<RentalBookingRequest> {
        return bookingRequests.value.filter { it.spaceId == spaceId && it.status == BookingRequestStatus.ACCEPTED }
    }

    fun getPendingBookingsForSpace(spaceId: String): List<RentalBookingRequest> {
        return bookingRequests.value.filter { it.spaceId == spaceId && it.status == BookingRequestStatus.PENDING }
    }

    // --- AI Avatar Marketing Campaign Generator ---
    fun generateAvatarCampaignForSpace(space: SpaceListing): AvatarCampaign {
        return repository.generateAvatarCampaign(space)
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
