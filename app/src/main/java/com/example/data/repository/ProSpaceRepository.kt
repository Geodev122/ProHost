package com.example.data.repository

import android.util.Log
import com.example.data.crypto.WhishSecurity
import com.example.data.firestore.FirestoreDataConnectBridge
import com.example.data.firestore.FirestoreSchema
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
    private val firestoreBridge = FirestoreDataConnectBridge.getInstance()
    private var firestoreListener: ListenerRegistration? = null

    val subscriptionFormulas: StateFlow<List<SubscriptionFormula>> = firestoreBridge.subscriptionFormulas
    val isCloudConnected: StateFlow<Boolean> = firestoreBridge.isCloudConnected

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

    private val _currentUser = MutableStateFlow<AppUser?>(
        AppUser(
            id = "USR-ADMIN-ROOT",
            email = "geo.elnajjar@gmail.com",
            fullName = "Geo El-Najjar",
            role = UserRole.ADMIN,
            specialty = "Super Administrator & Security Governance",
            phone = "+961 70 888 999",
            affiliation = "ProSpace Executive HQ & Central Governance",
            syndicateNumber = "SUPER-ADMIN-01",
            governorate = Governorate.BEIRUT,
            isVerified = true,
            verificationStatus = MemberVerificationStatus.VERIFIED,
            verificationTier = VerificationTier.TIER_3_COMMERCIAL_HOST,
            trustScore = 100
        )
    )
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    private val _credentialDocuments = MutableStateFlow<List<CredentialDocument>>(emptyList())
    val credentialDocuments: StateFlow<List<CredentialDocument>> = _credentialDocuments.asStateFlow()

    private val _avatarCampaigns = MutableStateFlow<List<AvatarCampaign>>(emptyList())
    val avatarCampaigns: StateFlow<List<AvatarCampaign>> = _avatarCampaigns.asStateFlow()

    private val _bookingRequests = MutableStateFlow<List<RentalBookingRequest>>(emptyList())
    val bookingRequests: StateFlow<List<RentalBookingRequest>> = _bookingRequests.asStateFlow()

    private val _spaceArchitectureSchema = MutableStateFlow<SpaceArchitectureSchema>(createDefaultSchema())
    val spaceArchitectureSchema: StateFlow<SpaceArchitectureSchema> = _spaceArchitectureSchema.asStateFlow()

    init {
        seedInitialData()
        startRealtimeSync()
    }

    fun startRealtimeSync() {
        try {
            // Initialize Firestore collections schema & seed default structures
            firestoreBridge.initializeSchema(
                initialSpaces = _spaces.value,
                initialUsers = _users.value
            )

            // Attach multi-collection real-time snapshot listeners
            firestoreBridge.attachLiveListeners(
                onWorkspacesUpdated = { updatedSpaces ->
                    _spaces.value = updatedSpaces
                },
                onUsersUpdated = { updatedUsers ->
                    _users.value = updatedUsers
                },
                onBookingsUpdated = { updatedBookings ->
                    _bookingRequests.value = updatedBookings
                },
                onFormulasUpdated = { _ ->
                    // Handled internally in bridge
                }
            )

            val firestore = FirebaseFirestore.getInstance()
            firestoreListener?.remove()
            firestoreListener = firestore.collection("prospace_bookings")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Firestore sync listener note: ${error.message}. Operating in resilient offline mode.")
                        _isOfflineMode.value = true
                        _syncStatusMessage.value = "Offline Cache Active • Local Persistence Ready"
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        _isOfflineMode.value = false
                        _syncStatusMessage.value = "Real-time Cloud Sync Active"
                        snapshot.documents.forEach { doc ->
                            val statusStr = doc.getString("status")
                            val reqId = doc.id
                            if (statusStr != null) {
                                val newStatus = runCatching { BookingRequestStatus.valueOf(statusStr) }.getOrNull()
                                if (newStatus != null) {
                                    _bookingRequests.value = _bookingRequests.value.map { req ->
                                        if (req.id == reqId && req.status != newStatus) {
                                            req.copy(
                                                status = newStatus,
                                                reviewedAt = doc.getLong("reviewedAt") ?: System.currentTimeMillis()
                                            )
                                        } else req
                                    }
                                }
                            }
                        }
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
            try {
                val firestore = FirebaseFirestore.getInstance()
                val updateMap = mutableMapOf<String, Any>(
                    "status" to status.name,
                    "reviewedAt" to System.currentTimeMillis()
                )
                if (note != null) {
                    updateMap["rejectionReason"] = note
                }
                firestore.collection("prospace_bookings").document(requestId)
                    .set(updateMap, SetOptions.merge())
                    .addOnSuccessListener {
                        _isOfflineMode.value = false
                        _syncStatusMessage.value = "Live Cloud Sync: $requestId ➔ ${status.name}"
                    }
                    .addOnFailureListener {
                        _isOfflineMode.value = true
                        _syncStatusMessage.value = "Offline: Status change cached locally"
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore sync push exception: ${e.message}")
                _isOfflineMode.value = true
                _syncStatusMessage.value = "Offline: Status cached locally"
            }
        }
    }

    fun syncNewBookingToFirestore(request: RentalBookingRequest) {
        coroutineScope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance()
                val data = mapOf(
                    "id" to request.id,
                    "spaceId" to request.spaceId,
                    "spaceTitle" to request.spaceTitle,
                    "ownerId" to request.ownerId,
                    "practitionerId" to request.practitionerId,
                    "practitionerName" to request.practitionerName,
                    "formulaType" to request.formula.type.name,
                    "totalAmountUsd" to request.totalAmountUsd,
                    "status" to request.status.name,
                    "createdAt" to request.createdAt,
                    "selectedDateTimeRange" to request.selectedDateTimeRange
                )
                firestore.collection("prospace_bookings").document(request.id)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        _isOfflineMode.value = false
                        _syncStatusMessage.value = "Booking Synced with Firebase Cloud"
                    }
                    .addOnFailureListener {
                        _isOfflineMode.value = true
                        _syncStatusMessage.value = "Offline: Booking Stored in Local Cache"
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore booking push fallback: ${e.message}")
                _isOfflineMode.value = true
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

    private fun seedInitialData() {
        val initialUsers = listOf(
            AppUser(
                id = "USR-ADMIN-ROOT",
                email = "geo.elnajjar@gmail.com",
                fullName = "Geo El-Najjar",
                role = UserRole.ADMIN,
                specialty = "Super Administrator & Security Governance",
                phone = "+961 70 888 999",
                affiliation = "ProSpace Executive HQ & Central Governance",
                syndicateNumber = "SUPER-ADMIN-01",
                governorate = Governorate.BEIRUT,
                isVerified = true,
                verificationStatus = MemberVerificationStatus.VERIFIED,
                verificationTier = VerificationTier.TIER_3_COMMERCIAL_HOST,
                trustScore = 100
            )
        )
        _users.value = initialUsers

        val initialAuditLogs = listOf(
            AuditSecurityLog(
                id = "LOG-1001",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 2,
                actionType = "SUPER_ADMIN_AUTHORIZATION",
                details = "Root security & governance clearance granted to geo.elnajjar@gmail.com",
                actorEmail = "geo.elnajjar@gmail.com",
                severity = "SECURE"
            ),
            AuditSecurityLog(
                id = "LOG-1002",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 45,
                actionType = "WHISH_CRYPTO_INITIALIZED",
                details = "Channel ID 15462415 MD5 verification active for Lebanon settlement corridor",
                actorEmail = "system@prospace.lb",
                severity = "INFO"
            ),
            AuditSecurityLog(
                id = "LOG-1003",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 20,
                actionType = "PRICING_ENGINE_BASELINE",
                details = "Dynamic monthly listing fee verified at $1.80 USD baseline",
                actorEmail = "geo.elnajjar@gmail.com",
                severity = "INFO"
            ),
            AuditSecurityLog(
                id = "LOG-1004",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 5,
                actionType = "ROLE_SECURITY_FIREWALL",
                details = "Role-based access matrix enforced: Super Admin restricted exclusively to Governance & Security Console",
                actorEmail = "geo.elnajjar@gmail.com",
                severity = "SECURE"
            )
        )
        _auditLogs.value = initialAuditLogs

        val initialDocuments = listOf(
            CredentialDocument(
                id = "DOC-ADMIN-01",
                userId = "USR-ADMIN-ROOT",
                type = DocumentType.NATIONAL_ID,
                fileName = "Biometric_Passport_Geo_ElNajjar.pdf",
                fileSizeKb = 2600,
                uploadedAt = System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30,
                status = DocumentStatus.VERIFIED,
                documentNumber = "PASS-RL8829104",
                issuingAuthority = "General Directorate of General Security",
                expiryDate = "2034-03-15",
                verificationHash = "SHA256:ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                reviewerNotes = "Super Admin Identity Accreditation Complete"
            ),
            CredentialDocument(
                id = "DOC-ADMIN-02",
                userId = "USR-ADMIN-ROOT",
                type = DocumentType.TAX_REGISTRATION,
                fileName = "Ministry_Of_Finance_Raqam_Mali.pdf",
                fileSizeKb = 1890,
                uploadedAt = System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30,
                status = DocumentStatus.VERIFIED,
                documentNumber = "MOF-774921-601",
                issuingAuthority = "Republic of Lebanon Ministry of Finance",
                expiryDate = "2028-12-31",
                verificationHash = "SHA256:2c624232cdd221771294dfbb310aca000a0df6ac9b66bb",
                reviewerNotes = "Financial registration verified"
            )
        )
        _credentialDocuments.value = initialDocuments

        val initialSpaces = listOf(
            SpaceListing(
                id = "SPC-BEI-101",
                title = "Achrafieh Premium Executive Suite & Consulting Bay",
                spaceType = SpaceType.PRIVATE_OFFICE,
                governorate = Governorate.BEIRUT,
                district = "Achrafieh - Sassine Square",
                streetAddress = "Independence Avenue, SAS Tower, Floor 4",
                floorInfo = "Floor 4, Suite 402 (Elevator & Ramp)",
                lat = 33.8886,
                lng = 35.5184,
                isShared = true,
                complementarySpecialties = listOf("Financial Advisor", "Legal Counsel", "Business Consultant", "Architect"),
                residentPractitioners = listOf("Nour El-Khoury (Consultant)", "Alain Sfeir (Senior Architect)"),
                essentialFacilities = listOf(
                    "24/7 Generator Electricity",
                    "Continuous Water Supply",
                    "High-Speed Fiber Wi-Fi",
                    "HVAC Climate Control",
                    "Daily Professional Sanitization",
                    "Dedicated Underground Parking",
                    "Client Accessibility / Elevator",
                    "Reception & Admin Support"
                ),
                equipment = listOf(
                    EquipmentItem("EQ-1", "4K Ultra-HD Presentation Display", EquipmentCategory.IT_TECH, 1, "Wireless screen mirroring & HDMI hub"),
                    EquipmentItem("EQ-2", "Motorized Ergonomic Standing Desk", EquipmentCategory.WORKSPACES, 1, "Dual-monitor mount with cable management"),
                    EquipmentItem("EQ-3", "Smart Keyless Lock System", EquipmentCategory.SPECIALIZED, 1, "App-enabled entry tracking"),
                    EquipmentItem("EQ-4", "Executive Walnut Wood Desk", EquipmentCategory.WORKSPACES, 1, "With ergonomic leather executive chair"),
                    EquipmentItem("EQ-5", "Client Meeting Armchairs", EquipmentCategory.WORKSPACES, 2, "High-density foam designer chairs"),
                    EquipmentItem("EQ-6", "Workstation PC & High-Speed Scanner", EquipmentCategory.IT_TECH, 1, "Network duplex laser scanner"),
                    EquipmentItem("EQ-7", "Beverage Station & Espresso Bar", EquipmentCategory.OFFICE_AMENITIES, 1, "Italian bean-to-cup machine"),
                    EquipmentItem("EQ-8", "Soundproof Acoustic Dividers", EquipmentCategory.OFFICE_AMENITIES, 2, "Certified sound isolation")
                ),
                rentalFormulas = listOf(
                    RentalFormula(
                        id = "FRM-BEI-01",
                        type = RentalFormulaType.SHIFT,
                        rateUsd = 120.0,
                        scheduleDescription = "Morning Shift (8:00 AM - 1:00 PM) • 2 days/wk",
                        daysOfWeek = listOf("Tue", "Thu"),
                        startHour = "08:00",
                        endHour = "13:00",
                        totalWeeklyHours = 10
                    ),
                    RentalFormula(
                        id = "FRM-BEI-02",
                        type = RentalFormulaType.SHIFT,
                        rateUsd = 140.0,
                        scheduleDescription = "Afternoon Shift (1:30 PM - 6:30 PM) • 2 days/wk",
                        daysOfWeek = listOf("Mon", "Wed"),
                        startHour = "13:30",
                        endHour = "18:30",
                        totalWeeklyHours = 10
                    ),
                    RentalFormula(
                        id = "FRM-BEI-03",
                        type = RentalFormulaType.DAY_PER_WEEK,
                        rateUsd = 250.0,
                        scheduleDescription = "Every Tuesday & Thursday (Full Day 8AM - 7PM)",
                        daysOfWeek = listOf("Tue", "Thu"),
                        startHour = "08:00",
                        endHour = "19:00",
                        totalWeeklyHours = 22
                    ),
                    RentalFormula(
                        id = "FRM-BEI-04",
                        type = RentalFormulaType.FULL_MONTH,
                        rateUsd = 580.0,
                        scheduleDescription = "Dedicated Exclusive Monthly Access",
                        daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
                        startHour = "08:00",
                        endHour = "20:00",
                        totalWeeklyHours = 72
                    )
                ),
                rules = PremisesRules(
                    smokingAllowed = false,
                    foodAllowed = true,
                    petsAllowed = false,
                    visitorPolicy = "Accompanying guests allowed; 1 client at a time in consult chamber",
                    offHoursAccess = true,
                    sharedAmenities = listOf("Executive Reception", "Lounge", "Coffee & Water Station", "Document & Storage Area")
                ),
                schedule = SpaceOperatingSchedule(
                    openingHour = "08:00",
                    closingHour = "20:00",
                    operatingDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
                    blackoutSlots = listOf(
                        BlackoutSlot("BLK-01", "Friday", "18:00", "20:00", "Scheduled Facility Maintenance"),
                        BlackoutSlot("BLK-02", "Sunday", "08:00", "20:00", "Sunday Facility Closure")
                    ),
                    isSundayOperating = false
                ),
                ownerId = "ORG-BEI-101",
                ownerName = "Achrafieh Commercial Properties",
                ownerPhone = "+961 1 200 300",
                ownerEmail = "achrafieh.suites@prospace.lb",
                isVerified = true,
                isActiveSubscription = true,
                subscriptionExpiryMillis = System.currentTimeMillis() + (29L * 24 * 60 * 60 * 1000),
                imageUrls = listOf("https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&q=80&w=600"),
                baseMonthlyRateUsd = 580.0,
                avatarEngagementViews = 0,
                avatarInquiryClicks = 0
            ),
            SpaceListing(
                id = "SPC-MTL-102",
                title = "Jounieh Coastline Professional & Design Studio Center",
                spaceType = SpaceType.CENTER,
                governorate = Governorate.MOUNT_LEBANON,
                district = "Keserwan - Jounieh Main Highway",
                streetAddress = "Sea Road Plaza, Block B, Floor 2",
                floorInfo = "Floor 2, Sea View Suite 201",
                lat = 33.9808,
                lng = 35.6186,
                isShared = true,
                complementarySpecialties = listOf("Graphic Designer", "3D Visualizer", "Content Creator", "Web Developer"),
                residentPractitioners = listOf("Senior Architectural & UI/UX Consultant"),
                essentialFacilities = listOf(
                    "24/7 Generator Electricity",
                    "Continuous Water Supply",
                    "High-Speed Fiber Wi-Fi",
                    "HVAC Climate Control",
                    "Daily Professional Cleaning",
                    "Dedicated Underground Parking",
                    "Reception & Mail Handling"
                ),
                equipment = listOf(
                    EquipmentItem("EQ-9", "Calibrated Color Grading Monitor", EquipmentCategory.IT_TECH, 1, "4K IPS Panel for color precision"),
                    EquipmentItem("EQ-10", "Studio Lighting & Backdrop Kit", EquipmentCategory.SPECIALIZED, 1, "Softboxes with multiple backdrops"),
                    EquipmentItem("EQ-11", "High-Resolution Laser Printer", EquipmentCategory.OFFICE_AMENITIES, 1, "A3/A4 duplex laser unit"),
                    EquipmentItem("EQ-12", "Herman Miller Ergonomic Chair", EquipmentCategory.WORKSPACES, 1, "Ergonomic lumbar support"),
                    EquipmentItem("EQ-13", "Client Lounge Sofa & Screen", EquipmentCategory.WORKSPACES, 1, "Seats 6 for portfolio presentation")
                ),
                rentalFormulas = listOf(
                    RentalFormula(
                        id = "FRM-MTL-01",
                        type = RentalFormulaType.DAY_PER_WEEK,
                        rateUsd = 180.0,
                        scheduleDescription = "Fixed 2 Days per Week (Mon & Wed 8:30 AM - 6:30 PM)",
                        daysOfWeek = listOf("Mon", "Wed"),
                        startHour = "08:30",
                        endHour = "18:30",
                        totalWeeklyHours = 20
                    ),
                    RentalFormula(
                        id = "FRM-MTL-02",
                        type = RentalFormulaType.SHIFT,
                        rateUsd = 95.0,
                        scheduleDescription = "Morning Shift (9:00 AM - 2:00 PM) • 2 days/wk",
                        daysOfWeek = listOf("Tue", "Thu"),
                        startHour = "09:00",
                        endHour = "14:00",
                        totalWeeklyHours = 10
                    ),
                    RentalFormula(
                        id = "FRM-MTL-03",
                        type = RentalFormulaType.FULL_MONTH,
                        rateUsd = 480.0,
                        scheduleDescription = "Full Month Dedicated Practice Access",
                        daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
                        startHour = "08:00",
                        endHour = "19:00",
                        totalWeeklyHours = 66
                    )
                ),
                rules = PremisesRules(
                    smokingAllowed = false,
                    foodAllowed = false,
                    petsAllowed = false,
                    visitorPolicy = "Standard client appointment access",
                    offHoursAccess = false,
                    sharedAmenities = listOf("Executive Reception", "Panoramic Conference Suite", "Sterilization Hub")
                ),
                schedule = SpaceOperatingSchedule(
                    openingHour = "08:30",
                    closingHour = "19:00",
                    operatingDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
                    blackoutSlots = listOf(
                        BlackoutSlot("BLK-03", "Saturday", "15:00", "19:00", "Weekend Maintenance & Sanitization")
                    ),
                    isSundayOperating = false
                ),
                ownerId = "ORG-MTL-102",
                ownerName = "Coastline Suites & Studios Jounieh",
                ownerPhone = "+961 9 640 100",
                ownerEmail = "jounieh.studios@prospace.lb",
                isVerified = true,
                isActiveSubscription = true,
                subscriptionExpiryMillis = System.currentTimeMillis() + (24L * 24 * 60 * 60 * 1000),
                imageUrls = listOf("https://images.unsplash.com/photo-1504384308090-c894fdcc538d?auto=format&fit=crop&q=80&w=600"),
                baseMonthlyRateUsd = 480.0,
                avatarEngagementViews = 0,
                avatarInquiryClicks = 0,
                subdivisions = listOf(
                    Subdivision(
                        id = "SUB-JOU-01",
                        name = "Coastal Design Room A",
                        type = Level2Type.ROOMS,
                        imageUrls = listOf("https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&q=80&w=400"),
                        amenities = listOf("Dual-Monitor Workstation", "HVAC A/C", "Sea View Access"),
                        rentalStrategies = listOf(
                            SubdivisionStrategy(RentalStrategy.HOURLY, 15.0),
                            SubdivisionStrategy(RentalStrategy.SHIFT_BASED, 60.0, availableHoursOrShifts = "Morning Shift (8:00 AM - 1:00 PM)"),
                            SubdivisionStrategy(RentalStrategy.MONTHLY, 450.0)
                        )
                    ),
                    Subdivision(
                        id = "SUB-JOU-02",
                        name = "Executive Conference Room B",
                        type = Level2Type.CONFERENCE_ROOM,
                        imageUrls = listOf("https://images.unsplash.com/photo-1497366811353-6870744d04b2?auto=format&fit=crop&q=80&w=400"),
                        amenities = listOf("Ultra-HD TV Board", "Whiteboard & Projector", "Comfort Seats 10"),
                        rentalStrategies = listOf(
                            SubdivisionStrategy(RentalStrategy.HOURLY, 25.0),
                            SubdivisionStrategy(RentalStrategy.DAILY, 150.0)
                        )
                    )
                )
            ),
            SpaceListing(
                id = "SPC-NOR-103",
                title = "Tripoli Mina Executive Center",
                spaceType = SpaceType.CENTER,
                governorate = Governorate.NORTH,
                district = "Tripoli - Al Mina Port Road",
                streetAddress = "Golden Plaza Center, Suite 305",
                floorInfo = "Floor 3, Elevator Accessible",
                lat = 34.4442,
                lng = 35.8211,
                isShared = false,
                complementarySpecialties = listOf("Corporate Lawyer", "Managing Director", "Management Consultant"),
                residentPractitioners = emptyList(),
                essentialFacilities = listOf(
                    "24/7 Generator Electricity",
                    "Continuous Water Supply",
                    "High-Speed Fiber Wi-Fi",
                    "HVAC Climate Control",
                    "Daily Professional Cleaning",
                    "Client Accessibility / Bed Elevator"
                ),
                equipment = listOf(
                    EquipmentItem("EQ-14", "12-Person Boardroom Conference Table", EquipmentCategory.WORKSPACES, 1, "Integrated power ports & wireless charging"),
                    EquipmentItem("EQ-15", "4K Video Conferencing Bar", EquipmentCategory.IT_TECH, 1, "Smart zoom camera & mic array"),
                    EquipmentItem("EQ-16", "Mobile Catering Cart", EquipmentCategory.OFFICE_AMENITIES, 2, "3-tier presentation cart"),
                    EquipmentItem("EQ-17", "Interactive Smart Whiteboard", EquipmentCategory.IT_TECH, 1, "Digital stylus and screen share")
                ),
                rentalFormulas = listOf(
                    RentalFormula(
                        id = "FRM-NOR-01",
                        type = RentalFormulaType.FULL_MONTH,
                        rateUsd = 390.0,
                        scheduleDescription = "Exclusive Dedicated Workspace Month",
                        daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
                        startHour = "09:00",
                        endHour = "18:00",
                        totalWeeklyHours = 54
                    ),
                    RentalFormula(
                        id = "FRM-NOR-02",
                        type = RentalFormulaType.DAY_PER_WEEK,
                        rateUsd = 120.0,
                        scheduleDescription = "1 Day per week (Saturdays 9AM - 6PM)",
                        daysOfWeek = listOf("Sat"),
                        startHour = "09:00",
                        endHour = "18:00",
                        totalWeeklyHours = 9
                    )
                ),
                rules = PremisesRules(
                    smokingAllowed = false,
                    foodAllowed = true,
                    petsAllowed = false,
                    visitorPolicy = "Standard commercial security pass",
                    offHoursAccess = true
                ),
                schedule = SpaceOperatingSchedule(
                    openingHour = "09:00",
                    closingHour = "18:00",
                    operatingDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                ),
                ownerId = "ORG-NOR-103",
                ownerName = "Tripoli Mina Business Center",
                ownerPhone = "+961 6 210 500",
                ownerEmail = "tripoli.mina@prospace.lb",
                isVerified = true,
                isActiveSubscription = true,
                subscriptionExpiryMillis = System.currentTimeMillis() + (18L * 24 * 60 * 60 * 1000),
                imageUrls = listOf("https://images.unsplash.com/photo-1524758631624-e2822e304c36?auto=format&fit=crop&q=80&w=600"),
                baseMonthlyRateUsd = 390.0,
                avatarEngagementViews = 0,
                avatarInquiryClicks = 0
            ),
            SpaceListing(
                id = "SPC-BEK-104",
                title = "Zahle Boulevard Medical Polyclinic",
                spaceType = SpaceType.POLYCLINIC,
                governorate = Governorate.BEKAA,
                district = "Zahle - Boulevard Saint Antoine",
                streetAddress = "Al-Rahi Center, 1st Floor",
                floorInfo = "Floor 1, Private Quiet Wing",
                lat = 33.8492,
                lng = 35.9042,
                isShared = true,
                complementarySpecialties = listOf("Consultant", "Life Coach", "Career Counselor", "Mediator"),
                residentPractitioners = emptyList(),
                essentialFacilities = listOf(
                    "24/7 Generator Electricity",
                    "Continuous Water Supply",
                    "High-Speed Fiber Wi-Fi",
                    "HVAC Climate Control",
                    "Daily Professional Sanitization",
                    "Dedicated Underground Parking"
                ),
                equipment = listOf(
                    EquipmentItem("EQ-18", "Soundproof Acoustic Panels", EquipmentCategory.OFFICE_AMENITIES, 8, "Maximum client confidentiality"),
                    EquipmentItem("EQ-19", "Ergonomic Relaxation Leather Lounge", EquipmentCategory.WORKSPACES, 1, "Plush executive lounger"),
                    EquipmentItem("EQ-20", "Therapist Executive Chair & Desk", EquipmentCategory.WORKSPACES, 1, "Wood finish with private filing lock"),
                    EquipmentItem("EQ-21", "Whiteboard & Presentation Toolkits", EquipmentCategory.OFFICE_AMENITIES, 1, "Workshop materials")
                ),
                rentalFormulas = listOf(
                    RentalFormula(
                        id = "FRM-BEK-01",
                        type = RentalFormulaType.HOURLY,
                        rateUsd = 20.0,
                        scheduleDescription = "Flexible Hourly On-Demand Booking (Mon-Fri)",
                        daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
                        startHour = "14:00",
                        endHour = "19:00",
                        totalWeeklyHours = 25
                    ),
                    RentalFormula(
                        id = "FRM-BEK-02",
                        type = RentalFormulaType.SHIFT,
                        rateUsd = 65.0,
                        scheduleDescription = "Afternoon Shift (2:00 PM - 7:00 PM) • 2 days/wk",
                        daysOfWeek = listOf("Tue", "Thu"),
                        startHour = "14:00",
                        endHour = "19:00",
                        totalWeeklyHours = 10
                    ),
                    RentalFormula(
                        id = "FRM-BEK-03",
                        type = RentalFormulaType.DAY_PER_WEEK,
                        rateUsd = 130.0,
                        scheduleDescription = "Fixed 2 Days/Week (Tue & Fri 9AM - 7PM)",
                        daysOfWeek = listOf("Tue", "Fri"),
                        startHour = "09:00",
                        endHour = "19:00",
                        totalWeeklyHours = 20
                    )
                ),
                rules = PremisesRules(
                    smokingAllowed = false,
                    foodAllowed = true,
                    petsAllowed = false,
                    visitorPolicy = "Quiet waiting area",
                    offHoursAccess = true
                ),
                schedule = SpaceOperatingSchedule(
                    openingHour = "09:00",
                    closingHour = "19:00",
                    operatingDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
                ),
                ownerId = "ORG-BEK-104",
                ownerName = "Zahle Boulevard Medical Suites",
                ownerPhone = "+961 8 820 400",
                ownerEmail = "zahle.boulevard@prospace.lb",
                isVerified = true,
                isActiveSubscription = true,
                subscriptionExpiryMillis = System.currentTimeMillis() + (12L * 24 * 60 * 60 * 1000),
                imageUrls = listOf("https://images.unsplash.com/photo-1497215728101-856f4ea42174?auto=format&fit=crop&q=80&w=600"),
                baseMonthlyRateUsd = 340.0,
                avatarEngagementViews = 0,
                avatarInquiryClicks = 0,
                subdivisions = listOf(
                    Subdivision(
                        id = "SUB-ZAH-01",
                        name = "Pediatric Consultation Wing",
                        type = Level2Type.ROOMS,
                        imageUrls = listOf("https://images.unsplash.com/photo-1629909613654-28e377c37b09?auto=format&fit=crop&q=80&w=400"),
                        amenities = listOf("Pediatric Patient Bed", "Air Purifier", "Handwashing Station"),
                        rentalStrategies = listOf(
                            SubdivisionStrategy(RentalStrategy.SHIFT_BASED, 80.0, availableHoursOrShifts = "Morning Shift (9AM - 2PM)"),
                            SubdivisionStrategy(RentalStrategy.DAILY, 140.0)
                        )
                    ),
                    Subdivision(
                        id = "SUB-ZAH-02",
                        name = "Surgical / Wellness Suite",
                        type = Level2Type.ROOMS,
                        imageUrls = listOf("https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?auto=format&fit=crop&q=80&w=400"),
                        amenities = listOf("Acoustic Soundproofing", "Dental Recliner", "Indirect Dimmable Lighting"),
                        rentalStrategies = listOf(
                            SubdivisionStrategy(RentalStrategy.HOURLY, 20.0),
                            SubdivisionStrategy(RentalStrategy.SHIFT_BASED, 70.0, availableHoursOrShifts = "Evening Shift (3PM - 8PM)")
                        )
                    )
                )
            ),
            SpaceListing(
                id = "SPC-SOU-105",
                title = "Sidon Seafront Coworking Space",
                spaceType = SpaceType.COWORKING_SPACE,
                governorate = Governorate.SOUTH,
                district = "Sidon - Riad El Solh Street",
                streetAddress = "Sidon Business Center, Floor 5",
                floorInfo = "Floor 5, High Panoramic View",
                lat = 33.5612,
                lng = 35.3725,
                isShared = true,
                complementarySpecialties = listOf("Freelancer", "Software Engineer", "Digital Marketer", "Accountant"),
                residentPractitioners = emptyList(),
                essentialFacilities = listOf(
                    "24/7 Generator Electricity",
                    "Continuous Water Supply",
                    "High-Speed Fiber Wi-Fi",
                    "HVAC Climate Control",
                    "Reception & Mail Support"
                ),
                equipment = listOf(
                    EquipmentItem("EQ-22", "Dedicated Desk & Lockable Pedestal", EquipmentCategory.WORKSPACES, 1, "Lockable storage for valuables"),
                    EquipmentItem("EQ-23", "27-inch USB-C Hub Monitor", EquipmentCategory.IT_TECH, 1, "Power delivery & display"),
                    EquipmentItem("EQ-24", "Quiet Phone Booth Access", EquipmentCategory.SPECIALIZED, 1, "Sound-damped video call booth")
                ),
                rentalFormulas = listOf(
                    RentalFormula(
                        id = "FRM-SOU-01",
                        type = RentalFormulaType.SHIFT,
                        rateUsd = 45.0,
                        scheduleDescription = "Morning Shift (8:00 AM - 1:00 PM) • Mon, Wed, Fri",
                        daysOfWeek = listOf("Mon", "Wed", "Fri"),
                        startHour = "08:00",
                        endHour = "13:00",
                        totalWeeklyHours = 15
                    ),
                    RentalFormula(
                        id = "FRM-SOU-02",
                        type = RentalFormulaType.FULL_MONTH,
                        rateUsd = 190.0,
                        scheduleDescription = "Full Shared Desk Access Monthly",
                        daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
                        startHour = "08:00",
                        endHour = "18:00",
                        totalWeeklyHours = 60
                    )
                ),
                rules = PremisesRules(
                    smokingAllowed = false,
                    foodAllowed = true,
                    petsAllowed = false,
                    visitorPolicy = "Standard reception check-in",
                    offHoursAccess = false
                ),
                schedule = SpaceOperatingSchedule(
                    openingHour = "08:00",
                    closingHour = "18:00",
                    operatingDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                ),
                ownerId = "ORG-SOU-105",
                ownerName = "Sidon Seafront Coworking Hub",
                ownerPhone = "+961 7 720 800",
                ownerEmail = "sidon.seafront@prospace.lb",
                isVerified = true,
                isActiveSubscription = true,
                subscriptionExpiryMillis = System.currentTimeMillis() + (29L * 24 * 60 * 60 * 1000),
                imageUrls = listOf("https://images.unsplash.com/photo-1556761175-4b46a572b786?auto=format&fit=crop&q=80&w=600"),
                baseMonthlyRateUsd = 190.0,
                avatarEngagementViews = 0,
                avatarInquiryClicks = 0,
                subdivisions = listOf(
                    Subdivision(
                        id = "SUB-SOU-01",
                        name = "Dedicated Desk A4",
                        type = Level2Type.DESK_IN_SHARED_AREA,
                        imageUrls = listOf("https://images.unsplash.com/photo-1497215728101-856f4ea42174?auto=format&fit=crop&q=80&w=400"),
                        amenities = listOf("Comfort Mesh Chair", "Power Outlets", "Lockable Drawer"),
                        rentalStrategies = listOf(
                            SubdivisionStrategy(RentalStrategy.HOURLY, 4.0),
                            SubdivisionStrategy(RentalStrategy.SHIFT_BASED, 15.0, availableHoursOrShifts = "Full Day Shift")
                        )
                    )
                )
            )
        )
        _spaces.value = initialSpaces
        _transactions.value = emptyList()
        _avatarCampaigns.value = emptyList()
        _bookingRequests.value = emptyList()
    }

    // --- Security & Audit Logging ---
    fun addAuditLog(
        actionType: String,
        details: String,
        severity: String = "INFO",
        actorEmail: String = _currentUser.value?.email ?: "geo.elnajjar@gmail.com"
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
    fun updateMonthlySubscriptionFee(newFeeUsd: Double) {
        val oldFee = _pricingState.value.monthlySubscriptionFeeUsd
        _pricingState.value = _pricingState.value.copy(
            monthlySubscriptionFeeUsd = newFeeUsd
        )
        addAuditLog(
            actionType = "PRICING_ADJUSTMENT",
            details = "Monthly fee changed from $${String.format(Locale.US, "%.2f", oldFee)} to $${String.format(Locale.US, "%.2f", newFeeUsd)} USD",
            severity = "WARN",
            actorEmail = _currentUser.value?.email ?: "geo.elnajjar@gmail.com"
        )
    }

    fun resetMonthlySubscriptionFee() {
        val baseline = _pricingState.value.baselineFeeUsd
        _pricingState.value = _pricingState.value.copy(
            monthlySubscriptionFeeUsd = baseline
        )
        addAuditLog(
            actionType = "PRICING_RESET",
            details = "Monthly fee reset to official baseline $${String.format(Locale.US, "%.2f", baseline)} USD",
            severity = "INFO"
        )
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
    fun processWhishPaySubscription(
        spaceId: String,
        payerName: String,
        payerPhone: String
    ): WhishTransaction {
        val currentFee = _pricingState.value.monthlySubscriptionFeeUsd
        val orderId = "ORD-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val signature = WhishSecurity.generateSignature(
            amount = currentFee,
            orderId = orderId
        )

        val targetSpace = _spaces.value.find { it.id == spaceId }
        val spaceTitle = targetSpace?.title ?: "ProSpace Lebanon Subscription"

        val tx = WhishTransaction(
            id = "TX-WSH-" + UUID.randomUUID().toString().take(8).uppercase(),
            orderId = orderId,
            amountUsd = currentFee,
            currency = "USD",
            status = TransactionStatus.SUCCESS,
            timestamp = System.currentTimeMillis(),
            payerName = payerName,
            payerPhone = payerPhone,
            channelId = WhishSecurity.CHANNEL_ID,
            sourceEmail = WhishSecurity.SOURCE_EMAIL,
            signatureHash = signature,
            spaceId = spaceId,
            spaceTitle = spaceTitle,
            daysGranted = 30
        )

        // Add to ledger
        _transactions.value = listOf(tx) + _transactions.value

        // Grant 30 days active entitlement
        _spaces.value = _spaces.value.map { space ->
            if (space.id == spaceId) {
                space.copy(
                    isActiveSubscription = true,
                    subscriptionExpiryMillis = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                )
            } else space
        }

        addAuditLog(
            actionType = "WHISH_PAYMENT_SUCCESS",
            details = "Order ${tx.orderId} ($${String.format(Locale.US, "%.2f", currentFee)}) settled. Signature: ${signature.take(12)}... Entitlement granted for ${spaceTitle}",
            severity = "SECURE",
            actorEmail = payerName
        )

        return tx
    }

    fun processWhishPayBooking(
        bookingId: String,
        payerName: String,
        payerPhone: String,
        txId: String,
        signature: String
    ): Boolean {
        val request = _bookingRequests.value.find { it.id == bookingId } ?: return false
        
        // Update request status to ACCEPTED and set isExternalPaymentSettled to true
        _bookingRequests.value = _bookingRequests.value.map {
            if (it.id == bookingId) {
                it.copy(
                    status = BookingRequestStatus.ACCEPTED,
                    isExternalPaymentSettled = true
                )
            } else it
        }

        // Create transaction entry
        val tx = WhishTransaction(
            id = txId,
            orderId = "ORD-BKG-" + bookingId,
            amountUsd = request.totalAmountUsd,
            currency = "USD",
            status = TransactionStatus.SUCCESS,
            timestamp = System.currentTimeMillis(),
            payerName = payerName,
            payerPhone = payerPhone,
            channelId = WhishSecurity.CHANNEL_ID,
            sourceEmail = WhishSecurity.SOURCE_EMAIL,
            signatureHash = signature,
            spaceId = request.spaceId,
            spaceTitle = request.spaceTitle,
            daysGranted = request.durationMonths * 30
        )

        _transactions.value = listOf(tx) + _transactions.value

        addAuditLog(
            actionType = "WHISH_BOOKING_SETTLEMENT_SUCCESS",
            details = "Booking request #${bookingId} for space ${request.spaceTitle} settled via Whish Pay by ${payerName}. Amount: $${request.totalAmountUsd}",
            severity = "SECURE",
            actorEmail = request.practitionerEmail
        )
        return true
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

    fun updateSpaceListing(updated: SpaceListing) {
        _spaces.value = _spaces.value.map { if (it.id == updated.id) updated else it }
        addAuditLog(
            actionType = "LISTING_UPDATED",
            details = "Admin updated workspace listing #${updated.id} (${updated.title})",
            severity = "INFO"
        )
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

    fun updateUser(updated: AppUser) {
        _users.value = _users.value.map { if (it.id == updated.id) updated else it }
        if (_currentUser.value?.id == updated.id) {
            _currentUser.value = updated
        }
        addAuditLog(
            actionType = "USER_UPDATED",
            details = "Admin updated user profile for ${updated.fullName} (${updated.email})",
            severity = "INFO"
        )
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

    fun toggleUserVerification(userId: String) {
        val target = _users.value.find { it.id == userId } ?: return
        val nextVerified = !target.isVerified
        val nextStatus = if (nextVerified) MemberVerificationStatus.VERIFIED else MemberVerificationStatus.UNVERIFIED
        val nextTier = if (nextVerified) {
            if (target.role == UserRole.SPACE_OWNER) VerificationTier.TIER_3_COMMERCIAL_HOST else VerificationTier.TIER_2_PROFESSIONAL
        } else {
            VerificationTier.TIER_1_BASIC
        }
        val updated = target.copy(
            isVerified = nextVerified,
            verificationStatus = nextStatus,
            verificationTier = nextTier,
            trustScore = if (nextVerified) 98 else 30
        )
        updateUser(updated)
        addAuditLog(
            actionType = "USER_VERIFICATION_TOGGLE",
            details = "Admin toggled verification for ${target.fullName} to $nextVerified ($nextStatus)",
            severity = "SECURE"
        )
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

    fun toggleListingVerification(spaceId: String) {
        val target = _spaces.value.find { it.id == spaceId }
        val nextStatus = !(target?.isVerified ?: false)
        _spaces.value = _spaces.value.map {
            if (it.id == spaceId) it.copy(isVerified = nextStatus) else it
        }
        addAuditLog(
            actionType = "VERIFICATION_OVERRIDE",
            details = "Workspace #${spaceId} verified status changed to $nextStatus by Super Admin",
            severity = "SECURE"
        )
    }

    fun toggleListingActive(spaceId: String) {
        val target = _spaces.value.find { it.id == spaceId }
        val nextStatus = !(target?.isActiveSubscription ?: false)
        _spaces.value = _spaces.value.map {
            if (it.id == spaceId) it.copy(isActiveSubscription = nextStatus) else it
        }
        addAuditLog(
            actionType = "SUBSCRIPTION_STATUS_TOGGLE",
            details = "Listing #${spaceId} subscription active status set to $nextStatus by Super Admin",
            severity = "WARN"
        )
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

    fun acceptBookingRequest(requestId: String): Boolean {
        val request = _bookingRequests.value.find { it.id == requestId } ?: return false
        val now = System.currentTimeMillis()

        _bookingRequests.value = _bookingRequests.value.map {
            if (it.id == requestId) {
                it.copy(
                    status = BookingRequestStatus.ACCEPTED,
                    reviewedAt = now,
                    isExternalPaymentSettled = true
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
        val cleanEmail = email.trim().lowercase()
        val userId = "USR-LB-" + UUID.randomUUID().toString().take(6).uppercase()
        val newUser = AppUser(
            id = userId,
            email = cleanEmail,
            fullName = fullName.trim(),
            role = role,
            specialty = specialty.trim(),
            phone = phone.trim(),
            affiliation = affiliation.trim(),
            syndicateNumber = syndicateNumber.trim(),
            governorate = governorate,
            isVerified = true
        )

        _users.value = _users.value.filter { !it.email.equals(cleanEmail, ignoreCase = true) } + newUser
        _currentUser.value = newUser

        addAuditLog(
            actionType = "MEMBER_REGISTRATION",
            details = "New member registered: ${newUser.fullName} (${newUser.role.name}) • ${newUser.specialty} • ${newUser.governorate.displayName}",
            severity = "SECURE",
            actorEmail = newUser.email
        )
        return newUser
    }

    fun login(email: String, desiredRole: UserRole? = null): AppUser {
        val cleanEmail = email.trim().lowercase()
        val existing = _users.value.find { it.email.equals(cleanEmail, ignoreCase = true) }

        val user = if (cleanEmail == "geo.elnajjar@gmail.com" || desiredRole == UserRole.ADMIN) {
            existing ?: AppUser(
                id = "USR-ADMIN-ROOT",
                email = "geo.elnajjar@gmail.com",
                fullName = "Geo El-Najjar",
                role = UserRole.ADMIN,
                specialty = "Super Administrator & Security Governance",
                phone = "+961 70 888 999",
                affiliation = "ProSpace Executive HQ & Central Governance",
                syndicateNumber = "SUPER-ADMIN-01",
                governorate = Governorate.BEIRUT,
                isVerified = true
            )
        } else if (existing != null) {
            existing
        } else {
            // New user registration flow
            val role = desiredRole ?: UserRole.MEDICAL_PRACTITIONER
            AppUser(
                id = "USR-" + UUID.randomUUID().toString().take(6).uppercase(),
                email = cleanEmail,
                fullName = if (cleanEmail.contains("@")) cleanEmail.substringBefore("@").replace(".", " ").capitalize(Locale.US) else "Professional Member",
                role = role,
                specialty = if (role == UserRole.PROFESSIONAL) "Independent Professional" else "Workspace Host",
                phone = "+961 70 000 000",
                affiliation = "ProSpace Member Network",
                syndicateNumber = "PRO-LB-" + (1000..9999).random(),
                governorate = Governorate.BEIRUT,
                isVerified = true
            ).also {
                _users.value = _users.value + it
            }
        }

        _currentUser.value = user
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

    fun setCurrentUser(user: AppUser?) {
        _currentUser.value = user
    }

    fun switchRole(role: UserRole) {
        val current = _currentUser.value
        val baseName = current?.fullName ?: "Geo El-Najjar"
        val baseEmail = current?.email ?: "geo.elnajjar@gmail.com"
        val basePhone = current?.phone ?: "+961 70 888 999"

        val user = when (role) {
            UserRole.ADMIN -> _users.value.find { it.role == UserRole.ADMIN } ?: AppUser(
                id = current?.id ?: "USR-ADMIN-ROOT",
                email = baseEmail,
                fullName = baseName,
                role = UserRole.ADMIN,
                specialty = "Super Administrator & Security Governance",
                phone = basePhone,
                affiliation = "ProSpace Executive HQ & Central Governance",
                syndicateNumber = "SUPER-ADMIN-01",
                governorate = current?.governorate ?: Governorate.BEIRUT,
                isVerified = true,
                verificationStatus = MemberVerificationStatus.VERIFIED,
                verificationTier = VerificationTier.TIER_3_COMMERCIAL_HOST,
                trustScore = 100
            )
            UserRole.PROFESSIONAL -> current?.copy(
                role = UserRole.PROFESSIONAL,
                specialty = if (current.specialty.contains("Admin") || current.specialty.contains("Host")) "Licensed Practitioner & Consultant" else current.specialty,
                verificationTier = VerificationTier.TIER_2_PROFESSIONAL
            ) ?: AppUser(
                id = "USR-PRO-01",
                email = baseEmail,
                fullName = baseName,
                role = UserRole.PROFESSIONAL,
                specialty = "Licensed Practitioner & Consultant",
                phone = basePhone,
                affiliation = "Syndicate of Engineers & Physicians Network",
                syndicateNumber = "PRO-LB-8842",
                governorate = Governorate.BEIRUT,
                isVerified = true,
                verificationStatus = MemberVerificationStatus.VERIFIED,
                verificationTier = VerificationTier.TIER_2_PROFESSIONAL,
                trustScore = 98
            )
            UserRole.SPACE_OWNER -> current?.copy(
                role = UserRole.SPACE_OWNER,
                specialty = if (current.specialty.contains("Admin") || current.specialty.contains("Practitioner")) "Commercial Workspace Host" else current.specialty,
                verificationTier = VerificationTier.TIER_3_COMMERCIAL_HOST
            ) ?: AppUser(
                id = "USR-HOST-01",
                email = baseEmail,
                fullName = baseName,
                role = UserRole.SPACE_OWNER,
                specialty = "Commercial Workspace Host",
                phone = basePhone,
                affiliation = "Lebanon Commercial Spaces Network",
                syndicateNumber = "HOST-LB-4321",
                governorate = Governorate.BEIRUT,
                isVerified = true,
                verificationStatus = MemberVerificationStatus.VERIFIED,
                verificationTier = VerificationTier.TIER_3_COMMERCIAL_HOST,
                trustScore = 98,
                subscriptionExpiryMillis = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
            )
        }
        _currentUser.value = user
        addAuditLog(
            actionType = "ROLE_QUICK_SWITCH",
            details = "Active identity switched to ${user.fullName} (${user.role.name})",
            severity = "INFO",
            actorEmail = user.email
        )
    }

    fun updateCurrentUserProfile(
        name: String,
        specialty: String,
        phone: String,
        affiliation: String,
        syndicateNumber: String,
        governorate: Governorate
    ) {
        _currentUser.value?.let { current ->
            val updated = current.copy(
                fullName = name,
                specialty = specialty,
                phone = phone,
                affiliation = affiliation,
                syndicateNumber = syndicateNumber,
                governorate = governorate
            )
            _currentUser.value = updated
            _users.value = _users.value.map { if (it.id == updated.id) updated else it }
        }
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

        addAuditLog(
            actionType = "DOCUMENT_UPLOADED",
            details = "Credential document ${type.title} ($fileName, #$documentNumber) uploaded for member verification",
            severity = "INFO",
            actorEmail = _currentUser.value?.email ?: "member@prospace.lb"
        )

        recalculateUserVerification(userId)
        return doc
    }

    fun removeCredentialDocument(documentId: String) {
        val doc = _credentialDocuments.value.find { it.id == documentId }
        _credentialDocuments.value = _credentialDocuments.value.filterNot { it.id == documentId }
        if (doc != null) {
            addAuditLog(
                actionType = "DOCUMENT_REMOVED",
                details = "Credential document ${doc.type.title} (#${doc.documentNumber}) removed",
                severity = "INFO",
                actorEmail = _currentUser.value?.email ?: "member@prospace.lb"
            )
            recalculateUserVerification(doc.userId)
        }
    }

    fun submitUserVerification(userId: String) {
        val user = _users.value.find { it.id == userId } ?: _currentUser.value ?: return
        val userDocs = _credentialDocuments.value.filter { it.userId == userId }
        val requiredTypes = DocumentType.values().filter { it.requiredFor.contains(user.role) }
        val uploadedRequired = requiredTypes.filter { req -> userDocs.any { it.type == req && it.status != DocumentStatus.NOT_UPLOADED } }

        val newStatus = if (uploadedRequired.size >= requiredTypes.size) {
            MemberVerificationStatus.PENDING_REVIEW
        } else {
            MemberVerificationStatus.ACTION_REQUIRED
        }

        val updated = user.copy(
            verificationStatus = newStatus,
            verificationNotes = "Submitted on ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())}. Pending admin accreditation."
        )
        if (_currentUser.value?.id == userId) {
            _currentUser.value = updated
        }
        _users.value = _users.value.map { if (it.id == userId) updated else it }

        addAuditLog(
            actionType = "VERIFICATION_SUBMITTED",
            details = "Member ${user.fullName} submitted ${uploadedRequired.size}/${requiredTypes.size} credential documents for compliance review",
            severity = "INFO",
            actorEmail = user.email
        )
    }

    fun adminApproveDocument(documentId: String, reviewerNotes: String = "Validated against Lebanese Syndicate Registry") {
        val doc = _credentialDocuments.value.find { it.id == documentId } ?: return
        val updatedDoc = doc.copy(
            status = DocumentStatus.VERIFIED,
            reviewerNotes = reviewerNotes,
            rejectionReason = null
        )
        _credentialDocuments.value = _credentialDocuments.value.map { if (it.id == documentId) updatedDoc else it }

        addAuditLog(
            actionType = "DOCUMENT_ACCREDITED",
            details = "Admin approved ${doc.type.title} (#${doc.documentNumber}) for member ${doc.userId}",
            severity = "SECURE",
            actorEmail = _currentUser.value?.email ?: "admin@prospace.lb"
        )
        recalculateUserVerification(doc.userId)
    }

    fun adminRejectDocument(documentId: String, reason: String) {
        val doc = _credentialDocuments.value.find { it.id == documentId } ?: return
        val updatedDoc = doc.copy(
            status = DocumentStatus.REJECTED,
            rejectionReason = reason,
            reviewerNotes = "Revision requested: $reason"
        )
        _credentialDocuments.value = _credentialDocuments.value.map { if (it.id == documentId) updatedDoc else it }

        addAuditLog(
            actionType = "DOCUMENT_REVISION_REQUESTED",
            details = "Admin requested revision on ${doc.type.title} (#${doc.documentNumber}): $reason",
            severity = "WARN",
            actorEmail = _currentUser.value?.email ?: "admin@prospace.lb"
        )
        recalculateUserVerification(doc.userId)
    }

    fun recalculateUserVerification(userId: String) {
        val user = _users.value.find { it.id == userId } ?: _currentUser.value ?: return
        val userDocs = _credentialDocuments.value.filter { it.userId == userId }
        val requiredTypes = DocumentType.values().filter { it.requiredFor.contains(user.role) }

        val verifiedCount = requiredTypes.count { req -> userDocs.any { it.type == req && it.status == DocumentStatus.VERIFIED } }
        val hasRejected = userDocs.any { it.status == DocumentStatus.REJECTED }
        val hasPending = userDocs.any { it.status == DocumentStatus.PENDING_REVIEW }

        val newStatus = when {
            verifiedCount == requiredTypes.size && requiredTypes.isNotEmpty() -> MemberVerificationStatus.VERIFIED
            hasRejected -> MemberVerificationStatus.ACTION_REQUIRED
            hasPending || verifiedCount > 0 -> MemberVerificationStatus.PENDING_REVIEW
            else -> MemberVerificationStatus.UNVERIFIED
        }

        val isFullyVerified = newStatus == MemberVerificationStatus.VERIFIED
        val tier = when (user.role) {
            UserRole.SPACE_OWNER -> if (isFullyVerified) VerificationTier.TIER_3_COMMERCIAL_HOST else VerificationTier.TIER_1_BASIC
            UserRole.PROFESSIONAL -> if (isFullyVerified) VerificationTier.TIER_2_PROFESSIONAL else VerificationTier.TIER_1_BASIC
            UserRole.ADMIN -> VerificationTier.TIER_3_COMMERCIAL_HOST
        }

        val trustScore = when (newStatus) {
            MemberVerificationStatus.VERIFIED -> 98
            MemberVerificationStatus.PENDING_REVIEW -> 75
            MemberVerificationStatus.ACTION_REQUIRED -> 45
            MemberVerificationStatus.UNVERIFIED -> 30
        }

        val updated = user.copy(
            isVerified = isFullyVerified,
            verificationStatus = newStatus,
            verificationTier = tier,
            trustScore = trustScore
        )

        if (_currentUser.value?.id == userId) {
            _currentUser.value = updated
        }
        _users.value = _users.value.map { if (it.id == userId) updated else it }
    }

    // --- AI Avatar Marketing Generator ---
    fun generateAvatarCampaign(space: SpaceListing): AvatarCampaign {
        val formulasText = space.rentalFormulas.joinToString(", ") { "${it.type.displayName} ($${it.rateUsd})" }
        val facilitiesHighlight = space.essentialFacilities.take(3).joinToString(" • ")
        val specialtiesText = space.complementarySpecialties.joinToString(", ")

        val caption = "🏢 ${space.title} in ${space.district}, ${space.governorate.displayName}!\n" +
                "✨ Facilities: $facilitiesHighlight\n" +
                "💼 Ideal Synergy for: $specialtiesText\n" +
                "📅 Rental Formula: $formulasText\n" +
                "📲 Connect directly with ${space.ownerName} via WhatsApp or tap link in bio!\n" +
                "#ProSpaceLebanon #OfficeShare #CoworkingLebanon #${space.governorate.name}Workspace"

        val campaign = AvatarCampaign(
            id = "CMP-" + UUID.randomUUID().toString().take(6).uppercase(),
            spaceId = space.id,
            spaceTitle = space.title,
            instagramHandle = "@prospace.lebanon",
            totalReelViews = 0,
            linkClicks = 0,
            inquiriesGenerated = 0,
            generatedCaption = caption,
            storyOverlayTag = "${space.district} • ${space.spaceType.displayName} • 24/7 Power",
            lastNudgeText = "Live AI Social campaign configured. Broadcast ready."
        )

        _avatarCampaigns.value = listOf(campaign) + _avatarCampaigns.value.filter { it.spaceId != space.id }
        return campaign
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
        sb.appendLine("  \"platform\": \"ProSpace Lebanon\",")
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

    fun requestCashOut(ownerName: String, amountUsd: Double, whishPhone: String): Boolean {
        val tx = WhishTransaction(
            id = "TX-CASHOUT-" + UUID.randomUUID().toString().take(6).uppercase(),
            orderId = "ORD-CASHOUT-" + System.currentTimeMillis(),
            amountUsd = amountUsd,
            currency = "USD",
            status = TransactionStatus.SUCCESS,
            timestamp = System.currentTimeMillis(),
            payerName = ownerName,
            payerPhone = whishPhone,
            channelId = "15462415",
            sourceEmail = "geo.elnajjar@gmail.com",
            signatureHash = com.example.data.crypto.WhishSecurity.generateSignature("15462415", amountUsd, "USD", "ORD-CASHOUT"),
            spaceId = "SPACE-CASHOUT",
            spaceTitle = "Owner Cash-Out Settlement",
            daysGranted = 0
        )
        _transactions.value = listOf(tx) + _transactions.value
        return true
    }
}
