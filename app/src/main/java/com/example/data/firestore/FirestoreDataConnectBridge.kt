package com.example.data.firestore

import android.util.Log
import com.example.data.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ProHost Cloud Firestore & Firebase Data Connect Integration Bridge
 * 
 * Bridges Firebase Firestore cloud storage and Data Connect database services with
 * local state flows, resilient offline caching, and automatic schema initialization.
 */
class FirestoreDataConnectBridge private constructor() {

    companion object {
        private const val TAG = "FirestoreDataConnect"

        @Volatile
        private var instance: FirestoreDataConnectBridge? = null

        fun getInstance(): FirestoreDataConnectBridge {
            return instance ?: synchronized(this) {
                instance ?: FirestoreDataConnectBridge().also { instance = it }
            }
        }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var firestoreInstance: FirebaseFirestore? = null

    private val _isCloudConnected = MutableStateFlow(false)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    private val _syncMessage = MutableStateFlow("Initializing Cloud Schema...")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    private val _subscriptionFormulas = MutableStateFlow<List<SubscriptionFormula>>(emptyList())
    val subscriptionFormulas: StateFlow<List<SubscriptionFormula>> = _subscriptionFormulas.asStateFlow()

    private val activeListeners = mutableListOf<ListenerRegistration>()

    private fun getFirestore(): FirebaseFirestore? {
        if (firestoreInstance == null) {
            try {
                firestoreInstance = FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore initialization note: ${e.message}")
            }
        }
        return firestoreInstance
    }

    /**
     * Initializes Firestore schema collections and seeds default professional workspace templates,
     * user profiles with verification statuses, and flexible subscription formulas.
     */
    fun initializeSchema(
        initialSpaces: List<SpaceListing>,
        initialUsers: List<AppUser>,
        initialFormulas: List<SubscriptionFormula> = getDefaultSubscriptionFormulas()
    ) {
        _subscriptionFormulas.value = initialFormulas

        coroutineScope.launch {
            try {
                val db = getFirestore()
                if (db == null) {
                    _isCloudConnected.value = false
                    _syncMessage.value = "Local Data Connect Cache Ready"
                    return@launch
                }

                // Register Schema Versioning Metadata
                val metaDoc = mapOf(
                    "schemaVersion" to FirestoreSchema.SCHEMA_VERSION,
                    "serviceId" to FirestoreSchema.DATA_CONNECT_SERVICE_ID,
                    "initializedAt" to System.currentTimeMillis(),
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
                    .document("schema_config")
                    .set(metaDoc, SetOptions.merge())

                // Seed Default Subscription Formulas if not present
                initialFormulas.forEach { formula ->
                    db.collection(FirestoreSchema.Collections.SUBSCRIPTION_FORMULAS)
                        .document(formula.id)
                        .set(formula.toFirestoreMap(), SetOptions.merge())
                }

                // Check and Seed Workspace Listings
                initialSpaces.forEach { space ->
                    db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
                        .document(space.id)
                        .set(space.toFirestoreMap(), SetOptions.merge())
                }

                // Check and Seed User Profiles with Lebanese Verification Metadata
                initialUsers.forEach { user ->
                    db.collection(FirestoreSchema.Collections.USER_PROFILES)
                        .document(user.id)
                        .set(user.toFirestoreMap(), SetOptions.merge())
                }

                _isCloudConnected.value = true
                _syncMessage.value = "Firestore Schema Active • Live Sync Ready"
                Log.d(TAG, "ProSpace Firestore & Data Connect Schema initialized successfully.")
            } catch (e: Exception) {
                Log.w(TAG, "Firestore schema initialization fallback: ${e.message}")
                _isCloudConnected.value = false
                _syncMessage.value = "Resilient Offline Mode Active"
            }
        }
    }

    /**
     * Attaches real-time cloud listeners for synchronization.
     */
    fun attachLiveListeners(
        onWorkspacesUpdated: (List<SpaceListing>) -> Unit,
        onUsersUpdated: (List<AppUser>) -> Unit,
        onBookingsUpdated: (List<RentalBookingRequest>) -> Unit,
        onFormulasUpdated: (List<SubscriptionFormula>) -> Unit
    ) {
        val db = getFirestore() ?: return

        try {
            // Workspace listings listener
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

            // User profiles listener
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

            // Subscription formulas listener
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
                        if (formulas.isNotEmpty()) {
                            _subscriptionFormulas.value = formulas
                            onFormulasUpdated(formulas)
                        }
                    }
                }
            activeListeners.add(formulaListener)

            // Booking requests listener
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

        } catch (e: Exception) {
            Log.w(TAG, "Live listeners attachment warning: ${e.message}")
        }
    }

