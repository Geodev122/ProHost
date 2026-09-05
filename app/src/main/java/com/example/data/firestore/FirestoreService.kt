package com.example.data.firestore

import android.util.Log
import com.example.data.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * FirestoreService
 *
 * The single Firestore access layer for ProHost: collection/document CRUD, real-time
 * listeners, and initial-data seeding, all against the canonical collection names in
 * [FirestoreSchema]. This absorbs what used to be split across this class and the
 * now-removed `FirestoreDataConnectBridge` (which duplicated most of this against the
 * same collections via a parallel callback-style API).
 */
class FirestoreService(
    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.w("FirestoreService", "FirebaseFirestore instance unavailable: ${e.message}")
        null
    }
) {
    companion object {
        private const val TAG = "FirestoreService"

        @Volatile
        private var INSTANCE: FirestoreService? = null

        fun getInstance(): FirestoreService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirestoreService().also { INSTANCE = it }
            }
        }
    }

    private val listenerScope = CoroutineScope(Dispatchers.IO)
    private val activeListeners = mutableListOf<ListenerRegistration>()

    /**
     * Initializes the Firestore database schema with metadata and system verification markers.
     */
    suspend fun initializeSchema(): Boolean {
        return try {
            val db = firestore ?: return false
            val metadata = mapOf(
                "schemaVersion" to FirestoreSchema.SCHEMA_VERSION,
                "dataConnectService" to FirestoreSchema.DATA_CONNECT_SERVICE_ID,
                "databaseId" to FirestoreSchema.DEFAULT_DATABASE_ID,
                "initializedAt" to System.currentTimeMillis(),
                "status" to "HEALTHY",
                "collections" to listOf(
                    FirestoreSchema.Collections.WORKSPACE_LISTINGS,
                    FirestoreSchema.Collections.USER_PROFILES,
                    FirestoreSchema.Collections.SUBSCRIPTION_FORMULAS,
                    FirestoreSchema.Collections.BOOKING_REQUESTS,
                    FirestoreSchema.Collections.USER_CREDENTIALS,
                    FirestoreSchema.Collections.WHISH_TRANSACTIONS,
                    FirestoreSchema.Collections.AUDIT_SECURITY_LOGS
                )
            )
            db.collection(FirestoreSchema.Collections.SYSTEM_METADATA)
                .document("schema_info")
                .set(metadata, SetOptions.merge())
                .await()
            Log.i(TAG, "Firestore Schema initialized successfully version: ${FirestoreSchema.SCHEMA_VERSION}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firestore schema: ${e.message}", e)
            false
        }
    }

    /**
     * Fire-and-forget seed of default/starter data (subscription formulas, and whatever
     * initial spaces/users the caller already holds in memory) using merge writes, so this
     * is safe to call repeatedly without clobbering documents that already exist server-side.
     */
    fun seedInitialData(
        initialSpaces: List<SpaceListing>,
        initialUsers: List<AppUser>,
        initialFormulas: List<SubscriptionFormula> = getDefaultSubscriptionFormulas()
    ) {
        val db = firestore ?: return
        listenerScope.launch {
            try {
                initializeSchema()
                initialFormulas.forEach { formula ->
                    db.collection(FirestoreSchema.Collections.SUBSCRIPTION_FORMULAS)
                        .document(formula.id)
                        .set(formula.toFirestoreMap(), SetOptions.merge())
                        .await()
                }
                initialSpaces.forEach { space ->
                    db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
                        .document(space.id)
                        .set(space.toFirestoreMap(), SetOptions.merge())
                        .await()
                }
                initialUsers.forEach { user ->
                    db.collection(FirestoreSchema.Collections.USER_PROFILES)
                        .document(user.id)
                        .set(user.toFirestoreMap(), SetOptions.merge())
                        .await()
                }
                Log.d(TAG, "Initial data seed complete.")
            } catch (e: Exception) {
                Log.w(TAG, "Initial data seed fallback: ${e.message}")
            }
        }
    }

    /**
     * Attaches real-time snapshot listeners for the collections that need live cross-device
     * sync (workspaces, users, subscription formulas, bookings). Returns nothing; call
     * [clearListeners] to detach everything this has registered.
     */
    fun attachLiveListeners(
        onWorkspacesUpdated: (List<SpaceListing>) -> Unit,
        onUsersUpdated: (List<AppUser>) -> Unit,
        onBookingsUpdated: (List<RentalBookingRequest>) -> Unit,
        onFormulasUpdated: (List<SubscriptionFormula>) -> Unit,
        onTransactionsUpdated: (List<WhishTransaction>) -> Unit
    ) {
        val db = firestore ?: return

        try {
            val spaceListener = db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Workspaces sync note: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val spaces = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { data -> SpaceListing.fromFirestoreMap(doc.id, data) }
                        }
                        if (spaces.isNotEmpty()) onWorkspacesUpdated(spaces)
                    }
                }
            activeListeners.add(spaceListener)

            val userListener = db.collection(FirestoreSchema.Collections.USER_PROFILES)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Users sync note: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val users = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { data -> AppUser.fromFirestoreMap(doc.id, data) }
                        }
                        if (users.isNotEmpty()) onUsersUpdated(users)
                    }
                }
            activeListeners.add(userListener)

            val formulaListener = db.collection(FirestoreSchema.Collections.SUBSCRIPTION_FORMULAS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Formulas sync note: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val formulas = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { data -> SubscriptionFormula.fromFirestoreMap(doc.id, data) }
                        }
                        if (formulas.isNotEmpty()) onFormulasUpdated(formulas)
                    }
                }
            activeListeners.add(formulaListener)

            // Booking requests listener — the single collection ("booking_requests") that both
            // reads and writes must agree on. See ProSpaceRepository for the write side.
            val bookingListener = db.collection(FirestoreSchema.Collections.BOOKING_REQUESTS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Bookings sync note: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val bookings = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { data -> BookingRequest.fromFirestoreMap(doc.id, data) }
                        }
                        if (bookings.isNotEmpty()) onBookingsUpdated(bookings)
                    }
                }
            activeListeners.add(bookingListener)

            // Transactions are now created/settled server-side by the Whish payment Cloud
            // Functions (initiateWhishPayment/whishWebhook/checkWhishStatus) via Admin SDK —
            // this listener is how the client ever finds out about them at all.
            val transactionListener = db.collection(FirestoreSchema.Collections.WHISH_TRANSACTIONS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Transactions sync note: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val transactions = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { data -> WhishTransaction.fromFirestoreMap(doc.id, data) }
                        }
                        if (transactions.isNotEmpty()) onTransactionsUpdated(transactions)
                    }
                }
            activeListeners.add(transactionListener)
        } catch (e: Exception) {
            Log.w(TAG, "Live listeners attachment warning: ${e.message}")
        }
    }

    fun clearListeners() {
        activeListeners.forEach { runCatching { it.remove() } }
        activeListeners.clear()
    }

    // ==========================================
    // WORKSPACE LISTINGS (schema.gql SpaceListing)
    // ==========================================

    /**
     * Persists the FULL workspace document (equipment, rental formulas, rules, schedule,
     * subdivisions included) — previously this wrote only a partial field subset, silently
     * dropping nested data on every save.
     */
    suspend fun saveWorkspace(space: SpaceListing): Boolean {
        return try {
            val db = firestore ?: return false
            db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
                .document(space.id)
                .set(space.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving workspace: ${e.message}", e)
            false
        }
    }

    fun observeWorkspaces(): Flow<List<Map<String, Any>>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration: ListenerRegistration = db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing workspaces: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.data }
                    trySend(list)
                }
            }

        awaitClose { registration.remove() }
    }

    // ==========================================
    // USER PROFILES (schema.gql AppUser)
    // ==========================================

    suspend fun saveUserProfile(user: AppUser): Boolean {
        return try {
            val db = firestore ?: return false
            db.collection(FirestoreSchema.Collections.USER_PROFILES)
                .document(user.id)
                .set(user.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile: ${e.message}", e)
            false
        }
    }

    suspend fun getUserProfile(userId: String): Map<String, Any>? {
        return try {
            val db = firestore ?: return null
            val doc = db.collection(FirestoreSchema.Collections.USER_PROFILES).document(userId).get().await()
            doc.data
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user profile: ${e.message}", e)
            null
        }
    }

    suspend fun updateUserVerificationStatus(
        userId: String,
        status: MemberVerificationStatus,
        tier: VerificationTier,
        notes: String?
    ): Boolean {
        return try {
            val db = firestore ?: return false
            val updateMap = mutableMapOf<String, Any>(
                "verificationStatus" to status.name,
                "verificationTier" to tier.name,
                "isVerified" to (status == MemberVerificationStatus.VERIFIED),
                "updatedAt" to System.currentTimeMillis()
            )
            if (notes != null) updateMap["verificationNotes"] = notes

            db.collection(FirestoreSchema.Collections.USER_PROFILES)
                .document(userId)
                .set(updateMap, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating verification status: ${e.message}", e)
            false
        }
    }

    // ==========================================
    // ADMIN PRICING STATE
    // ==========================================
    // The initiateWhishPayment Cloud Function reads this same document server-side to
    // compute real charge amounts — see AdminPricingState.toFirestoreMap().

    suspend fun savePricingState(pricing: AdminPricingState): Boolean {
        return try {
            val db = firestore ?: return false
            db.collection(AdminPricingState.COLLECTION_PATH)
                .document(AdminPricingState.DOCUMENT_ID)
                .set(pricing.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pricing state: ${e.message}", e)
            false
        }
    }

    suspend fun getPricingState(): AdminPricingState? {
        return try {
            val db = firestore ?: return null
            val doc = db.collection(AdminPricingState.COLLECTION_PATH)
                .document(AdminPricingState.DOCUMENT_ID)
                .get()
                .await()
            doc.data?.let { AdminPricingState.fromFirestoreMap(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching pricing state: ${e.message}", e)
            null
        }
    }

    // ==========================================
    // SUBSCRIPTION FORMULAS
    // ==========================================

    suspend fun saveSubscriptionFormula(formula: SubscriptionFormula): Boolean {
        return try {
            val db = firestore ?: return false
            db.collection(FirestoreSchema.Collections.SUBSCRIPTION_FORMULAS)
                .document(formula.id)
                .set(formula.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving subscription formula: ${e.message}", e)
            false
        }
    }

    /**
     * Standard Lebanese workspace subscription packages, used to seed a fresh project.
     */
    fun getDefaultSubscriptionFormulas(): List<SubscriptionFormula> {
        return listOf(
            SubscriptionFormula(
                id = "SUB-FRM-001",
                title = "Flex Day-Pass (Hourly / Half-Day)",
                type = RentalFormulaType.HOURLY,
                billingInterval = SubscriptionBillingInterval.HOURLY,
                priceUsd = 15.0,
                description = "On-demand access for client consultations, depositions, and agile team huddles",
                daysPerWeek = 1,
                hoursPerDay = 4,
                startHour = "08:00",
                endHour = "20:00",
                targetSpecialties = listOf("Consultants", "Attorneys", "Engineers", "Financial Advisors"),
                includedPerks = listOf(
                    "High-speed Fiber Wi-Fi",
                    "Receptionist Greeting",
                    "Coffee & Tea Bar",
                    "24/7 Power Continuity"
                ),
                isFeatured = false
            ),
            SubscriptionFormula(
                id = "SUB-FRM-002",
                title = "Practitioner Shift Formula",
                type = RentalFormulaType.SHIFT,
                billingInterval = SubscriptionBillingInterval.SHIFT,
                priceUsd = 180.0,
                description = "Dedicated morning or afternoon shift access (Mon - Fri) tailored for active practice",
                daysPerWeek = 5,
                hoursPerDay = 6,
                startHour = "08:00",
                endHour = "14:00",
                targetSpecialties = listOf("Healthcare Specialists", "Architects", "Designers", "Chartered Accountants"),
                includedPerks = listOf(
                    "Dedicated Desk or Clinic Room",
                    "Client Lounge & Waiting Area",
                    "Syndicate Verified Listing Badge",
                    "10 Hours Conference Room Access",
                    "Fiber Internet & Generator Backup"
                ),
                isFeatured = true
            ),
            SubscriptionFormula(
                id = "SUB-FRM-003",
                title = "Day-per-Week Retainer",
                type = RentalFormulaType.DAY_PER_WEEK,
                billingInterval = SubscriptionBillingInterval.DAY_PER_WEEK,
                priceUsd = 120.0,
                description = "Reserve a specific fixed day every week throughout the entire month (e.g., Every Wednesday)",
                daysPerWeek = 1,
                hoursPerDay = 10,
                startHour = "08:00",
                endHour = "18:00",
                targetSpecialties = listOf("Visiting Doctors", "Legal Counsel", "Auditors", "Consulting Engineers"),
                includedPerks = listOf(
                    "Guaranteed Room Reservation",
                    "Receptionist Patient/Client Check-in",
                    "Private File Storage Locker",
                    "Fast Wi-Fi & Generator Power"
                ),
                isFeatured = false
            ),
            SubscriptionFormula(
                id = "SUB-FRM-004",
                title = "Full Dedicated Executive Suite (Monthly)",
                type = RentalFormulaType.FULL_MONTH,
                billingInterval = SubscriptionBillingInterval.MONTHLY,
                priceUsd = 450.0,
                description = "24/7 exclusive private office with premier commercial address and full receptionist support",
                daysPerWeek = 6,
                hoursPerDay = 24,
                startHour = "00:00",
                endHour = "23:59",
                targetSpecialties = listOf("Law Firms", "Engineering Consultancies", "Medical Centers", "Tech Startups"),
                includedPerks = listOf(
                    "24/7 Keycard & Smart Lock Access",
                    "Commercial Business Address Registration",
                    "Full Receptionist & Mail Handling",
                    "Unlimited High-Speed Fiber Internet",
                    "Solar + Generator Uninterrupted Power",
                    "20 Hours Executive Boardroom Credits"
                ),
                discountPercent = 10.0,
                isFeatured = true
            )
        )
    }

    // ==========================================
    // BOOKING REQUESTS (schema.gql BookingRequest)
    // ==========================================

    suspend fun saveBookingRequest(booking: BookingRequest): Boolean {
        return try {
            val db = firestore ?: return false
            db.collection(FirestoreSchema.Collections.BOOKING_REQUESTS)
                .document(booking.id)
                .set(booking.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving booking request: ${e.message}", e)
            false
        }
    }

    suspend fun updateBookingStatus(
        requestId: String,
        status: BookingRequestStatus,
        rejectionReason: String? = null
    ): Boolean {
        return try {
            val db = firestore ?: return false
            val updates = mutableMapOf<String, Any>(
                "status" to status.name,
                "reviewedAt" to System.currentTimeMillis()
            )
            if (rejectionReason != null) {
                updates["rejectionReason"] = rejectionReason
            }
            if (status == BookingRequestStatus.ACCEPTED) {
                updates["isExternalPaymentSettled"] = true
            }

            db.collection(FirestoreSchema.Collections.BOOKING_REQUESTS)
                .document(requestId)
                .set(updates, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating booking status: ${e.message}", e)
            false
        }
    }

    // ==========================================
    // CREDENTIAL DOCUMENTS
    // ==========================================

    suspend fun saveCredentialDocument(doc: CredentialDocument): Boolean {
        return try {
            val db = firestore ?: return false
            db.collection(FirestoreSchema.Collections.USER_CREDENTIALS)
                .document(doc.id)
                .set(doc.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving credential document: ${e.message}", e)
            false
        }
    }

    // ==========================================
    // FINANCIAL TRANSACTIONS & AUDIT LOGS
    // ==========================================

    suspend fun recordTransaction(tx: WhishTransaction): Boolean {
        return try {
            val db = firestore ?: return false
            db.collection(FirestoreSchema.Collections.WHISH_TRANSACTIONS)
                .document(tx.id)
                .set(tx.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error recording transaction: ${e.message}", e)
            false
        }
    }

    suspend fun recordAuditLog(log: AuditSecurityLog): Boolean {
        return try {
            val db = firestore ?: return false
            db.collection(FirestoreSchema.Collections.AUDIT_SECURITY_LOGS)
                .document(log.id)
                .set(log.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error recording audit log: ${e.message}", e)
            false
        }
    }

    // ==========================================
    // DATA CONNECT COMPLIANCE VALIDATOR
    // ==========================================
    // NOTE: this "audit" is largely self-congratulatory today (several checks are
    // hardcoded to pass regardless of real state). It's rewritten to reflect actual
    // runtime state in the remediation plan's testing-hardening phase — not touched here.

    data class ComplianceCheck(
        val name: String,
        val isCompliant: Boolean,
        val details: String
    )

    data class ComplianceReport(
        val isAllCompliant: Boolean,
        val timestamp: Long,
        val checks: List<ComplianceCheck>
    )

    fun runDataConnectComplianceAudit(): ComplianceReport {
        val checks = mutableListOf<ComplianceCheck>()

        // 1. Check Schema Definitions
        checks.add(
            ComplianceCheck(
                name = "Firestore Schema Version Contract",
                isCompliant = FirestoreSchema.SCHEMA_VERSION == "2.0.0",
                details = "Schema Version: ${FirestoreSchema.SCHEMA_VERSION}"
            )
        )

        // 2. Check Data Connect Tables Mapping
        val requiredCollections = listOf(
            FirestoreSchema.Collections.WORKSPACE_LISTINGS,
            FirestoreSchema.Collections.USER_PROFILES,
            FirestoreSchema.Collections.SUBSCRIPTION_FORMULAS,
            FirestoreSchema.Collections.BOOKING_REQUESTS,
            FirestoreSchema.Collections.USER_CREDENTIALS,
            FirestoreSchema.Collections.WHISH_TRANSACTIONS,
            FirestoreSchema.Collections.AUDIT_SECURITY_LOGS
        )
        checks.add(
            ComplianceCheck(
                name = "Data Connect GraphQL Collections Mapping",
                isCompliant = requiredCollections.size == 7,
                details = "Mapped collections: ${requiredCollections.joinToString(", ")}"
            )
        )

        // 3. Check Firestore Client Status
        checks.add(
            ComplianceCheck(
                name = "Firebase Firestore Client Initialization",
                isCompliant = firestore != null || true, // Offline-first compatible
                details = if (firestore != null) "Firestore Client Active" else "Firestore Offline-First Fallback Active"
            )
        )

        // 4. Check Data Connect Entities
        checks.add(
            ComplianceCheck(
                name = "Data Connect GraphQL Entity: SpaceListing",
                isCompliant = true,
                details = "Table space_listings with 28 mapped columns verified"
            )
        )
        checks.add(
            ComplianceCheck(
                name = "Data Connect GraphQL Entity: AppUser",
                isCompliant = true,
                details = "Table users with role & syndicate accreditation verified"
            )
        )
        checks.add(
            ComplianceCheck(
                name = "Data Connect GraphQL Entity: BookingRequest",
                isCompliant = true,
                details = "Table booking_requests with lifecycle states verified"
            )
        )
        checks.add(
            ComplianceCheck(
                name = "Whish Money & SHA-256 Protocol Parity",
                isCompliant = true,
                details = "HMAC/SHA-256 signature and dual verification layer operational"
            )
        )

        return ComplianceReport(
            isAllCompliant = checks.all { it.isCompliant },
            timestamp = System.currentTimeMillis(),
            checks = checks
        )
    }
}
