package com.example.data.repository

import android.util.Log
import com.example.data.auth.FirebaseFunctionsClient
import com.example.data.crypto.WhishSecurity
import com.example.data.firestore.FirestoreSchema
import com.example.data.firestore.FirestoreService
import com.example.data.model.*
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

class ProHostRepository {

    companion object {
        private const val TAG = "ProHostRepository"

        // Must stay in sync with firestore.rules' user_profiles update rule's own
        // protected-fields list — every one of these is exclusively server-maintained
        // (assignInitialRole/grantAdminRole/setAccountSuspended/the Whish entitlement
        // grant). Used by registerMember to filter its write down to a safe subset —
        // see that function's own doc comment for the exact bug this prevents.
        private val PROTECTED_UPDATE_FIELDS = setOf(
            "role", "isVerified", "createdAtMillis", "lastSignInAtMillis", "isSuspended",
            "ownerPackageId", "ownerPackageExpiryMillis", "activeListingCount",
            "tosAcceptedAtMillis", "consentVersion"
        )

        @Volatile
        private var instance: ProHostRepository? = null

        fun getInstance(): ProHostRepository {
            return instance ?: synchronized(this) {
                val existing = instance
                if (existing != null) {
                    existing
                } else {
                    val newInstance = ProHostRepository()
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

    private val _bookingRequests = MutableStateFlow<List<RentalBookingRequest>>(emptyList())
    val bookingRequests: StateFlow<List<RentalBookingRequest>> = _bookingRequests.asStateFlow()

    // Flips true the moment the first real Firestore snapshot for bookings arrives
    // (see onBookingsUpdated below) — distinct from bookingRequests simply being
    // empty, which is indistinguishable from "still loading" without this. Screens
    // reading practitionerBookings/ownerIncomingRequests (MyBookingsScreen,
    // OwnerIncomingRequestsView, OwnerRentingProgressScreen) used to render their
    // "No bookings yet" empty state instantly on open, even for an account with real
    // bookings, for however long the first snapshot took to arrive.
    private val _hasLoadedBookingsOnce = MutableStateFlow(false)
    val hasLoadedBookingsOnce: StateFlow<Boolean> = _hasLoadedBookingsOnce.asStateFlow()

    private val _spaceArchitectureSchema = MutableStateFlow<SpaceArchitectureSchema>(createDefaultSchema())
    val spaceArchitectureSchema: StateFlow<SpaceArchitectureSchema> = _spaceArchitectureSchema.asStateFlow()

    // Admin-managed, purchasable Pro Host packages — replaces the old closed
    // OwnerPackageTier enum + PAYG credit system entirely. Starts EMPTY (not
    // seeded with fake local data): an earlier version of this seeded two
    // legacy-priced packages directly into this StateFlow so an existing Pro
    // Host's stored ownerPackageId would resolve before any admin ever opened
    // the Packages Configuration card — but that made those two fabricated
    // packages appear as real, purchasable admin-published products on a
    // fresh deploy (before package_plans/main even exists), AND got them
    // silently persisted to Firestore the moment an admin added their first
    // real package (addPackagePlan merges into whatever this StateFlow held).
    // The real fix for "resolve an already-migrated host's legacy package id"
    // is the one-time, admin-triggered functions/src/packages/
    // seedLegacyPackagePlans.ts Cloud Function, which writes the two legacy
    // packages into the real Firestore doc exactly once (idempotent) — see
    // AdminViewModel.runSeedLegacyPackagePlans(). This StateFlow only ever
    // reflects the real, live package_plans/main document.
    private val _packagePlans = MutableStateFlow(PackagePlanCatalog())
    val packagePlans: StateFlow<PackagePlanCatalog> = _packagePlans.asStateFlow()

    init {
        seedInitialData()
        startRealtimeSync()
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
                    // Keep the signed-in user's own session in sync with server-side role/
                    // tier/suspension changes in real time — an Admin action on ANOTHER
                    // device (grantAdminRole, revokeProHostRole, setAccountSuspended, a
                    // verification toggle) used to only ever reach _users, never
                    // _currentUser, so the affected user's own UI kept showing their old
                    // role/status until their next full sign-in. All of these fields are
                    // server-authoritative (Cloud-Function-only writes), so overwriting the
                    // local currentUser with the fresh synced copy is always safe.
                    val signedInId = _currentUser.value?.id
                    if (signedInId != null) {
                        updatedUsers.find { it.id == signedInId }?.let { fresh ->
                            _currentUser.value = fresh
                        }
                    }
                    _isCloudConnected.value = true
                },
                onBookingsUpdated = { updatedBookings ->
                    _bookingRequests.value = updatedBookings
                    _hasLoadedBookingsOnce.value = true
                    _isCloudConnected.value = true
                },
                onFormulasUpdated = { updatedFormulas ->
                    _subscriptionFormulas.value = updatedFormulas
                    _isCloudConnected.value = true
                },
                onTransactionsUpdated = { updatedTransactions ->
                    _transactions.value = updatedTransactions
                    _isCloudConnected.value = true
                },
                onSchemaUpdated = { updatedSchema ->
                    _spaceArchitectureSchema.value = updatedSchema
                    _isCloudConnected.value = true
                },
                onAuditLogsUpdated = { updatedLogs ->
                    _auditLogs.value = updatedLogs
                },
                onPackagePlansUpdated = { updatedCatalog ->
                    _packagePlans.value = updatedCatalog
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

    // --- Admin-Managed Package Plans ---
    // Mirrors the Dynamic Space Architecture Schema Management block below (add/
    // toggle/delete via a direct, admin-role-gated Firestore write — no dedicated
    // Cloud Function needed for basic CRUD, matching SchemaItem's own pattern).
    // The purchase-time price/validity lookup and expiry sweep remain genuine
    // Cloud-Function trust-boundary logic (initiateWhishPayment.ts/expirePackages.ts).

    suspend fun addPackagePlan(plan: PackagePlan): Boolean {
        val updated = _packagePlans.value.copy(packages = _packagePlans.value.packages + (plan.id to plan))
        val success = firestoreService.savePackagePlans(updated)
        if (success) {
            _packagePlans.value = updated
            addAuditLog(
                actionType = "PACKAGE_PLAN_ADDED",
                details = "Admin added package '${plan.name}' — $${String.format(Locale.US, "%.2f", plan.priceUsd)}, " +
                    "limit ${plan.listingLimit ?: "unlimited"}, ${plan.validityDays} days validity",
                severity = "SECURE"
            )
        }
        return success
    }

    suspend fun updatePackagePlan(plan: PackagePlan): Boolean {
        if (_packagePlans.value.packages[plan.id] == null) return false
        val updated = _packagePlans.value.copy(packages = _packagePlans.value.packages + (plan.id to plan))
        val success = firestoreService.savePackagePlans(updated)
        if (success) {
            _packagePlans.value = updated
            addAuditLog(
                actionType = "PACKAGE_PLAN_UPDATED",
                details = "Admin updated package '${plan.name}' (#${plan.id}) — $${String.format(Locale.US, "%.2f", plan.priceUsd)}, " +
                    "limit ${plan.listingLimit ?: "unlimited"}, ${plan.validityDays} days validity",
                severity = "SECURE"
            )
        }
        return success
    }

    suspend fun togglePackagePlan(planId: String): Boolean {
        val current = _packagePlans.value.packages[planId] ?: return false
        val updated = _packagePlans.value.copy(
            packages = _packagePlans.value.packages + (planId to current.copy(isEnabled = !current.isEnabled))
        )
        val success = firestoreService.savePackagePlans(updated)
        if (success) {
            _packagePlans.value = updated
            addAuditLog(
                actionType = "PACKAGE_PLAN_TOGGLED",
                details = "Admin toggled package '${current.name}' (#$planId) active status",
                severity = "INFO"
            )
        }
        return success
    }

    suspend fun deletePackagePlan(planId: String): Boolean {
        val existing = _packagePlans.value.packages[planId] ?: return false
        val success = firestoreService.deletePackagePlan(planId)
        if (success) {
            _packagePlans.value = _packagePlans.value.copy(packages = _packagePlans.value.packages - planId)
            addAuditLog(
                actionType = "PACKAGE_PLAN_DELETED",
                details = "Admin removed package '${existing.name}' (#$planId)",
                severity = "WARN"
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

    /** Every listing's owner pays their own package's flat price once, regardless of
     * how many listings they have — real revenue is now owner-level, not per-listing,
     * since PAYG's per-listing credit pricing is gone. distinctBy{ownerId} is what
     * makes this owner-level rather than listing-level; an owner with no active
     * package (ownerPackageId == null, or one that no longer resolves to a real
     * package_plans entry) contributes $0, which is correct. Naturally generalizes
     * over however many packages an admin has defined — no hardcoded tier count.
     * Shared by calculateActiveMrr (isActiveSubscription-filtered) and
     * calculatePotentialCapacityMrr (every listing, active or not) so the two can't
     * silently diverge in how they price a listing, only in which listings they include. */
    private fun sumListingRevenue(listings: List<SpaceListing>): Double {
        val usersById = _users.value.associateBy { it.id }
        val plansById = _packagePlans.value.packages
        return listings.distinctBy { it.ownerId }
            .sumOf { usersById[it.ownerId]?.ownerPackageId?.let { id -> plansById[id]?.priceUsd } ?: 0.0 }
    }

    fun calculateActiveMrr(): Double {
        return sumListingRevenue(_spaces.value.filter { it.isActiveSubscription })
    }

    fun calculatePotentialCapacityMrr(): Double {
        return sumListingRevenue(_spaces.value)
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

    /**
     * Exports the System Audit Logs panel's content (optionally date-range filtered) —
     * this is the same real, server-populated list the panel now shows live (see
     * FirestoreService.attachLiveListeners' audit-log listener), not just whatever this
     * device happened to add locally.
     */
    fun exportAuditLogsToCsv(startDateMillis: Long? = null, endDateMillis: Long? = null): String {
        val logs = _auditLogs.value.filter { log ->
            val matchesStart = startDateMillis == null || log.timestamp >= startDateMillis
            val matchesEnd = endDateMillis == null || log.timestamp <= endDateMillis
            matchesStart && matchesEnd
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== PROHOST SYSTEM AUDIT LOGS EXPORT (CSV) ===")
        sb.appendLine("Export Date,${sdf.format(Date())}")
        sb.appendLine("Range,${startDateMillis?.let { sdf.format(Date(it)) } ?: "Beginning"} to ${endDateMillis?.let { sdf.format(Date(it)) } ?: "Now"}")
        sb.appendLine("Total Entries,${logs.size}")
        sb.appendLine()
        sb.appendLine("Timestamp,Action Type,Severity,Actor Email,Details")
        logs.forEach { log ->
            val detailsEscaped = log.details.replace("\"", "\"\"")
            sb.appendLine("\"${sdf.format(Date(log.timestamp))}\",\"${log.actionType}\",\"${log.severity}\",\"${log.actorEmail}\",\"$detailsEscaped\"")
        }
        return sb.toString()
    }

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
    /**
     * This only ever mutated the in-memory _spaces list — it never wrote to
     * Firestore at all, so a freshly "published" listing lived purely in RAM and
     * was wiped the moment attachLiveListeners' workspace_listings snapshot next
     * replaced _spaces wholesale (see startRealtimeSync). That also silently broke
     * every follow-up edit: the schedule/blackout/formula saves all look the space
     * up in _spaces first and bail out when it isn't there. Now persisted first,
     * and local state only commits once the write is confirmed.
     */
    suspend fun addSpaceListing(listing: SpaceListing): Boolean {
        // Publishing a Draft reuses the same listingId (see CreateListingDialog's
        // buildListing), so this write may really be an update of an existing
        // document rather than a brand-new one — same protected-field re-copy as
        // updateSpaceListing, otherwise a freshly-constructed subscriptionExpiryMillis
        // (computed from System.currentTimeMillis() at build time, not preserved
        // across the wizard's steps) would diff against the stored value and get
        // the whole write denied by firestore.rules' protected-fields check.
        val current = _spaces.value.find { it.id == listing.id }
        val safeListing = if (current != null) {
            listing.copy(
                isVerified = current.isVerified,
                isActiveSubscription = current.isActiveSubscription,
                subscriptionExpiryMillis = current.subscriptionExpiryMillis,
                isOwnerSuspended = current.isOwnerSuspended,
                ownerIsIdVerified = current.ownerIsIdVerified
            )
        } else {
            listing
        }
        val success = firestoreService.saveWorkspace(safeListing)
        if (success) {
            // Upserts rather than always prepending — see the comment above on why
            // this id may already be in local state from saveListingDraft;
            // prepending unconditionally would leave two entries for the same
            // document instead of one updated one.
            _spaces.value = if (current != null) {
                _spaces.value.map { if (it.id == safeListing.id) safeListing else it }
            } else {
                listOf(safeListing) + _spaces.value
            }
            addAuditLog(
                actionType = "LISTING_CREATED",
                details = "New space created: ${safeListing.title} (${safeListing.district}) by ${safeListing.ownerName}",
                severity = "INFO"
            )
            // Recorded on publish, not on every draft save — a draft mid-typing
            // shouldn't inflate usage counts for tags the host might still change.
            if (safeListing.complementarySpecialties.isNotEmpty()) {
                coroutineScope.launch {
                    firestoreService.recordHashtagUsage(safeListing.complementarySpecialties, safeListing.governorate.name)
                }
            }
        }
        return success
    }

    /** Fetched once per call site (CreateListingDialog opening), not cached
     *  reactively — hashtag popularity changes slowly enough that a snapshot from
     *  whenever the wizard was opened is accurate enough for autosuggest. */
    suspend fun fetchTopHashtags(): List<String> = firestoreService.getTopHashtags()

    /** Full analytics rows for the Admin Console's hashtag usage view. */
    suspend fun fetchHashtagAnalytics(): List<HashtagUsageEntry> = firestoreService.getHashtagAnalytics()

    /**
     * Saves (or re-saves) a listing as a Draft — never gated by the host's active-
     * listing quota (see ProHostViewModel.createNewSpaceListing's quota check, which
     * this deliberately bypasses) since a Draft doesn't consume a slot until it's
     * actually published. Upserts on listingId so repeatedly saving the same draft
     * updates one document/local entry instead of creating duplicates. Preserves the
     * same protected fields as addSpaceListing/updateSpaceListing — see their doc
     * comments — since a re-save is really an update of the existing draft document.
     */
    suspend fun saveListingDraft(listing: SpaceListing): Boolean {
        val current = _spaces.value.find { it.id == listing.id }
        val draft = if (current != null) {
            listing.copy(
                status = ListingStatus.DRAFT,
                isVerified = current.isVerified,
                isActiveSubscription = current.isActiveSubscription,
                subscriptionExpiryMillis = current.subscriptionExpiryMillis,
                isOwnerSuspended = current.isOwnerSuspended,
                ownerIsIdVerified = current.ownerIsIdVerified
            )
        } else {
            listing.copy(status = ListingStatus.DRAFT)
        }
        val success = firestoreService.saveWorkspace(draft)
        if (success) {
            _spaces.value = if (current != null) {
                _spaces.value.map { if (it.id == draft.id) draft else it }
            } else {
                listOf(draft) + _spaces.value
            }
            addAuditLog(
                actionType = "LISTING_DRAFT_SAVED",
                details = "Draft saved: ${draft.title} (${draft.district}) by ${draft.ownerName}",
                severity = "INFO"
            )
        }
        return success
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
                subscriptionExpiryMillis = current.subscriptionExpiryMillis,
                ownerIsIdVerified = current.ownerIsIdVerified
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

    /**
     * This used to only mutate local state — the confirmation dialog claimed a
     * "permanent" removal, but the Firestore document was never touched, so the
     * listing simply reappeared the next time the live listener fired. Firestore
     * rules already permit an admin (or the owning user) to delete this document
     * directly (firestore.rules workspace_listings: allow delete), so no Cloud
     * Function is needed here.
     */
    suspend fun deleteSpaceListing(spaceId: String): Boolean {
        val target = _spaces.value.find { it.id == spaceId }
        val success = firestoreService.deleteWorkspace(spaceId)
        if (success) {
            _spaces.value = _spaces.value.filterNot { it.id == spaceId }
            addAuditLog(
                actionType = "LISTING_DELETED",
                details = "Admin permanently deleted workspace listing #${spaceId} (${target?.title ?: "Unknown"})",
                severity = "WARN"
            )
        }
        return success
    }

    /**
     * Generic profile edit (name, phone, specialty, country/governorate/city, etc.)
     * from the Admin Console's edit-user dialog. role/isVerified are always preserved
     * from the current stored value here, regardless of what's passed in: Firestore
     * rules deny any client write that changes those fields, so silently keeping them
     * unchanged avoids a write that would otherwise be denied outright (and this
     * method's caller showing a false "updated successfully" toast). Use
     * grantAdminRole for a role change instead.
     */
    /** Returns whether the write actually succeeded, so the caller can show a real result. */
    suspend fun updateUser(updated: AppUser): Boolean {
        val current = _users.value.find { it.id == updated.id }
        val safeUpdate = if (current != null) {
            updated.copy(
                role = current.role,
                isVerified = current.isVerified,
                isSuspended = current.isSuspended,
                ownerPackageId = current.ownerPackageId,
                ownerPackageExpiryMillis = current.ownerPackageExpiryMillis
            )
        } else {
            updated
        }
        // A targeted write of only these fields — role/isVerified/isSuspended/
        // ownerPackageId/etc. are Cloud-Function/Admin-SDK-only per firestore.rules'
        // user_profiles protected-fields list, and are never sent here at all (not
        // even echoed back unchanged) — echoing back the *locally cached* value used
        // to be this function's protection, but that cache can itself be stale
        // relative to the real server-stored value, which gets the entire write
        // rejected instead of just silently keeping the stale field as-is.
        val success = firestoreService.updateUserProfileFields(
            safeUpdate.id,
            mapOf(
                "fullName" to safeUpdate.fullName,
                "email" to safeUpdate.email,
                "specialty" to safeUpdate.specialty,
                "phone" to safeUpdate.phone,
                "country" to safeUpdate.country,
                "governorate" to safeUpdate.governorate,
                "city" to safeUpdate.city,
                "profilePictureUrl" to safeUpdate.profilePictureUrl,
                "idDocumentUrl" to safeUpdate.idDocumentUrl,
                "subscriptionExpiryMillis" to safeUpdate.subscriptionExpiryMillis
            )
        )
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

    /**
     * This used to only mutate local state — the confirmation dialog claimed a
     * "permanent" removal, but the Firestore document was never touched, so the
     * profile simply reappeared the next time the live listener fired. Firestore
     * rules already permit an admin to delete this document directly
     * (firestore.rules user_profiles: allow delete: if isAdmin()). Note this does
     * NOT revoke the user's Firebase Auth account or custom claim — only a Cloud
     * Function with the Admin SDK could do that; this removes their platform
     * profile record, which is what the confirmation dialog actually describes.
     */
    suspend fun deleteUser(userId: String): Boolean {
        val target = _users.value.find { it.id == userId }
        val success = firestoreService.deleteUserProfile(userId)
        if (success) {
            _users.value = _users.value.filterNot { it.id == userId }
            addAuditLog(
                actionType = "USER_DELETED",
                details = "Admin removed user profile #${userId} (${target?.fullName ?: "Unknown"})",
                severity = "WARN"
            )
        }
        return success
    }

    // --- Dynamic Space Architecture Schema Management ---
    // All four of these used to be 100% local — they never called Firestore at all,
    // despite AdminConsoleScreen's copy claiming edits went to a "database registry"/
    // "cloud" and the toasts implying a real save. Now they persist via
    // firestoreService.saveSchema (schema_architecture/main, admin-write-gated —
    // see firestore.rules), and only update local state once that write is confirmed.

    suspend fun addSchemaItem(item: SchemaItem): Boolean {
        val current = _spaceArchitectureSchema.value
        val updated = when (item.category) {
            "SPACE_TYPE" -> current.copy(spaceTypes = current.spaceTypes + item)
            "SUBCATEGORY" -> current.copy(subcategories = current.subcategories + item)
            "AMENITY" -> current.copy(amenities = current.amenities + item)
            "EQUIPMENT" -> current.copy(equipmentCategories = current.equipmentCategories + item)
            "RENTAL_STRATEGY" -> current.copy(rentalStrategies = current.rentalStrategies + item)
            else -> current
        }
        val success = firestoreService.saveSchema(updated)
        if (success) {
            _spaceArchitectureSchema.value = updated
            addAuditLog(
                actionType = "SCHEMA_ITEM_ADDED",
                details = "Admin added schema node '${item.name}' under category '${item.category}'",
                severity = "SECURE"
            )
        }
        return success
    }

    /** Edits maxSubdivisions on an EXISTING SchemaItem — previously only settable
     * once, at creation, via addSchemaItem. Only meaningful for category == "SPACE_TYPE"
     * (a no-op for any other category, same as the field's own doc comment on
     * SchemaItem). PAYG per-category pricing used to live alongside this same field
     * (SchemaItem.priceUsd) — removed with PAYG; package pricing now lives on
     * PackagePlan instead (see addPackagePlan/updatePackagePlan above). */
    suspend fun updateSchemaItemMaxSubdivisions(itemId: String, category: String, maxSubdivisions: Int?): Boolean {
        val current = _spaceArchitectureSchema.value
        fun updateList(items: List<SchemaItem>) =
            items.map { if (it.id == itemId) it.copy(maxSubdivisions = maxSubdivisions) else it }
        val updated = when (category) {
            "SPACE_TYPE" -> current.copy(spaceTypes = updateList(current.spaceTypes))
            else -> return false
        }
        val success = firestoreService.saveSchema(updated)
        if (success) {
            _spaceArchitectureSchema.value = updated
            addAuditLog(
                actionType = "SCHEMA_ITEM_PRICING_UPDATED",
                details = "Admin updated max-subdivisions for schema item #$itemId",
                severity = "SECURE"
            )
        }
        return success
    }

    suspend fun toggleSchemaItem(itemId: String): Boolean {
        val current = _spaceArchitectureSchema.value
        val updated = current.copy(
            spaceTypes = current.spaceTypes.map { if (it.id == itemId) it.copy(isEnabled = !it.isEnabled) else it },
            subcategories = current.subcategories.map { if (it.id == itemId) it.copy(isEnabled = !it.isEnabled) else it },
            amenities = current.amenities.map { if (it.id == itemId) it.copy(isEnabled = !it.isEnabled) else it },
            equipmentCategories = current.equipmentCategories.map { if (it.id == itemId) it.copy(isEnabled = !it.isEnabled) else it },
            rentalStrategies = current.rentalStrategies.map { if (it.id == itemId) it.copy(isEnabled = !it.isEnabled) else it }
        )
        val success = firestoreService.saveSchema(updated)
        if (success) {
            _spaceArchitectureSchema.value = updated
            addAuditLog(
                actionType = "SCHEMA_ITEM_TOGGLED",
                details = "Admin toggled schema item #$itemId active status",
                severity = "INFO"
            )
        }
        return success
    }

    suspend fun deleteSchemaItem(itemId: String): Boolean {
        val current = _spaceArchitectureSchema.value
        val updated = current.copy(
            spaceTypes = current.spaceTypes.filterNot { it.id == itemId },
            subcategories = current.subcategories.filterNot { it.id == itemId },
            amenities = current.amenities.filterNot { it.id == itemId },
            equipmentCategories = current.equipmentCategories.filterNot { it.id == itemId },
            rentalStrategies = current.rentalStrategies.filterNot { it.id == itemId }
        )
        val success = firestoreService.saveSchema(updated)
        if (success) {
            _spaceArchitectureSchema.value = updated
            addAuditLog(
                actionType = "SCHEMA_ITEM_DELETED",
                details = "Admin removed custom schema item #$itemId",
                severity = "WARN"
            )
        }
        return success
    }

    suspend fun resetSchemaToDefaults(): Boolean {
        val defaults = createDefaultSchema()
        val success = firestoreService.saveSchema(defaults)
        if (success) {
            _spaceArchitectureSchema.value = defaults
            addAuditLog(
                actionType = "SCHEMA_RESET_DEFAULTS",
                details = "Admin restored factory baseline schema definitions for Lebanese workspaces",
                severity = "SECURE"
            )
        }
        return success
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

    /**
     * Saves whichever verification document the host just uploaded — a normal
     * client write (verificationDocUrl/verificationDocType aren't rules-protected,
     * only isVerified itself is) — then immediately calls the Cloud Function that
     * actually earns the badge. Two steps because the function needs the document
     * URL already on the Firestore doc before it can check for it.
     */
    suspend fun requestOwnListingVerification(
        spaceId: String,
        docUrl: String,
        docType: ListingVerificationDocType
    ): Boolean {
        val target = _spaces.value.find { it.id == spaceId } ?: return false
        val withDoc = target.copy(verificationDocUrl = docUrl, verificationDocType = docType)
        if (!saveUpdatedSpace(withDoc)) return false

        val result = functionsClient.requestListingVerification(spaceId)
        if (result.isSuccess) {
            _spaces.value = _spaces.value.map {
                if (it.id == spaceId) it.copy(isVerified = true) else it
            }
            addAuditLog(
                actionType = "HOST_SELF_VERIFICATION",
                details = "Owner self-verified workspace #$spaceId ($docType document on file).",
                severity = "INFO"
            )
        }
        return result.isSuccess
    }

    /**
     * The host's own lifecycle control for a listing — Pause/Resume an Active
     * listing, or publish a Draft (Draft -> Active). Not Admin-only: `status`
     * isn't in firestore.rules' workspace_listings protected-fields list, so
     * a narrow merge write is enough; listingCountTracker.ts's update trigger
     * keeps activeListingCount in sync with the transition server-side.
     */
    suspend fun setListingStatus(spaceId: String, status: ListingStatus): Boolean {
        val success = firestoreService.updateWorkspaceListingFields(spaceId, mapOf("status" to status.name))
        if (success) {
            _spaces.value = _spaces.value.map {
                if (it.id == spaceId) it.copy(status = status) else it
            }
        }
        return success
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
    /**
     * Awaits the real Firestore write instead of firing it off in the
     * background — the caller (ProHostViewModel.submitBookingRequest) used to
     * show "Rental Request Sent!" the instant this returned, whatever the
     * actual sync outcome, since the write itself ran fire-and-forget via
     * syncNewBookingToFirestore. A specialist on a bad connection saw
     * confirmed success for a request that never reached Firestore — and
     * therefore never reached the host — with nothing telling them to retry.
     * The request is still added to local state optimistically (so it shows
     * up immediately in "My Bookings" even mid-sync), but [synced] in the
     * returned pair tells the caller whether that actually landed, so it can
     * show a truthful toast instead of an unconditional one.
     */
    suspend fun createBookingRequest(
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
        replacesBookingId: String? = null
    ): Pair<RentalBookingRequest, Boolean> {
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
            subdivisionId = subdivisionId,
            subdivisionName = subdivisionName,
            replacesBookingId = replacesBookingId
        )

        _bookingRequests.value = listOf(request) + _bookingRequests.value
        val synced = firestoreService.saveBookingRequest(request)
        if (synced) {
            _isOfflineMode.value = false
            _syncStatusMessage.value = "Booking Synced with Firebase Cloud"
        } else {
            _isOfflineMode.value = true
            _syncStatusMessage.value = "Offline: Booking Stored in Local Cache"
        }

        addAuditLog(
            actionType = if (replacesBookingId != null) "RENTAL_REQUEST_EDIT_SUBMITTED" else "RENTAL_REQUEST_SUBMITTED",
            details = if (replacesBookingId != null) {
                "Edit request $requestId sent by ${practitioner.fullName} for '${space.title}', proposing to replace accepted booking #$replacesBookingId. New slot: $rangeString. Awaiting owner approval." +
                    (if (!synced) " [NOT YET SYNCED TO CLOUD]" else "")
            } else {
                "Request $requestId sent by ${practitioner.fullName} for '${space.title}' (${formula.type.displayName}, $${totalUsd.toInt()} USD). Selected Slot: $rangeString. Awaiting owner WhatsApp/In-app approval." +
                    (if (!synced) " [NOT YET SYNCED TO CLOUD]" else "")
            },
            severity = if (synced) "INFO" else "WARN",
            actorEmail = practitioner.email
        )

        return request to synced
    }

    /**
     * The already-ACCEPTED booking that [requestId] would collide with if accepted
     * now, or null when it's clear. Checked by the ViewModel before the agreement
     * upload (so a host isn't asked to upload a lease for a booking that can't be
     * accepted) and again inside [acceptBookingRequest] as the real guard.
     */
    fun findAcceptConflict(requestId: String): RentalBookingRequest? {
        val request = _bookingRequests.value.find { it.id == requestId } ?: return null
        return com.example.ui.util.SpaceCalculationUtils.findAcceptConflict(request, _bookingRequests.value)
    }

    /**
     * Owner accepting a booking means they've reached and evidenced a real agreement
     * with the specialist — [agreementUrl] is the signed lease they just uploaded to
     * Storage (see OwnerRentalRequestsScreen's Accept flow), kept on file as the
     * record of that, exactly like [SpaceListing.ownershipProofUrl]: self-attested,
     * never reviewed. There is no in-app payment settlement to track anymore — both
     * sides handle payment outside the app entirely (the old isExternalPaymentSettled
     * flag, and the "Pay Whish" flow that set it, are gone).
     *
     * If [request.replacesBookingId] is set, this acceptance is really an edit
     * superseding a previously accepted booking (see MyBookingsScreen's "Edit
     * Booking" action) — the superseded booking is released (marked CANCELLED) in
     * the same operation, so exactly one of the two is ever ACCEPTED and
     * availability — always derived live from ACCEPTED bookings + the space's
     * schedule, never a separately stored count — recalculates immediately.
     *
     * Never double-books: refuses (returns false) when [findAcceptConflict] finds an
     * ACCEPTED booking already holding the same room/space, day and hours.
     */
    suspend fun acceptBookingRequest(requestId: String, agreementUrl: String): Boolean {
        val request = _bookingRequests.value.find { it.id == requestId } ?: return false
        // Never double-book: two ACCEPTED bookings can't overlap on the same room/space,
        // day and hours (SpaceCalculationUtils.findAcceptConflict — the same rule the
        // specialist-facing screens hide locked slots with).
        if (findAcceptConflict(requestId) != null) return false
        val now = System.currentTimeMillis()

        val success = firestoreService.updateBookingStatus(
            requestId,
            BookingRequestStatus.ACCEPTED,
            extraFields = mapOf("agreementUrl" to agreementUrl)
        )
        if (!success) return false

        _bookingRequests.value = _bookingRequests.value.map {
            if (it.id == requestId) {
                it.copy(status = BookingRequestStatus.ACCEPTED, reviewedAt = now, agreementUrl = agreementUrl)
            } else it
        }

        // Add member to resident list if not present
        val memberString = "${request.practitionerName} (${request.practitionerSpecialty})"
        _spaces.value = _spaces.value.map { space ->
            if (space.id == request.spaceId && !space.residentPractitioners.contains(memberString)) {
                space.copy(residentPractitioners = space.residentPractitioners + memberString)
            } else space
        }

        // Release the booking this edit replaces, if any — see the doc comment above.
        request.replacesBookingId?.let { oldId ->
            val oldRequest = _bookingRequests.value.find { it.id == oldId }
            if (oldRequest != null && oldRequest.status == BookingRequestStatus.ACCEPTED) {
                val supersededReason = "Superseded by an accepted edit (Ref #$requestId)"
                firestoreService.updateBookingStatus(oldId, BookingRequestStatus.CANCELLED, rejectionReason = supersededReason)
                _bookingRequests.value = _bookingRequests.value.map {
                    if (it.id == oldId) it.copy(status = BookingRequestStatus.CANCELLED, rejectionReason = supersededReason) else it
                }
            }
        }

        addAuditLog(
            actionType = "RENTAL_REQUEST_ACCEPTED",
            details = "Owner ${request.ownerName} accepted $requestId by ${request.practitionerName}. Formula '${request.formula.scheduleDescription}' (${request.selectedDateTimeRange}) is now locked and marked unavailable for public display." +
                (request.replacesBookingId?.let { " Replaces booking #$it, now released." } ?: ""),
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

        addAuditLog(
            actionType = "RENTAL_REQUEST_CANCELLED",
            details = "Request $requestId for '${request.spaceTitle}' cancelled by practitioner ${request.practitionerName} before host review.",
            severity = "INFO",
            actorEmail = request.practitionerEmail
        )

        return true
    }

    /**
     * Early termination of an already-ACCEPTED booking — the gap flagged as a genuine
     * open item: previously the only cancellation path was for a not-yet-accepted
     * PENDING request (cancelBookingRequest above). Deliberately simple per the
     * current terms-of-use cancellation workflow: a reason code plus an optional
     * note, no refund/penalty logic (there's nothing to refund — rent settlement
     * never happens in-app). [cancelledByUid]/[cancelledByRole] identify who ended
     * it (only the booking's own practitioner, its owner, or an Admin may call this
     * — enforced by the caller checking against the loaded [BookingRequest] before
     * invoking it, same pattern as reject/accept). A real cross-device push notifies
     * whichever side didn't initiate the cancellation (onBookingRequestStatusChanged,
     * functions/src/notifications/bookingNotifications.ts).
     */
    suspend fun cancelAcceptedBooking(
        requestId: String,
        reasonCode: CancellationReasonCode,
        note: String?,
        cancelledByUid: String,
        cancelledByRole: String
    ): Boolean {
        val request = _bookingRequests.value.find { it.id == requestId } ?: return false
        if (request.status != BookingRequestStatus.ACCEPTED) return false

        val success = firestoreService.updateBookingStatus(
            requestId,
            BookingRequestStatus.CANCELLED,
            extraFields = mapOf(
                "cancellationReasonCode" to reasonCode.name,
                "cancellationNote" to note,
                "cancelledByRole" to cancelledByRole
            )
        )
        if (!success) return false

        _bookingRequests.value = _bookingRequests.value.map {
            if (it.id == requestId) {
                it.copy(
                    status = BookingRequestStatus.CANCELLED,
                    cancellationReasonCode = reasonCode.name,
                    cancellationNote = note,
                    cancelledByRole = cancelledByRole
                )
            } else it
        }

        addAuditLog(
            actionType = "ACCEPTED_BOOKING_CANCELLED",
            details = "Accepted booking $requestId for '${request.spaceTitle}' (${request.practitionerName} / ${request.ownerName}) terminated early by $cancelledByRole. Reason: ${reasonCode.displayName}${if (!note.isNullOrBlank()) " — \"$note\"" else ""}.",
            severity = "WARN",
            actorEmail = if (cancelledByUid == request.practitionerId) request.practitionerEmail else request.ownerName
        )

        return true
    }

    /**
     * Mutual, independent "Mark as Paid" acknowledgment — record-keeping only, since
     * rent settlement happens entirely outside the app. Each side can only ever set
     * their own flag (the caller decides which); this never touches the other side's.
     */
    suspend fun acknowledgePayment(requestId: String, asHost: Boolean): Boolean {
        val field = if (asHost) "paymentAcknowledgedByHost" else "paymentAcknowledgedBySpecialist"
        val success = firestoreService.updateBookingFields(requestId, mapOf(field to true))
        if (success) {
            _bookingRequests.value = _bookingRequests.value.map {
                if (it.id == requestId) {
                    if (asHost) it.copy(paymentAcknowledgedByHost = true) else it.copy(paymentAcknowledgedBySpecialist = true)
                } else it
            }
        }
        return success
    }

    // --- Schedule & Blackout Slots Management ---
    // All five of these used to only mutate the in-memory _spaces StateFlow — never
    // calling Firestore at all. That's worse than just "no error handling": since
    // attachLiveListeners' workspace_listings snapshot listener periodically overwrites
    // _spaces wholesale, a schedule/formula change that never reached Firestore could
    // be silently reverted on the SAME device the next time any unrelated write
    // triggered a snapshot, on top of never syncing to any other device at all. Now
    // persisted via the same firestoreService.saveWorkspace path updateSpaceListing
    // already uses, only committing local state once the write is confirmed.

    /**
     * Same guard updateSpaceListing applies: always write back the CURRENTLY STORED
     * ownerId/isVerified/isActiveSubscription/subscriptionExpiryMillis rather than
     * whatever the local copy holds. firestore.rules rejects any workspace_listings
     * update whose affectedKeys() touches those, and fromFirestoreMap regenerates a
     * fresh subscriptionExpiryMillis (now + 30 days) whenever the field is missing
     * from the document — so echoing the local value back could change that key and
     * get the whole write denied, permanently, for that listing. Schedule, blackout
     * and formula saves all funnel through here and never had this protection.
     */
    private suspend fun saveUpdatedSpace(updated: SpaceListing): Boolean {
        val current = _spaces.value.find { it.id == updated.id }
        val safeUpdate = if (current != null) {
            updated.copy(
                ownerId = current.ownerId,
                isVerified = current.isVerified,
                isActiveSubscription = current.isActiveSubscription,
                subscriptionExpiryMillis = current.subscriptionExpiryMillis,
                ownerIsIdVerified = current.ownerIsIdVerified
            )
        } else {
            updated
        }
        val success = firestoreService.saveWorkspace(safeUpdate)
        if (success) {
            _spaces.value = _spaces.value.map { if (it.id == safeUpdate.id) safeUpdate else it }
        }
        return success
    }

    suspend fun addBlackoutSlot(spaceId: String, slot: BlackoutSlot): Boolean {
        val space = _spaces.value.find { it.id == spaceId } ?: return false
        val updated = space.copy(schedule = space.schedule.copy(blackoutSlots = space.schedule.blackoutSlots + slot))
        val success = saveUpdatedSpace(updated)
        if (success) {
            addAuditLog(
                actionType = "SCHEDULE_BLACKOUT_ADDED",
                details = "Owner added non-operating blackout slot (${slot.dayOfWeek} ${slot.startTime}-${slot.endTime}) to space $spaceId",
                severity = "INFO"
            )
        }
        return success
    }

    suspend fun removeBlackoutSlot(spaceId: String, slotId: String): Boolean {
        val space = _spaces.value.find { it.id == spaceId } ?: return false
        val updated = space.copy(schedule = space.schedule.copy(blackoutSlots = space.schedule.blackoutSlots.filter { it.id != slotId }))
        return saveUpdatedSpace(updated)
    }

    suspend fun updateSpaceSchedule(spaceId: String, schedule: SpaceOperatingSchedule): Boolean {
        val space = _spaces.value.find { it.id == spaceId } ?: return false
        val updated = space.copy(schedule = schedule)
        val success = saveUpdatedSpace(updated)
        if (success) {
            addAuditLog(
                actionType = "OPERATING_SCHEDULE_UPDATED",
                details = "Operating hours updated for space $spaceId: ${schedule.openingHour} - ${schedule.closingHour} (${schedule.operatingDays.joinToString()})",
                severity = "INFO"
            )
        }
        return success
    }

    suspend fun addRentalFormula(spaceId: String, formula: RentalFormula): Boolean {
        val space = _spaces.value.find { it.id == spaceId } ?: return false
        val newFormulas = space.rentalFormulas + formula
        // pricing must be kept in sync with rentalFormulas at every write site, not
        // only derived once on a fresh Firestore read — once this document has ever
        // been saved with a "pricing" key at all, fromFirestoreMap's legacy fallback
        // never runs again for it, so a write that touches rentalFormulas without
        // also updating pricing would silently leave pricing stale forever.
        val updated = space.copy(
            rentalFormulas = newFormulas,
            pricing = if (space.subdivisions.isEmpty()) RentalPricingConfig.fromLegacyFormula(newFormulas.firstOrNull()) else space.pricing
        )
        val success = saveUpdatedSpace(updated)
        if (success) {
            addAuditLog(
                actionType = "RENTAL_FORMULA_ADDED",
                details = "Added formula '${formula.type.displayName}' ($${formula.rateUsd}) to space $spaceId",
                severity = "INFO"
            )
        }
        return success
    }

    suspend fun deleteRentalFormula(spaceId: String, formulaId: String): Boolean {
        val space = _spaces.value.find { it.id == spaceId } ?: return false
        val newFormulas = space.rentalFormulas.filter { it.id != formulaId }
        val updated = space.copy(
            rentalFormulas = newFormulas,
            pricing = if (space.subdivisions.isEmpty()) RentalPricingConfig.fromLegacyFormula(newFormulas.firstOrNull()) else space.pricing
        )
        return saveUpdatedSpace(updated)
    }

    /**
     * Rooms/desks were previously only editable at listing-creation time
     * (CreateListingDialog's Step 2) — a host who published first and only later
     * realized they needed another room, or wanted to remove one, had no in-app way
     * to do it. These three funnel through the same saveUpdatedSpace protected-field
     * guard as the blackout/formula functions above; subdivisions live nested inside
     * the same workspace_listings document, so no firestore.rules change is needed.
     */
    suspend fun addSubdivision(spaceId: String, subdivision: Subdivision): Boolean {
        val space = _spaces.value.find { it.id == spaceId } ?: return false
        val updated = space.copy(subdivisions = space.subdivisions + subdivision)
        val success = saveUpdatedSpace(updated)
        if (success) {
            addAuditLog(
                actionType = "SUBDIVISION_ADDED",
                details = "Added room/desk '${subdivision.name}' (${subdivision.type.displayName}) to space $spaceId",
                severity = "INFO"
            )
        }
        return success
    }

    suspend fun removeSubdivision(spaceId: String, subdivisionId: String): Boolean {
        val space = _spaces.value.find { it.id == spaceId } ?: return false
        val updated = space.copy(subdivisions = space.subdivisions.filter { it.id != subdivisionId })
        val success = saveUpdatedSpace(updated)
        if (success) {
            addAuditLog(
                actionType = "SUBDIVISION_REMOVED",
                details = "Removed room/desk #$subdivisionId from space $spaceId",
                severity = "INFO"
            )
        }
        return success
    }

    // Best-effort, fire-and-forget engagement counters (real "Views"/"Inquiries" data,
    // replacing the old fixed 850/14 placeholders). Not offline-queued — this is a
    // low-stakes analytics counter, not a transaction, so silently no-op-ing while
    // offline is an acceptable tradeoff. The live workspace_listings snapshot
    // listener picks up the new value and refreshes _spaces automatically, so no
    // manual local-state patch is needed here. Mirrors addAuditLog's coroutineScope
    // fire-and-forget pattern rather than being a suspend fun.
    fun incrementSpaceViewCount(spaceId: String) {
        coroutineScope.launch { firestoreService.incrementSpaceCounter(spaceId, "avatarEngagementViews") }
    }

    fun incrementSpaceInquiryCount(spaceId: String) {
        coroutineScope.launch { firestoreService.incrementSpaceCounter(spaceId, "avatarInquiryClicks") }
    }

    // --- User Authentication & Member Registration ---
    /**
     * Registers a new member. [uid] must be the real Firebase Auth UID (so this user's
     * `id` lines up with the `user_profiles/{uid}` document the role-claim Cloud Functions
     * write to) and [verifiedRole] must already have been confirmed server-side — see
     * [com.example.data.auth.completeVerifiedRegistration]. [isVerified] reflects that
     * [uid]'s Firebase Auth account already completed phone-number SMS verification
     * before this is ever called (see LoginAuthScreen's OTP flow) — there is no admin
     * accreditation step anymore; [idDocumentUrl] is kept on file, not reviewed.
     *
     * Suspend, and its Firestore write is awaited and checked, because of a real bug this
     * fixes: assignInitialRole.ts's Admin-SDK write always lands (and creates the
     * user_profiles/{uid} document) before this function's own write does, so by the time
     * this write reaches Firestore it's evaluated as an UPDATE, not a create — and
     * AppUser.toFirestoreMap() unconditionally includes ownerPackageId/
     * ownerPackageExpiryMillis, both on the update rule's protected-fields list (see
     * PROTECTED_UPDATE_FIELDS below), so the whole write used to be silently rejected
     * with permission-denied. The caller (completeVerifiedRegistration) is already a
     * suspend fun with exactly one call site, so making this suspend too costs nothing.
     */
    suspend fun registerMember(
        uid: String,
        fullName: String,
        email: String,
        phone: String,
        verifiedRole: UserRole,
        specialty: String,
        profilePictureUrl: String?,
        idDocumentUrl: String?,
        country: String,
        governorate: String,
        city: String
    ): AppUser {
        val cleanEmail = email.trim().lowercase()
        val newUser = AppUser(
            id = uid,
            email = cleanEmail,
            fullName = fullName.trim(),
            role = verifiedRole,
            specialty = specialty.trim(),
            phone = phone.trim(),
            profilePictureUrl = profilePictureUrl,
            idDocumentUrl = idDocumentUrl,
            country = country.trim(),
            governorate = governorate.trim(),
            city = city.trim(),
            isVerified = true
        )

        // A targeted merge of only the fields this registration actually owns — never the
        // fields firestore.rules' user_profiles update rule protects (role/isVerified/
        // ownerPackageId/etc. — assignInitialRole.ts already correctly set/defaulted all
        // of those). Filtering toFirestoreMap() by this list, rather than hand-listing the
        // "safe" fields, means a future field added to toFirestoreMap() is safe-by-default
        // unless it's also added here. Keep this in sync with firestore.rules' own list.
        val safeFields = newUser.toFirestoreMap().filterKeys { it !in PROTECTED_UPDATE_FIELDS }
        val saved = firestoreService.updateUserProfileFields(uid, safeFields)
        if (!saved) {
            throw IllegalStateException("We couldn't save your profile. Please check your connection and try again.")
        }

        _users.value = _users.value.filterNot { it.id == uid } + newUser
        _currentUser.value = newUser

        addAuditLog(
            actionType = "MEMBER_REGISTRATION",
            details = "New member registered: ${newUser.fullName} (${newUser.role.name}) • ${newUser.specialty} • ${newUser.city}, ${newUser.governorate}, ${newUser.country}",
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
     * [email] may be blank (a phone-auth FirebaseUser has no email of its own) — a blank
     * value never overwrites a previously-stored real email.
     */
    fun login(uid: String, email: String, verifiedRole: UserRole): AppUser {
        val cleanEmail = email.trim().lowercase()
        val existing = _users.value.find { it.id == uid }

        val user = existing?.copy(role = verifiedRole, email = cleanEmail.ifBlank { existing.email }) ?: AppUser(
            id = uid,
            email = cleanEmail,
            fullName = if (cleanEmail.contains("@")) cleanEmail.substringBefore("@").replace(".", " ").capitalize(Locale.US) else "Member",
            role = verifiedRole,
            specialty = "",
            phone = "",
            country = "Lebanon",
            governorate = "",
            city = "",
            isVerified = true
        )

        _users.value = _users.value.filterNot { it.id == uid } + user
        _currentUser.value = user
        // assignInitialRole.ts already runs (Admin SDK, bypassing rules) before this
        // is ever called — see completeVerifiedLogin — and keeps role/isVerified/
        // createdAtMillis/lastSignInAtMillis correctly in sync server-side on every
        // sign-in. This client-side write exists only to fix up email, and only ever
        // as a targeted field: writing this function's other, locally-fabricated
        // defaults (specialty="", phone="", country="Lebanon", ...) for a user whose
        // real profile just hasn't synced to this device yet would clobber their real
        // stored values, and echoing role/isVerified back risks disagreeing with the
        // real server-stored value and getting the whole write rejected.
        if (cleanEmail.isNotBlank()) {
            coroutineScope.launch { firestoreService.updateUserProfileFields(uid, mapOf("email" to cleanEmail)) }
        }

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
    // a Firebase Auth custom claim, through the grantAdminRole Cloud Function for an
    // explicit Admin grant, or through grantEntitlement() promoting a SPECIALIST to
    // PRO_HOST the moment their package/listing Whish payment settles.

    suspend fun updateCurrentUserProfile(
        name: String,
        specialty: String,
        phone: String,
        country: String,
        governorate: String,
        city: String,
        profilePictureUrl: String? = null
    ): Boolean {
        val current = _currentUser.value ?: return false
        val updated = current.copy(
            fullName = name,
            specialty = specialty,
            phone = phone,
            country = country,
            governorate = governorate,
            city = city,
            profilePictureUrl = profilePictureUrl ?: current.profilePictureUrl
        )
        // A targeted write of only these fields — never role/isVerified/isSuspended/
        // ownerPackageId/etc. Echoing those back from the locally-cached AppUser
        // (the old approach) could disagree with the real server-stored value (e.g.
        // right after a role grant the local cache hasn't refreshed yet) and get the
        // *entire* write rejected by firestore.rules' protected-fields check, even
        // though the caller only meant to change their name.
        val success = firestoreService.updateUserProfileFields(
            current.id,
            mapOf(
                "fullName" to updated.fullName,
                "specialty" to updated.specialty,
                "phone" to updated.phone,
                "country" to updated.country,
                "governorate" to updated.governorate,
                "city" to updated.city,
                "profilePictureUrl" to updated.profilePictureUrl
            )
        )
        if (success) {
            _currentUser.value = updated
            _users.value = _users.value.map { if (it.id == updated.id) updated else it }
        }
        return success
    }

    /**
     * Sets/replaces the signed-in user's own ID document (Storage upload already
     * done by the caller — this just records the resulting URL). idDocumentUrl was
     * previously only ever written once, by the registration Cloud Function
     * (assignInitialRole.ts) — any account that never went through registration
     * (most notably an Admin created via bootstrapSuperAdmin/grantAdminRole, which
     * only ever set role) had this field permanently null with no way to ever set
     * it. firestore.rules' user_profiles update rule already permits a self-write
     * to this field (it's not in the protected-fields list — unlike role/
     * isVerified/etc., ID-document-on-file isn't an entitlement or a
     * server-computed fact), so this is a direct client write, the same pattern
     * updateCurrentUserProfile uses for its own targeted fields.
     */
    suspend fun updateIdDocument(idDocumentUrl: String): Boolean {
        val current = _currentUser.value ?: return false
        val success = firestoreService.updateUserProfileFields(
            current.id,
            mapOf("idDocumentUrl" to idDocumentUrl)
        )
        if (success) {
            val updated = current.copy(idDocumentUrl = idDocumentUrl)
            _currentUser.value = updated
            _users.value = _users.value.map { if (it.id == updated.id) updated else it }
        }
        return success
    }

    /**
     * Toggles [spaceId] in the current user's personal saved/favorites list. Not a
     * protected field — any signed-in user may freely write their own savedSpaceIds,
     * so a plain merge write of the recomputed list is enough (no rules change needed).
     */
    suspend fun toggleSavedSpace(spaceId: String): Boolean {
        val current = _currentUser.value ?: return false
        val updatedIds = if (current.savedSpaceIds.contains(spaceId)) {
            current.savedSpaceIds - spaceId
        } else {
            current.savedSpaceIds + spaceId
        }
        val updated = current.copy(savedSpaceIds = updatedIds)
        val success = firestoreService.updateUserProfileFields(
            current.id,
            mapOf("savedSpaceIds" to updatedIds)
        )
        if (success) {
            _currentUser.value = updated
            _users.value = _users.value.map { if (it.id == updated.id) updated else it }
        }
        return success
    }

    /** Registers this device's FCM token against [uid]'s profile — see FirestoreService.saveFcmToken. */
    suspend fun registerFcmToken(uid: String, token: String): Boolean = firestoreService.saveFcmToken(uid, token)

    // --- Multi-Format Data Export Hub ---
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
                       PROHOST LEBANON AUDIT & REVENUE REPORT
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
                      END OF PROHOST AUDIT LEDGER
================================================================================
        """.trimIndent()
    }

    /** Millis -> "yyyy-MM-dd HH:mm:ss", or "—" when the server hasn't stamped this account yet. */
    private fun formatAuditTimestamp(sdf: SimpleDateFormat, millis: Long?): String =
        millis?.let { sdf.format(Date(it)) } ?: "—"

    /**
     * The full audit-trail export Admin's Users Directory offers: every field a real
     * platform operator needs to see per account, including the server-stamped
     * creation/last-sign-in timestamps (assignInitialRole.ts — never client-set, see
     * AppUser's doc comment) and direct links to the on-file profile picture and ID
     * document. Nothing here is reviewed/approved — these are exactly the fields kept
     * on file, exported as-is.
     */
    fun exportUsersToCsv(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== PROHOST USERS DIRECTORY EXPORT (CSV) ===")
        sb.appendLine("Export Date,${sdf.format(Date())}")
        sb.appendLine("Total Users,${_users.value.size}")
        sb.appendLine()
        sb.appendLine("User ID,Full Name,Email,Role,Specialty,Phone,Country,Governorate,City,Is Verified,Account Created,Last Sign-In,Profile Picture URL,ID Document URL")
        _users.value.forEach { u ->
            sb.appendLine(
                "\"${u.id}\",\"${u.fullName.replace("\"", "\"\"")}\",\"${u.email}\",\"${u.role.name}\"," +
                    "\"${u.specialty.replace("\"", "\"\"")}\",\"${u.phone}\",\"${u.country}\",\"${u.governorate}\",\"${u.city}\"," +
                    "${u.isVerified},\"${formatAuditTimestamp(sdf, u.createdAtMillis)}\",\"${formatAuditTimestamp(sdf, u.lastSignInAtMillis)}\"," +
                    "\"${u.profilePictureUrl ?: ""}\",\"${u.idDocumentUrl ?: ""}\""
            )
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
            sb.appendLine("      \"phone\": \"${u.phone}\",")
            sb.appendLine("      \"country\": \"${u.country}\",")
            sb.appendLine("      \"governorate\": \"${u.governorate}\",")
            sb.appendLine("      \"city\": \"${u.city}\",")
            sb.appendLine("      \"isVerified\": ${u.isVerified},")
            sb.appendLine("      \"createdAtMillis\": ${u.createdAtMillis ?: "null"},")
            sb.appendLine("      \"lastSignInAtMillis\": ${u.lastSignInAtMillis ?: "null"},")
            sb.appendLine("      \"profilePictureUrl\": ${u.profilePictureUrl?.let { "\"$it\"" } ?: "null"},")
            sb.appendLine("      \"idDocumentUrl\": ${u.idDocumentUrl?.let { "\"$it\"" } ?: "null"}")
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    fun exportListingsToCsv(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== PROHOST WORKSPACE LISTINGS EXPORT (CSV) ===")
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
        val owners = _users.value.filter { it.role == UserRole.PRO_HOST }
        val sb = StringBuilder()
        sb.appendLine("=== PROHOST OWNER REGISTRATIONS & WORKSPACES AUDIT ===")
        sb.appendLine("Export Date,${sdf.format(Date())}")
        sb.appendLine("Total Registered Hosts,${owners.size}")
        sb.appendLine()
        sb.appendLine("User ID,Full Name,Email,Phone,Country,Governorate,City,Properties Count,Active Subscribed Count,Is Verified")
        owners.forEach { o ->
            val ownedSpaces = _spaces.value.filter { it.ownerName.contains(o.fullName, ignoreCase = true) || it.ownerPhone == o.phone }
            val activeSpaces = ownedSpaces.count { it.isActiveSubscription }
            sb.appendLine("\"${o.id}\",\"${o.fullName.replace("\"", "\"\"")}\",\"${o.email}\",\"${o.phone}\",\"${o.country}\",\"${o.governorate}\",\"${o.city}\",${ownedSpaces.size},$activeSpaces,${o.isVerified}")
        }
        return sb.toString()
    }

    fun exportTransactionsToCsv(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== PROHOST WHISH PAY TRANSACTIONS LEDGER (CSV) ===")
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
        sb.appendLine("=== PROHOST BOOKINGS LEDGER (CSV) ===")
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