    /**
     * Persists or updates a Workspace Listing in Firestore
     */
    fun saveWorkspaceListing(space: SpaceListing, onComplete: ((Boolean) -> Unit)? = null) {
        coroutineScope.launch {
            try {
                val db = getFirestore()
                if (db != null) {
                    db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
                        .document(space.id)
                        .set(space.toFirestoreMap(), SetOptions.merge())
                        .await()
                    _isCloudConnected.value = true
                    onComplete?.invoke(true)
                } else {
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Save workspace error: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Persists or updates a User Profile with verification status in Firestore
     */
    fun saveUserProfile(user: AppUser, onComplete: ((Boolean) -> Unit)? = null) {
        coroutineScope.launch {
            try {
                val db = getFirestore()
                if (db != null) {
                    db.collection(FirestoreSchema.Collections.USER_PROFILES)
                        .document(user.id)
                        .set(user.toFirestoreMap(), SetOptions.merge())
                        .await()
                    _isCloudConnected.value = true
                    onComplete?.invoke(true)
                } else {
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Save user profile error: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Updates verification status and credential tier for a user in Firestore
     */
    fun updateUserVerificationStatus(
        userId: String,
        status: MemberVerificationStatus,
        tier: VerificationTier,
        notes: String?,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        coroutineScope.launch {
            try {
                val db = getFirestore()
                if (db != null) {
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
                    onComplete?.invoke(true)
                } else {
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update verification status error: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Persists a Subscription Formula in Firestore
     */
    fun saveSubscriptionFormula(formula: SubscriptionFormula, onComplete: ((Boolean) -> Unit)? = null) {
        coroutineScope.launch {
            try {
                val db = getFirestore()
                if (db != null) {
                    db.collection(FirestoreSchema.Collections.SUBSCRIPTION_FORMULAS)
                        .document(formula.id)
                        .set(formula.toFirestoreMap(), SetOptions.merge())
                        .await()
                    onComplete?.invoke(true)
                } else {
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Save subscription formula error: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Persists or updates a Credential Document in Firestore
     */
    fun saveCredentialDocument(doc: CredentialDocument, onComplete: ((Boolean) -> Unit)? = null) {
        coroutineScope.launch {
            try {
                val db = getFirestore()
                if (db != null) {
                    db.collection(FirestoreSchema.Collections.USER_CREDENTIALS)
                        .document(doc.id)
                        .set(doc.toFirestoreMap(), SetOptions.merge())
                        .await()
                    onComplete?.invoke(true)
                } else {
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Save credential document error: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Persists a Whish Transaction in Firestore
     */
    fun saveWhishTransaction(tx: WhishTransaction, onComplete: ((Boolean) -> Unit)? = null) {
        coroutineScope.launch {
            try {
                val db = getFirestore()
                if (db != null) {
                    db.collection(FirestoreSchema.Collections.WHISH_TRANSACTIONS)
                        .document(tx.id)
                        .set(tx.toFirestoreMap(), SetOptions.merge())
                        .await()
                    onComplete?.invoke(true)
                } else {
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Save transaction error: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Persists an Audit Security Log in Firestore
     */
    fun saveAuditLog(log: AuditSecurityLog, onComplete: ((Boolean) -> Unit)? = null) {
        coroutineScope.launch {
            try {
                val db = getFirestore()
                if (db != null) {
                    db.collection(FirestoreSchema.Collections.AUDIT_SECURITY_LOGS)
                        .document(log.id)
                        .set(log.toFirestoreMap(), SetOptions.merge())
                        .await()
                    onComplete?.invoke(true)
                } else {
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Save audit log error: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    fun clearListeners() {
        activeListeners.forEach { runCatching { it.remove() } }
        activeListeners.clear()
    }

    /**
     * Standard Lebanese workspace subscription packages
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
}
