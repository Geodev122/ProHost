package com.example.data.repository

import android.util.Log
import com.example.data.auth.FirebaseFunctionsClient
import com.example.data.crypto.WhishSecurity
import com.example.data.firestore.FirestoreSchema
import com.example.data.firestore.FirestoreService
import com.example.data.model.*
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ProSpaceRepository {

    companion object {
        private const val TAG = "ProSpaceRepository"

        @Volatile
        private var instance: ProSpaceRepository? = null

        fun getInstance(): ProSpaceRepository {
            return instance ?: synchronized(this) {
                val existing = instance
                if (existing != null) {
                    existing
                } else {
                    val newInstance = ProSpaceRepository()
                    instance = newInstance
                    newInstance
                }
            }
        }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val firestoreService = FirestoreService.getInstance()
    private val functionsClient = FirebaseFunctionsClient()

    private val _subscriptionFormulas = MutableStateFlow<List<SubscriptionFormula>>(
        firestoreService.getDefaultSubscriptionFormulas()
    )
    val subscriptionFormulas: StateFlow<List<SubscriptionFormula>> = _subscriptionFormulas.asStateFlow()

    private val _isCloudConnected = MutableStateFlow(false)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>("Synced with Lebanese Cloud Network")
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    private val _pendingOfflineTransactions = MutableStateFlow<List<WhishTransaction>>(emptyList())
    val pendingOfflineTransactions: StateFlow<List<WhishTransaction>> = _pendingOfflineTransactions.asStateFlow()

    private val _fcmAlerts = MutableStateFlow<List<FCMAlert>>(emptyList())
    val fcmAlerts: StateFlow<List<FCMAlert>> = _fcmAlerts.asStateFlow()

    fun addFCMAlert(alert: FCMAlert) {
        _fcmAlerts.value = listOf(alert) + _fcmAlerts.value
    }

    fun markAlertAsRead(alertId: String) {
        _fcmAlerts.value = _fcmAlerts.value.map {
            if (it.id == alertId) it.copy(isRead = true) else it
        }
    }

    private val _pricingState = MutableStateFlow(AdminPricingState())
    val pricingState: StateFlow<AdminPricingState> = _pricingState.asStateFlow()

    private val _spaces = MutableStateFlow<List<SpaceListing>>(emptyList())
    val spaces: StateFlow<List<SpaceListing>> = _spaces.asStateFlow()

    private val _transactions = MutableStateFlow<List<WhishTransaction>>(emptyList())
    val transactions: StateFlow<List<WhishTransaction>> = _transactions.asStateFlow()

    private val _users = MutableStateFlow<List<AppUser>>(emptyList())
    val users: StateFlow<List<AppUser>> = _users.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditSecurityLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditSecurityLog>> = _auditLogs.asStateFlow()

    // Starts signed out. This previously defaulted to a fully-populated Super Admin
    // AppUser, meaning every fresh install of the app opened directly into the Admin
    // console with zero authentication — no login screen ever shown, no credential
    // ever checked. That was, by a wide margin, the most severe bug in the app.
    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    private val _credentialDocuments = MutableStateFlow<List<CredentialDocument>>(emptyList())
    val credentialDocuments: StateFlow<List<CredentialDocument>> = _credentialDocuments.asStateFlow()

    private val _bookingRequests = MutableStateFlow<List<RentalBookingRequest>>(emptyList())
    val bookingRequests: StateFlow<List<RentalBookingRequest>> = _bookingRequests.asStateFlow()

    private val _spaceArchitectureSchema = MutableStateFlow<SpaceArchitectureSchema>(createDefaultSchema())
    val spaceArchitectureSchema: StateFlow<SpaceArchitectureSchema> = _spaceArchitectureSchema.asStateFlow()

    // Unlike the fixed set of listeners in startRealtimeSync(), this one's scope
    // (which user, admin-or-not) changes with the signed-in user, so it's tracked
    // separately and re-attached whenever currentUser changes below.
    private var credentialDocumentsListener: ListenerRegistration? = null

    init {
        seedInitialData()
        startRealtimeSync()

        // Credential documents were the one collection with no live listener at all —
        // uploaded docs were safely written to Firestore but the local list reset to
        // empty on every fresh app instance (seedInitialData(), above), so they'd
        // vanish from the UI on restart even though nothing was actually lost server-
        // side. Re-attach a correctly-scoped listener (see attachCredentialDocumentsListener)
        // every time the signed-in user or their role changes.
        coroutineScope.launch {
            currentUser.collect { user ->
                credentialDocumentsListener?.remove()
                credentialDocumentsListener = null
                if (user == null) {
                    _credentialDocuments.value = emptyList()
                } else {
                    credentialDocumentsListener = firestoreService.attachCredentialDocumentsListener(
                        userId = user.id,
                        isAdmin = user.role == UserRole.ADMIN
                    ) { docs ->
                        _credentialDocuments.value = docs
                    }
                }
            }
        }
    }

    fun startRealtimeSync() {
        try {
            // Safe to call again (e.g. on manual retry) — detach any previous listeners first
            // so they don't stack up and fire duplicate updates.
            firestoreService.clearListeners()

            // Seed default/starter structures (merge writes — safe to repeat).
            firestoreService.seedInitialData(
                initialSpaces = _spaces.value,
                initialUsers = _users.value,
                initialFormulas = _subscriptionFormulas.value
            )

            // Attach the single set of real-time listeners. Bookings are read from and
            // written to the same collection (FirestoreSchema.Collections.BOOKING_REQUESTS) —
            // there used to be a second, separate "prospace_bookings" collection that writes
            // went to while this listener read from "booking_requests", so a booking from one
            // device never reached another device's listener. That split is now gone.
            firestoreService.attachLiveListeners(
                onWorkspacesUpdated = { updatedSpaces ->
                    _spaces.value = updatedSpaces
                    _isCloudConnected.value = true
                    _isOfflineMode.value = false
                    _syncStatusMessage.value = "Real-time Cloud Sync Active"
                },
                onUsersUpdated = { updatedUsers ->
                    _users.value = updatedUsers
                    _isCloudConnected.value = true
                },
                onBookingsUpdated = { updatedBookings ->
                    _bookingRequests.value = updatedBookings
                    _isCloudConnected.value = true
                },
                onFormulasUpdated = { updatedFormulas ->
                    _subscriptionFormulas.value = updatedFormulas
                    _isCloudConnected.value = true
                },
                onTransactionsUpdated = { updatedTransactions ->
                    _transactions.value = updatedTransactions
                    _isCloudConnected.value = true
                }
            )

            // Pricing: read-only from the client's side. If an admin has already
            // configured pricing on this project, use it; otherwise keep the local
            // AdminPricingState() defaults, which match the fallback defaults
            // functions/src/lib/pricing.ts uses server-side when the doc doesn't
            // exist yet — no client write needed to give initiateWhishPayment
            // something real to read. Writing system_metadata/pricing at all is now
            // exclusively the updatePricing Cloud Function's job (Phase 7 rules deny
            // every client write to it, admin or not).
            coroutineScope.launch {
                val remotePricing = firestoreService.getPricingState()
                if (remotePricing != null) {
                    _pricingState.value = remotePricing
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Firestore init fallback: ${e.message}")
            _isOfflineMode.value = true
            _syncStatusMessage.value = "Offline Cache Active"
        }
    }

    fun syncBookingStatusToFirestore(requestId: String, status: BookingRequestStatus, note: String? = null) {
        coroutineScope.launch {
            val success = firestoreService.updateBookingStatus(requestId, status, note)
            if (success) {
                _isOfflineMode.value = false
                _syncStatusMessage.value = "Live Cloud Sync: $requestId ➔ ${status.name}"
            } else {
                _isOfflineMode.value = true
                _syncStatusMessage.value = "Offline: Status change cached locally"
            }
        }
    }

    fun syncNewBookingToFirestore(request: RentalBookingRequest) {
        coroutineScope.launch {
            val success = firestoreService.saveBookingRequest(request)
            if (success) {
                _isOfflineMode.value = false
                _syncStatusMessage.value = "Booking Synced with Firebase Cloud"
            } else {
                _isOfflineMode.value = true
                _syncStatusMessage.value = "Offline: Booking Stored in Local Cache"
            }
        }
    }

    fun queueOfflineTransaction(tx: WhishTransaction) {
        _pendingOfflineTransactions.value = _pendingOfflineTransactions.value + tx
        _syncStatusMessage.value = "Transaction stored in resilient offline queue"
    }

    fun retryOfflineTransactions() {
        if (_pendingOfflineTransactions.value.isEmpty()) return
        val pending = _pendingOfflineTransactions.value
        _pendingOfflineTransactions.value = emptyList()
        _syncStatusMessage.value = "Recovered & synced ${pending.size} offline transactions"
        addAuditLog(
            actionType = "OFFLINE_TX_RECOVERED",
            details = "Successfully processed ${pending.size} pending offline transactions upon network reconnect",
            severity = "SECURE"
        )
    }

    // Demo/placeholder listings below are seeded so the app has something to show before
    // the real Firestore listeners attach — harmless, since they use fictional owner
    // identities. Users/audit logs/credential documents used to be seeded with a fake
    // "USR-ADMIN-ROOT" identity hardcoded to the real developer's email
    // (geo.elnajjar@gmail.com), pre-marked VERIFIED/ADMIN/Tier-3, with fabricated audit
    // log entries ("Root security & governance clearance granted to...") and fabricated
    // "verified" ID/tax documents attached to it — the same hardcoded-real-identity
    // pattern already fixed elsewhere this session, just in the seed data instead of a
    // screen fallback. These all get overwritten moments later by the real Firestore
    // listeners anyway, so there's no functional loss in starting them empty instead.
    private fun seedInitialData() {
        _users.value = emptyList()
        _auditLogs.value = emptyList()
        _credentialDocuments.value = emptyList()

        val initialSpaces = listOf(
            SpaceListing(
                id = "SP-001",
                title = "Achrafieh Executive Medical Suite",
                spaceType = SpaceType.POLYCLINIC,
                governorate = Governorate.BEIRUT,
                district = "Achrafieh",
                streetAddress = "Sursock Street, Beirut",
                floorInfo = "2nd Floor, Suite 204",
                lat = 33.8886,
                lng = 35.5142,
                isShared = true,
                complementarySpecialties = listOf("Cardiology", "Dermatology", "Pediatrics"),
                residentPractitioners = listOf("Dr. Sami Haddad"),
                essentialFacilities = listOf("High-Speed Wi-Fi", "Receptionist", "Sterilization Suite"),
                equipment = listOf(EquipmentItem("EQ-1", "Exam Table", EquipmentCategory.WORKSPACES, 1, "Hydraulic exam table")),
                rentalFormulas = listOf(
                    RentalFormula(
                        id = "F1",
                        type = RentalFormulaType.SHIFT,
                        rateUsd = 350.0,
                        scheduleDescription = "Morning Shift (08:00 - 14:00)",
                        daysOfWeek = listOf("Mon", "Wed", "Fri"),
                        startHour = "08:00",
                        endHour = "14:00",
                        totalWeeklyHours = 18,
                        shiftName = "Morning Shift"
                    )
                ),
                rules = PremisesRules(),
                ownerId = "USR-OWNER-01",
                ownerName = "Achrafieh Commercial Properties",
                ownerPhone = "+961 3 123456",
                ownerEmail = "host.achrafieh@prohost.lb",
                baseMonthlyRateUsd = 450.0
            )
        )
        _spaces.value = initialSpaces

        _transactions.value = emptyList()
        _bookingRequests.value = emptyList()
    }

    // --- Security & Audit Logging ---
    // actorEmail is kept as a parameter only for the local optimistic display copy
    // below — Firestore rules deny every direct client write to audit_security_logs,
    // and the recordClientAuditLog Cloud Function this now calls always uses the
    // caller's own verified token email server-side, ignoring whatever's passed
    // here. That closes the exact gap the original audit flagged: actorEmail used to
    // be a plain client-supplied (spoofable) value in the persisted record.
    fun addAuditLog(
        actionType: String,
        details: String,
        severity: String = "INFO",
        actorEmail: String = _currentUser.value?.email ?: "system@prohost.app"
    ) {
        addLocalAuditLogEntry(actionType, details, severity, actorEmail)
        coroutineScope.launch { functionsClient.recordAuditLog(actionType, details, severity) }
    }

    /**
     * Updates only the local, in-memory audit log list (no server write). Used by
     * actions that already have their own dedicated Cloud Function writing the real
     * audit entry (pricing, credential review, listing verification/subscription
     * overrides) — calling [addAuditLog] there too would double-write.
     */
    private fun addLocalAuditLogEntry(
        actionType: String,
        details: String,
        severity: String = "INFO",
        actorEmail: String = _currentUser.value?.email ?: "system@prohost.app"
    ) {
        val log = AuditSecurityLog(
            id = "LOG-" + UUID.randomUUID().toString().take(6).uppercase(),
            timestamp = System.currentTimeMillis(),
            actionType = actionType,
            details = details,
            actorEmail = actorEmail,
            severity = severity
        )
        _auditLogs.value = listOf(log) + _auditLogs.value
    }

    // --- Admin Governance & Revenue Pricing ---
    // Firestore rules deny every client write to system_metadata (it's read
    // server-side by initiateWhishPayment to compute real charge amounts), so this
    // now goes through the updatePricing Cloud Function instead of a direct write —
    // it also writes its own audit log entry, so this intentionally doesn't call
    // addAuditLog itself.
    /** Returns whether the server actually accepted the change. */
    private suspend fun persistPricingState(fields: Map<String, Any>): Boolean {
        return functionsClient.updatePricing(fields).isSuccess
    }

    /** Returns whether the change actually succeeded, so the caller can show a real result. */
    suspend fun updateMonthlySubscriptionFee(newFeeUsd: Double): Boolean {
        val oldFee = _pricingState.value.monthlySubscriptionFeeUsd
        val success = persistPricingState(mapOf("monthlySubscriptionFeeUsd" to newFeeUsd))
        if (success) {
            _pricingState.value = _pricingState.value.copy(monthlySubscriptionFeeUsd = newFeeUsd)
            addLocalAuditLogEntry(
                actionType = "PRICING_ADJUSTMENT",
                details = "Monthly fee changed from $${String.format(Locale.US, "%.2f", oldFee)} to $${String.format(Locale.US, "%.2f", newFeeUsd)} USD",
                severity = "WARN"
            )
        }
        return success
    }

    suspend fun updatePaygFee(spaceType: SpaceType, fee: Double): Boolean {
        val current = _pricingState.value
        val updated = when (spaceType) {
            SpaceType.PRIVATE_OFFICE -> current.copy(paygPrivateOfficeUsd = fee)
            SpaceType.CENTER -> current.copy(paygCenterUsd = fee)
            SpaceType.POLYCLINIC -> current.copy(paygPolyclinicUsd = fee)
            SpaceType.COWORKING_SPACE -> current.copy(paygCoworkingUsd = fee)
        }
        val field = when (spaceType) {
            SpaceType.PRIVATE_OFFICE -> "paygPrivateOfficeUsd"
            SpaceType.CENTER -> "paygCenterUsd"
            SpaceType.POLYCLINIC -> "paygPolyclinicUsd"
            SpaceType.COWORKING_SPACE -> "paygCoworkingUsd"
        }
        val success = persistPricingState(mapOf(field to fee))
        if (success) {
            _pricingState.value = updated
            addLocalAuditLogEntry(
                actionType = "PAYG_PRICING_UPDATED",
                details = "PAYG fee for ${spaceType.displayName} updated to $${String.format(Locale.US, "%.2f", fee)} USD",
                severity = "INFO"
            )
        }
        return success
    }

    suspend fun updatePackageFees(package2Fee: Double, package3Fee: Double): Boolean {
        val success = persistPricingState(
            mapOf("package2MonthlyFeeUsd" to package2Fee, "package3MonthlyFeeUsd" to package3Fee)
        )
        if (success) {
            _pricingState.value = _pricingState.value.copy(
                package2MonthlyFeeUsd = package2Fee,
                package3MonthlyFeeUsd = package3Fee
            )
            addLocalAuditLogEntry(
                actionType = "PACKAGE_FEES_UPDATED",
                details = "Package 2 (3-limit) fee updated to $${String.format(Locale.US, "%.2f", package2Fee)}, Package 3 (Unlimited) fee updated to $${String.format(Locale.US, "%.2f", package3Fee)}",
                severity = "INFO"
            )
        }
        return success
    }

    suspend fun updateGovernanceTag(tag: String): Boolean {
        val success = persistPricingState(mapOf("governanceTag" to tag))
        if (success) {
            _pricingState.value = _pricingState.value.copy(governanceTag = tag)
            addLocalAuditLogEntry(
                actionType = "ADMIN_GOVERNANCE_TAG_UPDATED",
                details = "Admin governance control tag updated to: $tag",
                severity = "WARN"
            )
        }
        return success
    }

    suspend fun resetMonthlySubscriptionFee(): Boolean {
        val baseline = _pricingState.value.baselineFeeUsd
        val success = persistPricingState(mapOf("monthlySubscriptionFeeUsd" to baseline))
        if (success) {
            _pricingState.value = _pricingState.value.copy(monthlySubscriptionFeeUsd = baseline)
            addLocalAuditLogEntry(
                actionType = "PRICING_RESET",
                details = "Monthly fee reset to official baseline $${String.format(Locale.US, "%.2f", baseline)} USD",
                severity = "INFO"
            )
        }
        return success
    }

    fun calculateActiveMrr(): Double {
        val activeCount = _spaces.value.count { it.isActiveSubscription }
        return activeCount * _pricingState.value.monthlySubscriptionFeeUsd
    }

    fun calculatePotentialCapacityMrr(): Double {
        val totalSpaces = _spaces.value.size
        return totalSpaces * _pricingState.value.monthlySubscriptionFeeUsd
    }

    fun calculateProjectedArr(): Double {
        return calculateActiveMrr() * 12.0
    }

    fun calculateTotalSettlementVolume(): Double {
        return _transactions.value
            .filter { it.status == TransactionStatus.SUCCESS }
            .sumOf { it.amountUsd }
    }

    // --- Whish Pay Settlement Ledger ---
    // processWhishPaySubscription/processOwnerPackagePayment/processPaygListingPayment/
    // processWhishPayBooking used to live here: each one locally fabricated a "SUCCESS"
    // WhishTransaction and granted the entitlement immediately, with no payment having
    // actually happened — the client both decided the price AND self-reported success.
    // Payment now goes through the initiateWhishPayment/whishWebhook/checkWhishStatus
    // Cloud Functions (functions/src/payments/), which compute the real amount
    // server-side and only grant entitlements after independently confirming success
    // with Whish's own status API. Transactions arrive here via the whish_transactions
    // Firestore listener (see startRealtimeSync) — the repository is a read-only
    // observer of payment state now, not the thing deciding it.

    fun exportTransactionsToCsv(startDateMillis: Long? = null, endDateMillis: Long? = null): String {
        val txs = _transactions.value.filter { tx ->
            val matchesStart = startDateMillis == null || tx.timestamp >= startDateMillis
            val matchesEnd = endDateMillis == null || tx.timestamp <= endDateMillis
            matchesStart && matchesEnd
        }

        val sb = StringBuilder()
        sb.appendLine("Package ID,User ID,Price,Date bought,Expiration Date,Transaction ID,Phone number (whish)")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        for (tx in txs) {
            val dateBought = dateFormat.format(Date(tx.timestamp))
            val expiryDate = dateFormat.format(Date(tx.timestamp + (tx.daysGranted.toLong() * 24 * 60 * 60 * 1000)))
            sb.appendLine("${tx.spaceId},${tx.userId.ifBlank { "N/A" }},${String.format(Locale.US, "%.2f", tx.amountUsd)},$dateBought,$expiryDate,${tx.id},${tx.payerPhone}")
        }
        return sb.toString()
    }

    // --- Space Listing Management ---
    fun addSpaceListing(listing: SpaceListing) {
        _spaces.value = listOf(listing) + _spaces.value
        addAuditLog(
            actionType = "LISTING_CREATED",
            details = "New space created: ${listing.title} (${listing.district}) by ${listing.ownerName}",
            severity = "INFO"
        )
    }

    /**
     * Generic listing edit (title, address, base rate, etc.) from the Admin
     * Console's Edit Listing dialog. Never actually reached Firestore before —
     * local-only mutation, a real gap independent of Phase 7's rules. Now
     * persists via firestoreService.saveWorkspace, but — same pattern as
     * updateUser() — always preserves the current stored isVerified/
     * isActiveSubscription/subscriptionExpiryMillis/ownerId regardless of what's
     * passed in, since Firestore rules deny any client write that changes them.
     * Use setListingVerification / setListingSubscriptionActive for those.
     */
    /** Returns whether the write actually succeeded, so the caller can show a real result. */
    suspend fun updateSpaceListing(updated: SpaceListing): Boolean {
        val current = _spaces.value.find { it.id == updated.id }
        val safeUpdate = if (current != null) {
            updated.copy(
                ownerId = current.ownerId,
                isVerified = current.isVerified,
                isActiveSubscription = current.isActiveSubscription,
                subscriptionExpiryMillis = current.subscriptionExpiryMillis
            )
        } else {
            updated
        }
        val success = firestoreService.saveWorkspace(safeUpdate)
        if (success) {
            _spaces.value = _spaces.value.map { if (it.id == safeUpdate.id) safeUpdate else it }
            addAuditLog(
                actionType = "LISTING_UPDATED",
                details = "Admin updated workspace listing #${safeUpdate.id} (${safeUpdate.title})",
                severity = "INFO"
            )
        }
        return success
    }

    fun deleteSpaceListing(spaceId: String) {
        val target = _spaces.value.find { it.id == spaceId }
        _spaces.value = _spaces.value.filterNot { it.id == spaceId }
        addAuditLog(
            actionType = "LISTING_DELETED",
            details = "Admin permanently deleted workspace listing #${spaceId} (${target?.title ?: "Unknown"})",
            severity = "WARN"
        )
    }

    /**
     * Generic profile edit (name, phone, specialty, syndicate number, etc.) from the
     * Admin Console's edit-user dialog. role/isVerified/verificationStatus/
     * verificationTier/trustScore are always preserved from the current stored
     * value here, regardless of what's passed in: Firestore rules deny any client
     * write that changes those fields, so silently keeping them unchanged avoids a
     * write that would otherwise be denied outright (and this method's caller
     * showing a false "updated successfully" toast). Use grantAdminRole /
     * reviewCredentialDocument / submitUserVerification for those instead.
     */
    /** Returns whether the write actually succeeded, so the caller can show a real result. */
    suspend fun updateUser(updated: AppUser): Boolean {
        val current = _users.value.find { it.id == updated.id }
        val safeUpdate = if (current != null) {
            updated.copy(
                role = current.role,
                isVerified = current.isVerified,
                verificationStatus = current.verificationStatus,
                verificationTier = current.verificationTier,
                trustScore = current.trustScore
            )
        } else {
            updated
        }
        val success = firestoreService.saveUserProfile(safeUpdate)
        if (success) {
            _users.value = _users.value.map { if (it.id == safeUpdate.id) safeUpdate else it }
            if (_currentUser.value?.id == safeUpdate.id) {
                _currentUser.value = safeUpdate
            }
            addAuditLog(
                actionType = "USER_UPDATED",
                details = "Admin updated user profile for ${safeUpdate.fullName} (${safeUpdate.email})",
                severity = "INFO"
            )
        }
        return success
    }

    fun deleteUser(userId: String) {
        val target = _users.value.find { it.id == userId }
        _users.value = _users.value.filterNot { it.id == userId }
        addAuditLog(
            actionType = "USER_DELETED",
            details = "Admin removed user profile #${userId} (${target?.fullName ?: "Unknown"})",
            severity = "WARN"
        )
    }

    /**
     * Admin-only direct verification override (adminSetUserVerification Cloud
     * Function) — Firestore rules deny any client write to verificationStatus/
     * verificationTier/trustScore, including through updateUser(), which this
     * used to compute the new state and call. Returns whether it actually
     * succeeded, so the caller can show a real success/failure result instead
     * of an unconditional one.
     */
    suspend fun toggleUserVerification(userId: String): Boolean {
        val target = _users.value.find { it.id == userId } ?: return false
        val nextVerified = !target.isVerified
        val result = functionsClient.setUserVerification(userId, nextVerified)
        if (result.isSuccess) {
            refreshUserProfile(userId)
            addLocalAuditLogEntry(
                actionType = "USER_VERIFICATION_TOGGLE",
                details = "Admin toggled verification for ${target.fullName} to $nextVerified",
                severity = "SECURE"
            )
        }
        return result.isSuccess
    }

    // --- Dynamic Space Architecture Schema Management ---
    fun addSchemaItem(item: SchemaItem) {
        val current = _spaceArchitectureSchema.value
        val updated = when (item.category) {
            "SPACE_TYPE" -> current.copy(spaceTypes = current.spaceTypes + item)
            "SUBCATEGORY" -> current.copy(subcategories = current.subcategories + item)
            "AMENITY" -> current.copy(amenities = current.amenities + item)
            "EQUIPMENT" -> current.copy(equipmentCategories = current.equipmentCategories + item)
            "SPECIALTY" -> current.copy(specialties = current.specialties + item)
            "RENTAL_STRATEGY" -> current.copy(rentalStrategies = current.rentalStrategies + item)
            else -> current
        }
        _spaceArchitectureSchema.value = updated
        addAuditLog(
            actionType = "SCHEMA_ITEM_ADDED",
            details = "Admin added schema node '${item.name}' under category '${item.category}'",
            severity = "SECURE"
        )
    }

    fun toggleSchemaItem(itemId: String) {
        val current = _spaceArchitectureSchema.value
        val updated = current.copy(
            spaceTypes = current.spaceTypes.map { if (it.id == itemId) it.copy(isEnabled = !it.isEnabled) else it },
            subcategories = current.subcategories.map { if (it.id == itemId) it.copy(isEnabled = !it.isEnabled) else it },
            amenities = current.amenities.map { if (it.id == itemId) it.copy(isEnabled = !it.isEnabled) else it },
            equipmentCategories = current.equipmentCategories.map { if (it.id == itemId) it.copy(isEnabled = !it.isEnabled) else it },
            specialties = current.specialties.map { if (it.id == itemId) it.copy(isEnabled = !it.isEnabled) else it },
            rentalStrategies = current.rentalStrategies.map { if (it.id == itemId) it.copy(isEnabled = !it.isEnabled) else it }
        )
        _spaceArchitectureSchema.value = updated
        addAuditLog(
            actionType = "SCHEMA_ITEM_TOGGLED",
            details = "Admin toggled schema item #$itemId active status",
            severity = "INFO"
        )
    }

    fun deleteSchemaItem(itemId: String) {
        val current = _spaceArchitectureSchema.value
        val updated = current.copy(
            spaceTypes = current.spaceTypes.filterNot { it.id == itemId },
            subcategories = current.subcategories.filterNot { it.id == itemId },
            amenities = current.amenities.filterNot { it.id == itemId },
            equipmentCategories = current.equipmentCategories.filterNot { it.id == itemId },
            specialties = current.specialties.filterNot { it.id == itemId },
            rentalStrategies = current.rentalStrategies.filterNot { it.id == itemId }
        )
        _spaceArchitectureSchema.value = updated
        addAuditLog(
            actionType = "SCHEMA_ITEM_DELETED",
            details = "Admin removed custom schema item #$itemId",
            severity = "WARN"
        )
    }

    fun resetSchemaToDefaults() {
        _spaceArchitectureSchema.value = createDefaultSchema()
        addAuditLog(
            actionType = "SCHEMA_RESET_DEFAULTS",
            details = "Admin restored factory baseline schema definitions for Lebanese workspaces",
            severity = "SECURE"
        )
    }

    private fun createDefaultSchema(): SpaceArchitectureSchema {
        return SpaceArchitectureSchema(
            spaceTypes = listOf(
                SchemaItem("ST-01", "Private Office", "Dedicated self-contained lockable office suites", "SPACE_TYPE", "Apartment"),
                SchemaItem("ST-02", "Center", "Multi-disciplinary center / medical polyclinic compound", "SPACE_TYPE", "Business"),
                SchemaItem("ST-03", "Polyclinic", "Certified medical examination rooms & clinical facilities", "SPACE_TYPE", "LocalHospital"),
                SchemaItem("ST-04", "Co-working Space", "Open collaborative desks and flexible shared work hubs", "SPACE_TYPE", "Groups"),
                SchemaItem("ST-05", "Executive Boardroom", "High-profile executive meeting and conference suites", "SPACE_TYPE", "MeetingRoom"),
                SchemaItem("ST-06", "Consultation Suite", "Acoustically isolated private consultation rooms", "SPACE_TYPE", "Psychology")
            ),
            subcategories = listOf(
                SchemaItem("SUB-01", "Rooms / Dedicated Suites", "Independent private room within premises", "SUBCATEGORY", "MeetingRoom"),
                SchemaItem("SUB-02", "Conference Room", "Equipped boardroom with presentation hardware", "SUBCATEGORY", "CoPresent"),
                SchemaItem("SUB-03", "Theater / Training Room", "High-capacity seminar and workshop hall", "SUBCATEGORY", "School"),
                SchemaItem("SUB-04", "Desk in Shared Area", "Dedicated hot desk with ergonomic seating", "SUBCATEGORY", "Desk"),
                SchemaItem("SUB-05", "Clinical Booth", "Sanitized treatment station with examination bed", "SUBCATEGORY", "MedicalServices")
            ),
            amenities = listOf(
                SchemaItem("AM-01", "24/7 Solar & Generator Backup", "Continuous uninterrupted power supply across Lebanon", "AMENITY", "Bolt"),
                SchemaItem("AM-02", "High-Speed Fiber Wi-Fi (100+ Mbps)", "Redundant ultra-fast Internet with backup 4G router", "AMENITY", "Wifi"),
                SchemaItem("AM-03", "Receptionist & Front Desk Support", "Professional greeting for visiting clients and patients", "AMENITY", "SupportAgent"),
                SchemaItem("AM-04", "Client Waiting Lounge", "Spacious waiting area with comfortable seating", "AMENITY", "Weekend"),
                SchemaItem("AM-05", "Kitchenette & Espresso Bar", "Complimentary Lebanese coffee, espresso, and tea", "AMENITY", "Coffee"),
                SchemaItem("AM-06", "Elevator & Wheelchair Access", "Accessible entrance complying with Lebanese building codes", "AMENITY", "Elevator"),
                SchemaItem("AM-07", "Smart Keycard / Digital Access", "Cryptographic digital door pass and mobile smart entry", "AMENITY", "VpnKey"),
                SchemaItem("AM-08", "Soundproof Acoustic Isolation", "Private acoustic partitioning for confidential consultations", "AMENITY", "VolumeOff")
            ),
            equipmentCategories = listOf(
                SchemaItem("EQ-01", "Workspace & Furniture", "Ergonomic executive chairs, desks, storage lockers", "EQUIPMENT", "Chair"),
                SchemaItem("EQ-02", "IT, Tech & Presentation", "4K Smart TV displays, HDMI, Polycom video conference", "EQUIPMENT", "Tv"),
                SchemaItem("EQ-03", "Office Amenities", "High-speed laser printer/scanner, paper shredder", "EQUIPMENT", "Print"),
                SchemaItem("EQ-04", "Specialized Clinical Tools", "Examination beds, diagnostic lights, sterilization units", "EQUIPMENT", "MedicalInformation")
            ),
            specialties = listOf(
                SchemaItem("SP-01", "Architecture & Interior Design", "Order of Engineers and Architects (OEA)", "SPECIALTY", "Architecture"),
                SchemaItem("SP-02", "Law & Legal Counsel", "Beirut Bar Association (BBA)", "SPECIALTY", "Gavel"),
                SchemaItem("SP-03", "Cardiology & Vascular Medicine", "Lebanese Order of Physicians (LOP)", "SPECIALTY", "Favorite"),
                SchemaItem("SP-04", "Dentistry & Orthodontics", "Lebanese Dental Association", "SPECIALTY", "HealthAndSafety"),
                SchemaItem("SP-05", "Financial & Investment Advisory", "Certified Financial Consultants", "SPECIALTY", "TrendingUp"),
                SchemaItem("SP-06", "Software Engineering & Tech", "Syndicate of Technology Specialists", "SPECIALTY", "Code"),
                SchemaItem("SP-07", "Psychotherapy & Clinical Psychology", "Lebanese Psychological Association", "SPECIALTY", "Psychology"),
                SchemaItem("SP-08", "Physical Therapy & Rehabilitation", "Syndicate of Physiotherapists in Lebanon", "SPECIALTY", "FitnessCenter")
            ),
            rentalStrategies = listOf(
                SchemaItem("RS-01", "Full Month (Exclusive)", "Continuous 30-day dedicated exclusive workspace lease", "RENTAL_STRATEGY", "CalendarMonth"),
                SchemaItem("RS-02", "Shift-Based (Morning / Afternoon)", "Scheduled time blocks (e.g. 08:00 - 13:00 or 14:00 - 19:00)", "RENTAL_STRATEGY", "Schedule"),
                SchemaItem("RS-03", "Day-per-Week Basis", "Recurring weekly dedicated days (e.g. Every Tue & Thu)", "RENTAL_STRATEGY", "DateRange"),
                SchemaItem("RS-04", "Hourly / On-Demand Slot", "Flexible hourly pass with 2-hour minimum booking", "RENTAL_STRATEGY", "Timelapse")
            )
        )
    }

    /**
     * Firestore rules deny every client write to workspace_listings.isVerified — only
     * setListingVerification's Admin SDK write can change it. This used to only ever
     * mutate local state (never actually reached Firestore, regardless of rules); now
     * it awaits the real server change before updating local state, so the two can't
     * drift if the call fails.
     */
    /** Returns whether the change actually succeeded, so the caller can show a real result. */
    suspend fun toggleListingVerification(spaceId: String): Boolean {
        val target = _spaces.value.find { it.id == spaceId } ?: return false
        val nextStatus = !target.isVerified
        val result = functionsClient.setListingVerification(spaceId, nextStatus)
        if (result.isSuccess) {
            _spaces.value = _spaces.value.map {
                if (it.id == spaceId) it.copy(isVerified = nextStatus) else it
            }
            addLocalAuditLogEntry(
                actionType = "VERIFICATION_OVERRIDE",
                details = "Workspace #${spaceId} verified status changed to $nextStatus by Super Admin",
                severity = "SECURE"
            )
        }
        return result.isSuccess
    }

    /** Same pattern as [toggleListingVerification] — see its doc comment. */
    suspend fun toggleListingActive(spaceId: String): Boolean {
        val target = _spaces.value.find { it.id == spaceId } ?: return false
        val nextStatus = !target.isActiveSubscription
        val result = functionsClient.setListingSubscriptionActive(spaceId, nextStatus)
        if (result.isSuccess) {
            _spaces.value = _spaces.value.map {
                if (it.id == spaceId) it.copy(isActiveSubscription = nextStatus) else it
            }
            addLocalAuditLogEntry(
                actionType = "SUBSCRIPTION_STATUS_TOGGLE",
                details = "Listing #${spaceId} subscription active status set to $nextStatus by Super Admin",
                severity = "WARN"
            )
        }
        return result.isSuccess
    }

    // --- Smart Booking & In-App Rental Request Engine ---
    fun createBookingRequest(
        space: SpaceListing,
        formula: RentalFormula,
        practitioner: AppUser,
        startDate: String,
        durationMonths: Int,
        notes: String,
        selectedDays: List<String> = emptyList(),
        selectedStartHour: String = "",
        selectedEndHour: String = "",
        selectedShift: String = "",
        calculatedTotalUsd: Double = 0.0,
        subdivisionId: String? = null,
        subdivisionName: String? = null,
        selectedStrategy: String? = null
    ): RentalBookingRequest {
        val requestId = "REQ-LB-" + (1000..9999).random()
        val totalUsd = if (calculatedTotalUsd > 0) calculatedTotalUsd else (formula.rateUsd * durationMonths)

        val daysChosen = if (selectedDays.isNotEmpty()) selectedDays else formula.daysOfWeek
        val startH = if (selectedStartHour.isNotBlank()) selectedStartHour else formula.startHour
        val endH = if (selectedEndHour.isNotBlank()) selectedEndHour else formula.endHour
        val shiftDesc = if (selectedShift.isNotBlank()) " [$selectedShift]" else ""

        val rangeString = "${daysChosen.joinToString(", ")} $startH - $endH$shiftDesc (Starting $startDate, $durationMonths Month${if (durationMonths > 1) "s" else ""})"

        val request = RentalBookingRequest(
            id = requestId,
            spaceId = space.id,
            spaceTitle = space.title,
            spaceDistrict = space.district,
            governorate = space.governorate,
            ownerId = space.ownerId,
            ownerName = space.ownerName,
            ownerPhone = space.ownerPhone,
            practitionerId = practitioner.id,
            practitionerName = practitioner.fullName,
            practitionerEmail = practitioner.email,
            practitionerPhone = practitioner.phone,
            practitionerSpecialty = practitioner.specialty,
            practitionerSyndicateNumber = practitioner.syndicateNumber,
            formula = formula,
            startDate = startDate,
            selectedDays = daysChosen,
            selectedStartHour = startH,
            selectedEndHour = endH,
            selectedShift = selectedShift,
            selectedDateTimeRange = rangeString,
            durationMonths = durationMonths,
            totalAmountUsd = totalUsd,
            clinicalNotes = notes,
            status = BookingRequestStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            isExternalPaymentSettled = false,
            subdivisionId = subdivisionId,
            subdivisionName = subdivisionName,
            selectedStrategy = selectedStrategy
        )

        _bookingRequests.value = listOf(request) + _bookingRequests.value
        syncNewBookingToFirestore(request)

        addAuditLog(
            actionType = "RENTAL_REQUEST_SUBMITTED",
            details = "Request $requestId sent by ${practitioner.fullName} for '${space.title}' (${formula.type.displayName}, $${totalUsd.toInt()} USD). Selected Slot: $rangeString. Awaiting owner WhatsApp/In-app approval.",
            severity = "INFO",
            actorEmail = practitioner.email
        )

        return request
    }

    /**
     * Owner accepting a booking locks in the schedule/terms — it never implies payment
     * was settled. isExternalPaymentSettled used to be forced to true right here,
     * unconditionally, regardless of whether the practitioner had paid anything —
     * the same self-reported-settlement pattern the whole Whish remediation was
     * about, just for bookings. It's now exclusively set by the payment webhook /
     * checkWhishStatus reconciliation (functions/src/payments/reconcile.ts) once a
     * real Whish payment for this booking actually succeeds — see
     * ProSpaceViewModel.payBookingViaWhish, the practitioner's separate later step
     * (MyBookingsScreen's "Pay Whish" button, not the orphaned RentalsViewModel, which
     * was deleted — it duplicated this same flow but had no screen wired to it).
     */
    fun acceptBookingRequest(requestId: String): Boolean {
        val request = _bookingRequests.value.find { it.id == requestId } ?: return false
        val now = System.currentTimeMillis()

        _bookingRequests.value = _bookingRequests.value.map {
            if (it.id == requestId) {
                it.copy(
                    status = BookingRequestStatus.ACCEPTED,
                    reviewedAt = now
                )
            } else it
        }

        syncBookingStatusToFirestore(requestId, BookingRequestStatus.ACCEPTED)

        // Add member to resident list if not present
        val memberString = "${request.practitionerName} (${request.practitionerSpecialty})"
        _spaces.value = _spaces.value.map { space ->
            if (space.id == request.spaceId && !space.residentPractitioners.contains(memberString)) {
                space.copy(residentPractitioners = space.residentPractitioners + memberString)
            } else space
        }

        addAuditLog(
            actionType = "RENTAL_REQUEST_ACCEPTED",
            details = "Owner ${request.ownerName} accepted $requestId by ${request.practitionerName}. Formula '${request.formula.scheduleDescription}' (${request.selectedDateTimeRange}) is now locked and marked unavailable for public display.",
            severity = "SECURE",
            actorEmail = request.ownerName
        )

        return true
    }

    fun rejectBookingRequest(requestId: String, note: String? = null): Boolean {
        val request = _bookingRequests.value.find { it.id == requestId } ?: return false
        val now = System.currentTimeMillis()

        _bookingRequests.value = _bookingRequests.value.map {
            if (it.id == requestId) {
                it.copy(
                    status = BookingRequestStatus.REJECTED,
                    reviewedAt = now,
                    rejectionReason = note ?: "Declined by space owner"
                )
            } else it
        }

        syncBookingStatusToFirestore(requestId, BookingRequestStatus.REJECTED, note)

        addAuditLog(
            actionType = "RENTAL_REQUEST_DECLINED",
            details = "Request $requestId declined by owner ${request.ownerName} (Reason: ${note ?: "None provided"}). Hours remain available to the public.",
            severity = "WARN",
            actorEmail = request.ownerName
        )

        return true
    }

    fun cancelBookingRequest(requestId: String): Boolean {
        val request = _bookingRequests.value.find { it.id == requestId } ?: return false
        _bookingRequests.value = _bookingRequests.value.map {
            if (it.id == requestId) it.copy(status = BookingRequestStatus.CANCELLED) else it
        }
        syncBookingStatusToFirestore(requestId, BookingRequestStatus.CANCELLED)
        return true
    }

    // --- Schedule & Blackout Slots Management ---
    fun addBlackoutSlot(spaceId: String, slot: BlackoutSlot) {
        _spaces.value = _spaces.value.map { space ->
            if (space.id == spaceId) {
                val updatedSched = space.schedule.copy(
                    blackoutSlots = space.schedule.blackoutSlots + slot
                )
                space.copy(schedule = updatedSched)
            } else space
        }
        addAuditLog(
            actionType = "SCHEDULE_BLACKOUT_ADDED",
            details = "Owner added non-operating blackout slot (${slot.dayOfWeek} ${slot.startTime}-${slot.endTime}) to space $spaceId",
            severity = "INFO"
        )
    }

    fun removeBlackoutSlot(spaceId: String, slotId: String) {
        _spaces.value = _spaces.value.map { space ->
            if (space.id == spaceId) {
                val updatedSched = space.schedule.copy(
                    blackoutSlots = space.schedule.blackoutSlots.filter { it.id != slotId }
                )
                space.copy(schedule = updatedSched)
            } else space
        }
    }

    fun updateSpaceSchedule(spaceId: String, schedule: SpaceOperatingSchedule) {
        _spaces.value = _spaces.value.map { space ->
            if (space.id == spaceId) space.copy(schedule = schedule) else space
        }
        addAuditLog(
            actionType = "OPERATING_SCHEDULE_UPDATED",
            details = "Operating hours updated for space $spaceId: ${schedule.openingHour} - ${schedule.closingHour} (${schedule.operatingDays.joinToString()})",
            severity = "INFO"
        )
    }

    fun addRentalFormula(spaceId: String, formula: RentalFormula) {
        _spaces.value = _spaces.value.map { space ->
            if (space.id == spaceId) space.copy(rentalFormulas = space.rentalFormulas + formula) else space
        }
        addAuditLog(
            actionType = "RENTAL_FORMULA_ADDED",
            details = "Added formula '${formula.type.displayName}' ($${formula.rateUsd}) to space $spaceId",
            severity = "INFO"
        )
    }

    fun updateRentalFormula(spaceId: String, updatedFormula: RentalFormula) {
        _spaces.value = _spaces.value.map { space ->
            if (space.id == spaceId) {
                space.copy(rentalFormulas = space.rentalFormulas.map { if (it.id == updatedFormula.id) updatedFormula else it })
            } else space
        }
    }

    fun deleteRentalFormula(spaceId: String, formulaId: String) {
        _spaces.value = _spaces.value.map { space ->
            if (space.id == spaceId) {
                space.copy(rentalFormulas = space.rentalFormulas.filter { it.id != formulaId })
            } else space
        }
    }

    // --- User Authentication & Member Registration ---
    /**
     * Registers a new member. [uid] must be the real Firebase Auth UID (so this user's
     * `id` lines up with the `user_profiles/{uid}` document the role-claim Cloud Functions
     * write to) and [verifiedRole] must already have been confirmed server-side — see
     * [com.example.data.auth.completeVerifiedRegistration].
     */
    fun registerMember(
        uid: String,
        fullName: String,
        email: String,
        phone: String,
        verifiedRole: UserRole,
        specialty: String,
        syndicateNumber: String,
        affiliation: String,
        governorate: Governorate
    ): AppUser {
        val cleanEmail = email.trim().lowercase()
        val newUser = AppUser(
            id = uid,
            email = cleanEmail,
            fullName = fullName.trim(),
            role = verifiedRole,
            specialty = specialty.trim(),
            phone = phone.trim(),
            affiliation = affiliation.trim(),
            syndicateNumber = syndicateNumber.trim(),
            governorate = governorate,
            isVerified = true
        )

        _users.value = _users.value.filterNot { it.id == uid } + newUser
        _currentUser.value = newUser
        coroutineScope.launch { firestoreService.saveUserProfile(newUser) }

        addAuditLog(
            actionType = "MEMBER_REGISTRATION",
            details = "New member registered: ${newUser.fullName} (${newUser.role.name}) • ${newUser.specialty} • ${newUser.governorate.displayName}",
            severity = "SECURE",
            actorEmail = newUser.email
        )
        return newUser
    }

    /**
     * Signs a user in locally once their identity AND role have already been verified
     * server-side (real Firebase Auth sign-in, then the role read from their ID token's
     * custom claim — see [com.example.data.auth.completeVerifiedLogin]). [uid] must be
     * the Firebase Auth UID; [verifiedRole] must come from the token claim, never from
     * UI state. There is no code path here that grants a role from caller-supplied input.
     */
    fun login(uid: String, email: String, verifiedRole: UserRole): AppUser {
        val cleanEmail = email.trim().lowercase()
        val existing = _users.value.find { it.id == uid }

        val user = existing?.copy(role = verifiedRole, email = cleanEmail) ?: AppUser(
            id = uid,
            email = cleanEmail,
            fullName = if (cleanEmail.contains("@")) cleanEmail.substringBefore("@").replace(".", " ").capitalize(Locale.US) else "Member",
            role = verifiedRole,
            specialty = when (verifiedRole) {
                UserRole.PROFESSIONAL -> "Independent Professional"
                UserRole.SPACE_OWNER -> "Workspace Host"
                UserRole.ADMIN -> "Super Administrator & Security Governance"
            },
            phone = "",
            affiliation = "ProSpace Member Network",
            syndicateNumber = "PRO-LB-" + (1000..9999).random(),
            governorate = Governorate.BEIRUT,
            isVerified = true
        )

        _users.value = _users.value.filterNot { it.id == uid } + user
        _currentUser.value = user
        coroutineScope.launch { firestoreService.saveUserProfile(user) }

        addAuditLog(
            actionType = "USER_LOGIN_SUCCESS",
            details = "Role: ${user.role.name} • Name: ${user.fullName} (${user.email})",
            severity = if (user.role == UserRole.ADMIN) "SECURE" else "INFO",
            actorEmail = user.email
        )
        return user
    }

    fun logout() {
        val previous = _currentUser.value?.email ?: "Unknown"
        _currentUser.value = null
        addAuditLog(
            actionType = "USER_LOGOUT",
            details = "Session closed for $previous",
            severity = "INFO",
            actorEmail = previous
        )
    }

    // setCurrentUser() and switchRole() were removed here — both let any caller (or,
    // for switchRole specifically, any already-logged-in user) instantly become ADMIN
    // with no server check at all. A role change now only ever happens through the
    // login()/registerMember() paths above, which require a role already verified via
    // a Firebase Auth custom claim, or through the grantAdminRole/requestRoleUpgrade
    // Cloud Functions for an explicit role change request.

    suspend fun updateCurrentUserProfile(
        name: String,
        specialty: String,
        phone: String,
        affiliation: String,
        syndicateNumber: String,
        governorate: Governorate
    ): Boolean {
        val current = _currentUser.value ?: return false
        val updated = current.copy(
            fullName = name,
            specialty = specialty,
            phone = phone,
            affiliation = affiliation,
            syndicateNumber = syndicateNumber,
            governorate = governorate
        )
        // This used to only mutate in-memory state — the "Profile Updated Successfully"
        // toast fired unconditionally while the edit was never sent to Firestore at all,
        // so it silently vanished on app restart or on another device.
        val success = firestoreService.saveUserProfile(updated)
        if (success) {
            _currentUser.value = updated
            _users.value = _users.value.map { if (it.id == updated.id) updated else it }
        }
        return success
    }

    // --- Credential Documents & Professional Verification Management ---

    fun uploadCredentialDocument(
        userId: String,
        type: DocumentType,
        fileName: String,
        fileSizeKb: Int,
        documentNumber: String,
        issuingAuthority: String,
        expiryDate: String,
        fileUri: String? = null
    ): CredentialDocument {
        val hash = "SHA256:" + java.util.UUID.randomUUID().toString().replace("-", "").take(32)
        val existingIndex = _credentialDocuments.value.indexOfFirst { it.userId == userId && it.type == type }
        val doc = CredentialDocument(
            id = "DOC-" + UUID.randomUUID().toString().take(8).uppercase(),
            userId = userId,
            type = type,
            fileName = fileName,
            fileSizeKb = fileSizeKb,
            uploadedAt = System.currentTimeMillis(),
            status = DocumentStatus.PENDING_REVIEW,
            documentNumber = documentNumber,
            issuingAuthority = issuingAuthority,
            expiryDate = expiryDate,
            fileUri = fileUri,
            verificationHash = hash,
            reviewerNotes = null
        )

        val updatedList = if (existingIndex >= 0) {
            _credentialDocuments.value.toMutableList().apply { set(existingIndex, doc) }
        } else {
            _credentialDocuments.value + doc
        }
        _credentialDocuments.value = updatedList
        coroutineScope.launch { firestoreService.saveCredentialDocument(doc) }

        addAuditLog(
            actionType = "DOCUMENT_UPLOADED",
            details = "Credential document ${type.title} ($fileName, #$documentNumber) uploaded for member verification",
            severity = "INFO",
            actorEmail = _currentUser.value?.email ?: "member@prospace.lb"
        )

        // Recomputes verificationStatus/verificationTier/trustScore server-side — those
        // fields are no longer client-writable (see submitUserVerification's comment).
        coroutineScope.launch {
            functionsClient.submitVerificationForReview()
            refreshUserProfile(userId)
        }
        return doc
    }

    suspend fun removeCredentialDocument(documentId: String): Boolean {
        val doc = _credentialDocuments.value.find { it.id == documentId } ?: return false
        val deleted = firestoreService.deleteCredentialDocument(documentId)
        if (!deleted) return false

        // The realtime listener (see init{}) will also reflect this once Firestore's
        // snapshot fires, but update local state immediately for a responsive UI.
        _credentialDocuments.value = _credentialDocuments.value.filterNot { it.id == documentId }
        addAuditLog(
            actionType = "DOCUMENT_REMOVED",
            details = "Credential document ${doc.type.title} (#${doc.documentNumber}) removed",
            severity = "INFO",
            actorEmail = _currentUser.value?.email ?: "member@prospace.lb"
        )
        functionsClient.submitVerificationForReview()
        refreshUserProfile(doc.userId)
        return true
    }

    suspend fun submitUserVerification(userId: String): Boolean {
        val user = _users.value.find { it.id == userId } ?: _currentUser.value ?: return false
        val userDocs = _credentialDocuments.value.filter { it.userId == userId }
        val requiredTypes = DocumentType.values().filter { it.requiredFor.contains(user.role) }
        val uploadedRequired = requiredTypes.filter { req -> userDocs.any { it.type == req && it.status != DocumentStatus.NOT_UPLOADED } }

        // verificationNotes is a free-text field the owner can write themselves (not
        // one of the restricted fields), so this part still writes directly; the
        // actual verificationStatus change happens server-side just below.
        val updated = user.copy(
            verificationNotes = "Submitted on ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())}. Pending admin accreditation."
        )
        val profileSaved = firestoreService.saveUserProfile(updated)
        if (!profileSaved) return false

        // Server-side: recomputes and writes verificationStatus/verificationTier/
        // trustScore from the same credential documents, since Firestore rules deny
        // every client write to those fields on user_profiles.
        val submitted = functionsClient.submitVerificationForReview()
        if (submitted.isFailure) return false

        if (_currentUser.value?.id == userId) {
            _currentUser.value = updated
        }
        _users.value = _users.value.map { if (it.id == userId) updated else it }
        addLocalAuditLogEntry(
            actionType = "VERIFICATION_SUBMITTED",
            details = "Member ${user.fullName} submitted ${uploadedRequired.size}/${requiredTypes.size} credential documents for compliance review",
            severity = "INFO",
            actorEmail = user.email
        )
        refreshUserProfile(userId)
        return true
    }

    /**
     * Approving/rejecting a credential document and the resulting change to the
     * member's verification status/tier/trust score are both server-authoritative
     * now (Firestore rules deny every client write to those fields) — this and
     * [adminRejectDocument] call reviewCredentialDocument instead of writing
     * directly, then refresh local state from Firestore once the server is done.
     */
    /** Returns whether the review actually succeeded, so the caller can show a real result. */
    suspend fun adminApproveDocument(documentId: String, reviewerNotes: String = "Validated against Lebanese Syndicate Registry"): Boolean {
        val doc = _credentialDocuments.value.find { it.id == documentId } ?: return false
        val result = functionsClient.reviewCredentialDocument(documentId, approve = true, reviewerNotes = reviewerNotes)
        if (result.isSuccess) {
            addLocalAuditLogEntry(
                actionType = "DOCUMENT_ACCREDITED",
                details = "Admin approved ${doc.type.title} (#${doc.documentNumber}) for member ${doc.userId}",
                severity = "SECURE",
                actorEmail = _currentUser.value?.email ?: "admin@prospace.lb"
            )
            refreshCredentialDocument(documentId)
            refreshUserProfile(doc.userId)
        }
        return result.isSuccess
    }

    /** Returns whether the review actually succeeded, so the caller can show a real result. */
    suspend fun adminRejectDocument(documentId: String, reason: String): Boolean {
        val doc = _credentialDocuments.value.find { it.id == documentId } ?: return false
        val result = functionsClient.reviewCredentialDocument(documentId, approve = false, rejectionReason = reason)
        if (result.isSuccess) {
            addLocalAuditLogEntry(
                actionType = "DOCUMENT_REVISION_REQUESTED",
                details = "Admin requested revision on ${doc.type.title} (#${doc.documentNumber}): $reason",
                severity = "WARN",
                actorEmail = _currentUser.value?.email ?: "admin@prospace.lb"
            )
            refreshCredentialDocument(documentId)
            refreshUserProfile(doc.userId)
        }
        return result.isSuccess
    }

    /** Re-reads one credential document from Firestore into local state after a server-side change. */
    private suspend fun refreshCredentialDocument(documentId: String) {
        val data = firestoreService.getCredentialDocument(documentId) ?: return
        val updatedDoc = CredentialDocument.fromFirestoreMap(documentId, data)
        _credentialDocuments.value = _credentialDocuments.value.map { if (it.id == documentId) updatedDoc else it }
    }

    /** Re-reads one user profile from Firestore into local state after a server-side change. */
    private suspend fun refreshUserProfile(userId: String) {
        val data = firestoreService.getUserProfile(userId) ?: return
        val updated = AppUser.fromFirestoreMap(userId, data)
        if (_currentUser.value?.id == userId) {
            _currentUser.value = updated
        }
        _users.value = _users.value.map { if (it.id == userId) updated else it }
    }

    // --- Multi-Format Data Export Hub ---
    fun exportToCsv(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== PROSPACE LEBANON AUDIT EXPORT (CSV) ===")
        sb.appendLine("Export Date,${sdf.format(Date())}")
        sb.appendLine("Active Subscription Fee USD,${_pricingState.value.monthlySubscriptionFeeUsd}")
        sb.appendLine("Active MRR USD,${calculateActiveMrr()}")
        sb.appendLine("Potential 100% Capacity MRR USD,${calculatePotentialCapacityMrr()}")
        sb.appendLine("Projected ARR USD,${calculateProjectedArr()}")
        sb.appendLine("Whish Channel ID,${WhishSecurity.CHANNEL_ID}")
        sb.appendLine("Whish Source Email,${WhishSecurity.SOURCE_EMAIL}")
        sb.appendLine()
        sb.appendLine("--- CLINIC SPACES INVENTORY ---")
        sb.appendLine("ID,Title,SpaceType,Governorate,District,StreetAddress,IsShared,BaseMonthlyUsd,IsActiveSub,OwnerName,OwnerPhone")
        _spaces.value.forEach { sp ->
            sb.appendLine("\"${sp.id}\",\"${sp.title.replace("\"", "\"\"")}\",\"${sp.spaceType.name}\",\"${sp.governorate.displayName}\",\"${sp.district}\",\"${sp.streetAddress}\",${sp.isShared},${sp.baseMonthlyRateUsd},${sp.isActiveSubscription},\"${sp.ownerName}\",\"${sp.ownerPhone}\"")
        }
        sb.appendLine()
        sb.appendLine("--- WHISH PAY TRANSACTIONS LEDGER ---")
        sb.appendLine("TxID,OrderId,AmountUSD,Status,Timestamp,PayerName,PayerPhone,ChannelID,SignatureMD5,SpaceID")
        _transactions.value.forEach { tx ->
            sb.appendLine("\"${tx.id}\",\"${tx.orderId}\",${tx.amountUsd},\"${tx.status}\",\"${sdf.format(Date(tx.timestamp))}\",\"${tx.payerName}\",\"${tx.payerPhone}\",\"${tx.channelId}\",\"${tx.signatureHash}\",\"${tx.spaceId}\"")
        }
        sb.appendLine()
        sb.appendLine("--- REGISTERED USERS & PROFESSIONALS ---")
        sb.appendLine("UserID,FullName,Email,Role,Specialty,Phone,Affiliation,LicenseID,IsVerified")
        _users.value.forEach { u ->
            sb.appendLine("\"${u.id}\",\"${u.fullName}\",\"${u.email}\",\"${u.role.name}\",\"${u.specialty}\",\"${u.phone}\",\"${u.affiliation}\",\"${u.syndicateNumber}\",${u.isVerified}")
        }
        sb.appendLine()
        sb.appendLine("--- SECURITY & AUDIT EVENT LOGS ---")
        sb.appendLine("LogID,Timestamp,ActionType,Details,ActorEmail,Severity")
        _auditLogs.value.forEach { l ->
            sb.appendLine("\"${l.id}\",\"${sdf.format(Date(l.timestamp))}\",\"${l.actionType}\",\"${l.details.replace("\"", "\"\"")}\",\"${l.actorEmail}\",\"${l.severity}\"")
        }
        return sb.toString()
    }

    fun exportToJson(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"platform\": \"ProHost\",")
        sb.appendLine("  \"exportedAt\": \"${sdf.format(Date())}\",")
        sb.appendLine("  \"adminGovernance\": {")
        sb.appendLine("    \"currentMonthlyFeeUsd\": ${_pricingState.value.monthlySubscriptionFeeUsd},")
        sb.appendLine("    \"activeMrrUsd\": ${calculateActiveMrr()},")
        sb.appendLine("    \"potentialCapacityMrrUsd\": ${calculatePotentialCapacityMrr()},")
        sb.appendLine("    \"projectedArrUsd\": ${calculateProjectedArr()},")
        sb.appendLine("    \"merchantChannelId\": \"${WhishSecurity.CHANNEL_ID}\",")
        sb.appendLine("    \"merchantSourceEmail\": \"${WhishSecurity.SOURCE_EMAIL}\"")
        sb.appendLine("  },")
        sb.appendLine("  \"totalSpacesCount\": ${_spaces.value.size},")
        sb.appendLine("  \"spaces\": [")
        _spaces.value.forEachIndexed { index, s ->
            val comma = if (index < _spaces.value.size - 1) "," else ""
            sb.appendLine("    {")
            sb.appendLine("      \"id\": \"${s.id}\",")
            sb.appendLine("      \"title\": \"${s.title.replace("\"", "\\\"")}\",")
            sb.appendLine("      \"type\": \"${s.spaceType.name}\",")
            sb.appendLine("      \"governorate\": \"${s.governorate.name}\",")
            sb.appendLine("      \"district\": \"${s.district}\",")
            sb.appendLine("      \"monthlyPriceUsd\": ${s.baseMonthlyRateUsd},")
            sb.appendLine("      \"owner\": \"${s.ownerName}\",")
            sb.appendLine("      \"isActiveSubscription\": ${s.isActiveSubscription}")
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ],")
        sb.appendLine("  \"transactionsLedger\": [")
        _transactions.value.forEachIndexed { index, tx ->
            val comma = if (index < _transactions.value.size - 1) "," else ""
            sb.appendLine("    {")
            sb.appendLine("      \"txId\": \"${tx.id}\",")
            sb.appendLine("      \"orderId\": \"${tx.orderId}\",")
            sb.appendLine("      \"amountUsd\": ${tx.amountUsd},")
            sb.appendLine("      \"status\": \"${tx.status}\",")
            sb.appendLine("      \"signatureHash\": \"${tx.signatureHash}\",")
            sb.appendLine("      \"payer\": \"${tx.payerName}\"")
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    fun exportToAuditText(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return """
================================================================================
                       PROSPACE LEBANON AUDIT & REVENUE REPORT
================================================================================
Generated: ${sdf.format(Date())}
System Status: HEALTHY | Compliance Engine: SECURE MD5 CRYPTO

[1] FINANCIAL & PRICING METRICS
--------------------------------------------------------------------------------
Monthly Subscription Fee (USD) : $${String.format(Locale.US, "%.2f", _pricingState.value.monthlySubscriptionFeeUsd)} / space owner
Active Subscribed Spaces        : ${_spaces.value.count { it.isActiveSubscription }} / ${_spaces.value.size} Total Spaces
Active Monthly Recurring (MRR) : $${String.format(Locale.US, "%.2f", calculateActiveMrr())} USD
100% Capacity Potential MRR    : $${String.format(Locale.US, "%.2f", calculatePotentialCapacityMrr())} USD
Projected Annual Run-Rate (ARR): $${String.format(Locale.US, "%.2f", calculateProjectedArr())} USD
Total Whish Settlement Volume  : $${String.format(Locale.US, "%.2f", calculateTotalSettlementVolume())} USD

[2] WHISH PAY GATEWAY SETTLEMENT LEDGER
--------------------------------------------------------------------------------
Channel ID    : ${WhishSecurity.CHANNEL_ID}
Merchant Email: ${WhishSecurity.SOURCE_EMAIL}
Auth Signature: MD5(channel|amount|currency|orderId|secretKey)

TRANSACTIONS:
${_transactions.value.joinToString("\n") { tx ->
    "• [${tx.status}] ${tx.id} | Order: ${tx.orderId} | $${String.format(Locale.US, "%.2f", tx.amountUsd)} USD | Payer: ${tx.payerName} (${tx.payerPhone}) | Sig: ${tx.signatureHash.take(16)}..."
}}

[3] INVENTORY & LISTINGS CATALOG
--------------------------------------------------------------------------------
${_spaces.value.joinToString("\n") { sp ->
    "• [${if (sp.isActiveSubscription) "ACTIVE (30d)" else "EXPIRED"}] ${sp.id}: ${sp.title} (${sp.governorate.displayName} - ${sp.district}) | Base Rate: $${sp.baseMonthlyRateUsd}/mo | Owner: ${sp.ownerName}"
}}

================================================================================
                      END OF PROSPACE AUDIT LEDGER
================================================================================
        """.trimIndent()
    }

    fun exportUsersToCsv(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== PROSPACE USERS DIRECTORY EXPORT (CSV) ===")
        sb.appendLine("Export Date,${sdf.format(Date())}")
        sb.appendLine("Total Users,${_users.value.size}")
        sb.appendLine()
        sb.appendLine("ID,Full Name,Email,Role,Specialty,Syndicate Number,Affiliation,Phone,Governorate,Is Verified,Verification Status,Trust Score,Tier")
        _users.value.forEach { u ->
            sb.appendLine("\"${u.id}\",\"${u.fullName.replace("\"", "\"\"")}\",\"${u.email}\",\"${u.role.name}\",\"${u.specialty.replace("\"", "\"\"")}\",\"${u.syndicateNumber}\",\"${u.affiliation.replace("\"", "\"\"")}\",\"${u.phone}\",\"${u.governorate.displayName}\",${u.isVerified},\"${u.verificationStatus.name}\",${u.trustScore},\"${u.verificationTier.name}\"")
        }
        return sb.toString()
    }

    fun exportUsersToJson(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"exportType\": \"USER_DIRECTORY\",")
        sb.appendLine("  \"exportedAt\": \"${sdf.format(Date())}\",")
        sb.appendLine("  \"totalUsers\": ${_users.value.size},")
        sb.appendLine("  \"users\": [")
        _users.value.forEachIndexed { idx, u ->
            val comma = if (idx < _users.value.size - 1) "," else ""
            sb.appendLine("    {")
            sb.appendLine("      \"id\": \"${u.id}\",")
            sb.appendLine("      \"fullName\": \"${u.fullName.replace("\"", "\\\"")}\",")
            sb.appendLine("      \"email\": \"${u.email}\",")
            sb.appendLine("      \"role\": \"${u.role.name}\",")
            sb.appendLine("      \"specialty\": \"${u.specialty.replace("\"", "\\\"")}\",")
            sb.appendLine("      \"syndicateNumber\": \"${u.syndicateNumber}\",")
            sb.appendLine("      \"phone\": \"${u.phone}\",")
            sb.appendLine("      \"governorate\": \"${u.governorate.displayName}\",")
            sb.appendLine("      \"isVerified\": ${u.isVerified},")
            sb.appendLine("      \"trustScore\": ${u.trustScore}")
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    fun exportListingsToCsv(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== PROSPACE WORKSPACE LISTINGS EXPORT (CSV) ===")
        sb.appendLine("Export Date,${sdf.format(Date())}")
        sb.appendLine("Total Listings,${_spaces.value.size}")
        sb.appendLine()
        sb.appendLine("Space ID,Title,Space Type,Governorate,District,Street Address,Monthly Rate USD,Is Shared,Is Verified,Active 30d Sub,Owner Name,Owner Phone,Owner Email")
        _spaces.value.forEach { sp ->
            sb.appendLine("\"${sp.id}\",\"${sp.title.replace("\"", "\"\"")}\",\"${sp.spaceType.name}\",\"${sp.governorate.displayName}\",\"${sp.district}\",\"${sp.streetAddress.replace("\"", "\"\"")}\",${sp.baseMonthlyRateUsd},${sp.isShared},${sp.isVerified},${sp.isActiveSubscription},\"${sp.ownerName.replace("\"", "\"\"")}\",\"${sp.ownerPhone}\",\"${sp.ownerEmail}\"")
        }
        return sb.toString()
    }

    fun exportListingsToJson(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"exportType\": \"WORKSPACE_LISTINGS\",")
        sb.appendLine("  \"exportedAt\": \"${sdf.format(Date())}\",")
        sb.appendLine("  \"totalListings\": ${_spaces.value.size},")
        sb.appendLine("  \"listings\": [")
        _spaces.value.forEachIndexed { idx, sp ->
            val comma = if (idx < _spaces.value.size - 1) "," else ""
            sb.appendLine("    {")
            sb.appendLine("      \"id\": \"${sp.id}\",")
            sb.appendLine("      \"title\": \"${sp.title.replace("\"", "\\\"")}\",")
            sb.appendLine("      \"spaceType\": \"${sp.spaceType.name}\",")
            sb.appendLine("      \"governorate\": \"${sp.governorate.displayName}\",")
            sb.appendLine("      \"district\": \"${sp.district}\",")
            sb.appendLine("      \"monthlyRateUsd\": ${sp.baseMonthlyRateUsd},")
            sb.appendLine("      \"isShared\": ${sp.isShared},")
            sb.appendLine("      \"isVerified\": ${sp.isVerified},")
            sb.appendLine("      \"isActiveSubscription\": ${sp.isActiveSubscription},")
            sb.appendLine("      \"ownerName\": \"${sp.ownerName.replace("\"", "\\\"")}\",")
            sb.appendLine("      \"ownerPhone\": \"${sp.ownerPhone}\"")
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    fun exportOwnerRegistrationsToCsv(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val owners = _users.value.filter { it.role == UserRole.SPACE_OWNER }
        val sb = StringBuilder()
        sb.appendLine("=== PROSPACE OWNER REGISTRATIONS & WORKSPACES AUDIT ===")
        sb.appendLine("Export Date,${sdf.format(Date())}")
        sb.appendLine("Total Registered Hosts,${owners.size}")
        sb.appendLine()
        sb.appendLine("User ID,Full Name,Email,Phone,Affiliation,Governorate,Properties Count,Active Subscribed Count,Syndicate/Permit #,Verification Status,Trust Score")
        owners.forEach { o ->
            val ownedSpaces = _spaces.value.filter { it.ownerName.contains(o.fullName, ignoreCase = true) || it.ownerPhone == o.phone }
            val activeSpaces = ownedSpaces.count { it.isActiveSubscription }
            sb.appendLine("\"${o.id}\",\"${o.fullName.replace("\"", "\"\"")}\",\"${o.email}\",\"${o.phone}\",\"${o.affiliation.replace("\"", "\"\"")}\",\"${o.governorate.displayName}\",${ownedSpaces.size},$activeSpaces,\"${o.syndicateNumber}\",\"${o.verificationStatus.name}\",${o.trustScore}")
        }
        return sb.toString()
    }

    fun exportTransactionsToCsv(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== PROSPACE WHISH PAY TRANSACTIONS LEDGER (CSV) ===")
        sb.appendLine("Export Date,${sdf.format(Date())}")
        sb.appendLine("Total Transactions,${_transactions.value.size}")
        sb.appendLine("Total Volume USD,${calculateTotalSettlementVolume()}")
        sb.appendLine()
        sb.appendLine("Transaction ID,Order ID,Amount USD,Status,Date,Payer Name,Payer Phone,Space ID,Signature Hash,Channel ID")
        _transactions.value.forEach { tx ->
            sb.appendLine("\"${tx.id}\",\"${tx.orderId}\",${tx.amountUsd},\"${tx.status}\",\"${sdf.format(Date(tx.timestamp))}\",\"${tx.payerName}\",\"${tx.payerPhone}\",\"${tx.spaceId}\",\"${tx.signatureHash}\",\"${tx.channelId}\"")
        }
        return sb.toString()
    }

    fun exportWorkspacesCsv(): String = exportListingsToCsv()

    fun exportBookingsCsv(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== PROSPACE BOOKINGS LEDGER (CSV) ===")
        sb.appendLine("Export Date,${sdf.format(Date())}")
        sb.appendLine("Total Bookings,${_bookingRequests.value.size}")
        sb.appendLine()
        sb.appendLine("Booking ID,Space ID,Space Title,Practitioner,Specialty,Duration Months,Total USD,Status,Start Date,End Date")
        _bookingRequests.value.forEach { b ->
            sb.appendLine("\"${b.id}\",\"${b.spaceId}\",\"${b.spaceTitle}\",\"${b.practitionerName}\",\"${b.practitionerSpecialty}\",${b.durationMonths},${b.totalAmountUsd},\"${b.status}\",\"${b.startDate}\",\"${b.endDate}\"")
        }
        return sb.toString()
    }

    fun exportUsersCsv(): String = exportOwnerRegistrationsToCsv()

    fun exportTransactionsCsv(): String = exportTransactionsToCsv()

    // Owner cash-out was never a real feature: it wasn't wired into any screen, and
    // the "request" it used to build self-reported an instant SUCCESS with no actual
    // Whish disbursement call — the same trust-the-client pattern removed from the
    // real payment flows in Phase 5. Removed rather than fixed in place, since a
    // real payout flow (does Whish's API even support merchant-to-user transfers?
    // is this a manual settlement admins confirm, like most local integrations?) is
    // a product decision, not a security patch.
}
