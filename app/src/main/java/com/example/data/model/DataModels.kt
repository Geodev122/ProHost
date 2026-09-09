package com.example.data.model

import com.example.data.config.MerchantConfig
import java.util.UUID

enum class SpaceType(val displayName: String, val iconName: String) {
    PRIVATE_OFFICE("Private Office", "Apartment"),
    CENTER("Center", "Business"),
    POLYCLINIC("Polyclinic", "LocalHospital"),
    COWORKING_SPACE("Co-working Space", "Groups")
}

enum class Level2Type(val displayName: String, val iconName: String) {
    ROOMS("Room", "MeetingRoom"),
    CONFERENCE_ROOM("Conference Room", "CoPresent"),
    THEATER_TRAINING("Theater / Training Room", "School"),
    DESK_IN_SHARED_AREA("Desk in Shared Area", "Desk")
}

enum class RentalStrategy(val displayName: String) {
    HOURLY("Hourly"),
    SHIFT_BASED("Shift-based"),
    DAILY("Daily / Per Day"),
    MONTHLY("Monthly")
}

data class SubdivisionStrategy(
    val strategy: RentalStrategy,
    val rateUsd: Double,
    val minDuration: Int = 1,
    val availableHoursOrShifts: String = "" // e.g. "08:00 - 13:00" or "Full Day"
)

data class Subdivision(
    val id: String = "SUB-" + java.util.UUID.randomUUID().toString().take(6).uppercase(),
    val name: String,
    val type: Level2Type,
    val imageUrls: List<String> = emptyList(),
    val amenities: List<String> = emptyList(),
    val rentalStrategies: List<SubdivisionStrategy> = emptyList()
)

enum class Governorate(val displayName: String, val centerLat: Double, val centerLng: Double) {
    BEIRUT("Beirut", 33.8938, 35.5018),
    MOUNT_LEBANON("Mount Lebanon", 33.8333, 35.6000),
    NORTH("North Lebanon (Tripoli/Batroun)", 34.4367, 35.8497),
    SOUTH("South Lebanon (Sidon/Tyre)", 33.5631, 35.3689),
    BEKAA("Bekaa (Zahle/Chtaura)", 33.8463, 35.9020),
    NABATIEH("Nabatieh", 33.3772, 35.4839)
}

enum class EquipmentCategory(val displayName: String) {
    WORKSPACES("Workspace & Furniture"),
    IT_TECH("IT, Tech & Presentation"),
    OFFICE_AMENITIES("Office Amenities"),
    SPECIALIZED("Specialized Tools & Equipment")
}

data class EquipmentItem(
    val id: String,
    val name: String,
    val category: EquipmentCategory,
    val quantity: Int = 1,
    val description: String = ""
)

enum class RentalFormulaType(val displayName: String) {
    FULL_MONTH("Full Month (Exclusive)"),
    SHIFT("Shift / Time-Slot Basis"),
    DAY_PER_WEEK("Day-per-Week Basis"),
    HOURLY("Half-Day / Hourly Slot")
}

data class RentalFormula(
    val id: String = "FRM-" + java.util.UUID.randomUUID().toString().take(6).uppercase(),
    val type: RentalFormulaType,
    val rateUsd: Double,
    val scheduleDescription: String,
    val daysOfWeek: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
    val startHour: String = "08:00",
    val endHour: String = "14:00",
    val totalWeeklyHours: Int = 30,
    val daysCountRequired: Int = 1, // e.g. For Day-per-Week: 1 day/wk, 2 days/wk
    val minHours: Int = 2, // For Hourly: min booking hours
    val shiftName: String = "Morning Shift" // e.g. Morning Shift, Afternoon Shift, Full Day
)

data class BlackoutSlot(
    val id: String = "BLK-" + java.util.UUID.randomUUID().toString().take(6).uppercase(),
    val dayOfWeek: String, // e.g. "Sunday", "Friday"
    val startTime: String = "18:00",
    val endTime: String = "22:00",
    val reason: String = "Workspace Closed / Maintenance"
)

data class SpaceOperatingSchedule(
    val openingHour: String = "08:00",
    val closingHour: String = "20:00",
    val operatingDays: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
    val blackoutSlots: List<BlackoutSlot> = emptyList(),
    val isSundayOperating: Boolean = false
)

enum class BookingRequestStatus(val displayName: String) {
    PENDING("Pending Owner Approval"),
    ACCEPTED("Accepted & Space Rented"),
    REJECTED("Declined"),
    CANCELLED("Cancelled by Professional")
}

/**
 * Reason codes for cancelling an already-ACCEPTED booking (early termination) —
 * distinct from a PENDING request simply being withdrawn before host review,
 * which needs no reason. Deliberately simple (a fixed code + optional free-text
 * note), per the current terms-of-use cancellation workflow: no in-app refund or
 * penalty logic, since no rent settlement happens in-app either.
 */
enum class CancellationReasonCode(val displayName: String) {
    SCHEDULE_CONFLICT("Schedule / Availability Conflict"),
    FOUND_ALTERNATIVE_SPACE("Found an Alternative Space"),
    PRACTICE_RELOCATION_OR_CLOSURE("Practice Relocation or Closure"),
    PROPERTY_CONDITION_ISSUE("Property Condition Issue"),
    MUTUAL_AGREEMENT("Mutual Agreement Between Parties"),
    OTHER("Other")
}

/**
 * Firestore Data Model: BookingRequest
 * Collection: "booking_requests"
 * Stores rental formula, selected date/time range, and booking lifecycle status.
 */
data class BookingRequest(
    val id: String = "BRQ-" + java.util.UUID.randomUUID().toString().take(6).uppercase(),
    val spaceId: String,
    val spaceTitle: String,
    val spaceDistrict: String = "Beirut",
    val governorate: Governorate = Governorate.BEIRUT,
    val ownerId: String,
    val ownerName: String,
    val ownerPhone: String = "",
    val practitionerId: String,
    val practitionerName: String,
    val practitionerEmail: String = "",
    val practitionerPhone: String = "",
    val practitionerSpecialty: String = "Professional Practice",
    val formula: RentalFormula,
    val startDate: String, // e.g. "2026-09-01"
    val endDate: String = "", // e.g. "2026-10-01"
    val selectedDays: List<String> = emptyList(), // Specific days chosen by professional from owner's available days
    val selectedStartHour: String = "", // Chosen start time
    val selectedEndHour: String = "", // Chosen end time
    val selectedShift: String = "", // e.g. "Morning Shift (08:00 - 13:00)"
    val selectedDateTimeRange: String = "", // e.g. "Every Wednesday 08:00 - 18:00 (Starting 2026-09-01, 3 Months)"
    val durationMonths: Int = 1,
    val totalAmountUsd: Double = 0.0,
    val clinicalNotes: String = "",
    val status: BookingRequestStatus = BookingRequestStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null,
    val rejectionReason: String? = null,
    val subdivisionId: String? = null,
    val subdivisionName: String? = null,
    val selectedStrategy: String? = null,
    // Set by the practitioner when submitting an edit to an already-ACCEPTED
    // booking (see MyBookingsScreen's "Edit Booking" action) — this new request
    // goes through the normal PENDING -> host-review cycle like any other, but if
    // the host accepts it, ProHostRepository.acceptBookingRequest releases the
    // booking named here (marks it CANCELLED) in the same operation, so exactly
    // one of the two is ever ACCEPTED at a time.
    val replacesBookingId: String? = null,
    // The signed lease the Pro Host uploads (Firebase Storage) when finalizing
    // acceptance — see OwnerRentalRequestsScreen's Accept flow. This, not any
    // in-app payment flag, is now the record that host and specialist reached and
    // evidenced a real agreement; nobody at ProHost reviews it, it's kept on file.
    // The Specialist's Digital Key Pass (MyBookingsScreen) links here.
    val agreementUrl: String? = null,
    // Early-termination record — set only when an already-ACCEPTED booking is
    // cancelled (as opposed to a PENDING request simply withdrawn/declined, which
    // needs neither). See ProHostRepository.cancelAcceptedBooking's doc comment.
    val cancellationReasonCode: String? = null,
    val cancellationNote: String? = null,
    val cancelledByRole: String? = null
) {
    val isPending: Boolean get() = status == BookingRequestStatus.PENDING
    val isAccepted: Boolean get() = status == BookingRequestStatus.ACCEPTED
    val isRejected: Boolean get() = status == BookingRequestStatus.REJECTED

    /**
     * Converts to Firestore Document Map for NoSQL cloud persistence
     */
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "spaceId" to spaceId,
            "spaceTitle" to spaceTitle,
            "spaceDistrict" to spaceDistrict,
            "governorate" to governorate.name,
            "ownerId" to ownerId,
            "ownerName" to ownerName,
            "ownerPhone" to ownerPhone,
            "practitionerId" to practitionerId,
            "practitionerName" to practitionerName,
            "practitionerEmail" to practitionerEmail,
            "practitionerPhone" to practitionerPhone,
            "practitionerSpecialty" to practitionerSpecialty,
            "formula" to mapOf(
                "id" to formula.id,
                "type" to formula.type.name,
                "rateUsd" to formula.rateUsd,
                "scheduleDescription" to formula.scheduleDescription,
                "daysOfWeek" to formula.daysOfWeek,
                "startHour" to formula.startHour,
                "endHour" to formula.endHour,
                "totalWeeklyHours" to formula.totalWeeklyHours,
                "daysCountRequired" to formula.daysCountRequired,
                "minHours" to formula.minHours,
                "shiftName" to formula.shiftName
            ),
            "startDate" to startDate,
            "endDate" to endDate,
            "selectedDays" to selectedDays,
            "selectedStartHour" to selectedStartHour,
            "selectedEndHour" to selectedEndHour,
            "selectedShift" to selectedShift,
            "selectedDateTimeRange" to if (selectedDateTimeRange.isNotBlank()) selectedDateTimeRange else "$startDate (${formula.daysOfWeek.joinToString()} ${formula.startHour}-${formula.endHour})",
            "durationMonths" to durationMonths,
            "totalAmountUsd" to totalAmountUsd,
            "clinicalNotes" to clinicalNotes,
            "status" to status.name,
            "createdAt" to createdAt,
            "reviewedAt" to reviewedAt,
            "rejectionReason" to rejectionReason,
            "subdivisionId" to subdivisionId,
            "subdivisionName" to subdivisionName,
            "selectedStrategy" to selectedStrategy,
            "replacesBookingId" to replacesBookingId,
            "agreementUrl" to agreementUrl,
            "cancellationReasonCode" to cancellationReasonCode,
            "cancellationNote" to cancellationNote,
            "cancelledByRole" to cancelledByRole
        )
    }

    companion object {
        const val COLLECTION_PATH = "booking_requests"

        fun fromFirestoreMap(docId: String, data: Map<String, Any?>): BookingRequest {
            val formulaMap = data["formula"] as? Map<String, Any?> ?: emptyMap()
            val formulaTypeStr = formulaMap["type"] as? String ?: RentalFormulaType.FULL_MONTH.name
            val formulaType = try { RentalFormulaType.valueOf(formulaTypeStr) } catch(e: Exception) { RentalFormulaType.FULL_MONTH }

            val formula = RentalFormula(
                id = formulaMap["id"] as? String ?: ("FRM-" + docId.take(4)),
                type = formulaType,
                rateUsd = (formulaMap["rateUsd"] as? Number)?.toDouble() ?: 500.0,
                scheduleDescription = formulaMap["scheduleDescription"] as? String ?: "Standard Formula",
                daysOfWeek = (formulaMap["daysOfWeek"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
                startHour = formulaMap["startHour"] as? String ?: "08:00",
                endHour = formulaMap["endHour"] as? String ?: "14:00",
                totalWeeklyHours = (formulaMap["totalWeeklyHours"] as? Number)?.toInt() ?: 30,
                daysCountRequired = (formulaMap["daysCountRequired"] as? Number)?.toInt() ?: 1,
                minHours = (formulaMap["minHours"] as? Number)?.toInt() ?: 2,
                shiftName = formulaMap["shiftName"] as? String ?: "Morning Shift"
            )

            val govStr = data["governorate"] as? String ?: Governorate.BEIRUT.name
            val gov = try { Governorate.valueOf(govStr) } catch(e: Exception) { Governorate.BEIRUT }

            val statusStr = data["status"] as? String ?: BookingRequestStatus.PENDING.name
            val status = try { BookingRequestStatus.valueOf(statusStr) } catch(e: Exception) { BookingRequestStatus.PENDING }

            val daysList = (data["selectedDays"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            return BookingRequest(
                id = docId,
                spaceId = data["spaceId"] as? String ?: "",
                spaceTitle = data["spaceTitle"] as? String ?: "",
                spaceDistrict = data["spaceDistrict"] as? String ?: "Beirut",
                governorate = gov,
                ownerId = data["ownerId"] as? String ?: "",
                ownerName = data["ownerName"] as? String ?: "",
                ownerPhone = data["ownerPhone"] as? String ?: "",
                practitionerId = data["practitionerId"] as? String ?: "",
                practitionerName = data["practitionerName"] as? String ?: "",
                practitionerEmail = data["practitionerEmail"] as? String ?: "",
                practitionerPhone = data["practitionerPhone"] as? String ?: "",
                practitionerSpecialty = data["practitionerSpecialty"] as? String ?: "Specialist",
                formula = formula,
                startDate = data["startDate"] as? String ?: "",
                endDate = data["endDate"] as? String ?: "",
                selectedDays = daysList,
                selectedStartHour = data["selectedStartHour"] as? String ?: "",
                selectedEndHour = data["selectedEndHour"] as? String ?: "",
                selectedShift = data["selectedShift"] as? String ?: "",
                selectedDateTimeRange = data["selectedDateTimeRange"] as? String ?: "",
                durationMonths = (data["durationMonths"] as? Number)?.toInt() ?: 1,
                totalAmountUsd = (data["totalAmountUsd"] as? Number)?.toDouble() ?: 0.0,
                clinicalNotes = data["clinicalNotes"] as? String ?: "",
                status = status,
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                reviewedAt = (data["reviewedAt"] as? Number)?.toLong(),
                rejectionReason = data["rejectionReason"] as? String,
                subdivisionId = data["subdivisionId"] as? String,
                subdivisionName = data["subdivisionName"] as? String,
                selectedStrategy = data["selectedStrategy"] as? String,
                replacesBookingId = data["replacesBookingId"] as? String,
                agreementUrl = data["agreementUrl"] as? String,
                cancellationReasonCode = data["cancellationReasonCode"] as? String,
                cancellationNote = data["cancellationNote"] as? String,
                cancelledByRole = data["cancelledByRole"] as? String
            )
        }
    }
}

typealias RentalBookingRequest = BookingRequest

data class PremisesRules(
    val smokingAllowed: Boolean = false,
    val foodAllowed: Boolean = true,
    val petsAllowed: Boolean = false,
    val visitorPolicy: String = "Clients & visitors welcomed in reception lounge",
    val offHoursAccess: Boolean = true,
    val sharedAmenities: List<String> = listOf("Receptionist Desk", "Client Waiting Lounge", "Kitchenette / Coffee Bar", "Restroom & High-Speed Wi-Fi")
)

data class SpaceListing(
    val id: String,
    val title: String,
    val spaceType: SpaceType,
    val governorate: Governorate,
    val district: String,
    val streetAddress: String,
    val floorInfo: String,
    val lat: Double,
    val lng: Double,
    val isShared: Boolean,
    val complementarySpecialties: List<String>,
    val residentPractitioners: List<String>,
    val essentialFacilities: List<String>,
    val equipment: List<EquipmentItem>,
    val rentalFormulas: List<RentalFormula>,
    val rules: PremisesRules,
    val schedule: SpaceOperatingSchedule = SpaceOperatingSchedule(),
    val ownerId: String,
    val ownerName: String,
    val ownerPhone: String,
    val ownerEmail: String,
    // Proof of ownership / right to rent this specific space out, uploaded by the
    // Pro Host at listing-creation time. Kept on file — nobody reviews/approves it,
    // there is no admin accreditation workflow anymore (see AppUser's doc comment).
    val ownershipProofUrl: String? = null,
    val isVerified: Boolean = true,
    val isActiveSubscription: Boolean = true,
    // Mirrored by setAccountSuspended.ts (Admin SDK) onto every listing this owner
    // has when their account is suspended/reactivated — server-only, never in
    // toFirestoreMap(). Hides the listing from public Discovery (firestore.rules)
    // while still letting the owner and Admin see it (with an explanatory badge).
    val isOwnerSuspended: Boolean = false,
    val subscriptionExpiryMillis: Long = System.currentTimeMillis() + (28L * 24 * 60 * 60 * 1000),
    val imageUrls: List<String> = emptyList(),
    val videoTourDurationSec: Int = 10,
    val baseMonthlyRateUsd: Double = 450.0,
    val avatarEngagementViews: Int = 0,
    val avatarInquiryClicks: Int = 0,
    val subdivisions: List<Subdivision> = emptyList()
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "title" to title,
            "spaceType" to spaceType.name,
            "governorate" to governorate.name,
            "district" to district,
            "streetAddress" to streetAddress,
            "floorInfo" to floorInfo,
            "lat" to lat,
            "lng" to lng,
            "isShared" to isShared,
            "complementarySpecialties" to complementarySpecialties,
            "residentPractitioners" to residentPractitioners,
            "essentialFacilities" to essentialFacilities,
            "equipment" to equipment.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "category" to it.category.name,
                    "quantity" to it.quantity,
                    "description" to it.description
                )
            },
            "rentalFormulas" to rentalFormulas.map {
                mapOf(
                    "id" to it.id,
                    "type" to it.type.name,
                    "rateUsd" to it.rateUsd,
                    "scheduleDescription" to it.scheduleDescription,
                    "daysOfWeek" to it.daysOfWeek,
                    "startHour" to it.startHour,
                    "endHour" to it.endHour,
                    "totalWeeklyHours" to it.totalWeeklyHours,
                    "daysCountRequired" to it.daysCountRequired,
                    "minHours" to it.minHours,
                    "shiftName" to it.shiftName
                )
            },
            "subdivisions" to subdivisions.map { sub ->
                mapOf(
                    "id" to sub.id,
                    "name" to sub.name,
                    "type" to sub.type.name,
                    "imageUrls" to sub.imageUrls,
                    "amenities" to sub.amenities,
                    "rentalStrategies" to sub.rentalStrategies.map { strat ->
                        mapOf(
                            "strategy" to strat.strategy.name,
                            "rateUsd" to strat.rateUsd,
                            "minDuration" to strat.minDuration,
                            "availableHoursOrShifts" to strat.availableHoursOrShifts
                        )
                    }
                )
            },
            "rules" to mapOf(
                "smokingAllowed" to rules.smokingAllowed,
                "foodAllowed" to rules.foodAllowed,
                "petsAllowed" to rules.petsAllowed,
                "visitorPolicy" to rules.visitorPolicy,
                "offHoursAccess" to rules.offHoursAccess,
                "sharedAmenities" to rules.sharedAmenities
            ),
            "schedule" to mapOf(
                "openingHour" to schedule.openingHour,
                "closingHour" to schedule.closingHour,
                "operatingDays" to schedule.operatingDays,
                "isSundayOperating" to schedule.isSundayOperating,
                "blackoutSlots" to schedule.blackoutSlots.map {
                    mapOf(
                        "id" to it.id,
                        "dayOfWeek" to it.dayOfWeek,
                        "startTime" to it.startTime,
                        "endTime" to it.endTime,
                        "reason" to it.reason
                    )
                }
            ),
            "ownerId" to ownerId,
            "ownerName" to ownerName,
            "ownerPhone" to ownerPhone,
            "ownerEmail" to ownerEmail,
            "ownershipProofUrl" to ownershipProofUrl,
            "isVerified" to isVerified,
            "isActiveSubscription" to isActiveSubscription,
            "subscriptionExpiryMillis" to subscriptionExpiryMillis,
            "imageUrls" to imageUrls,
            "videoTourDurationSec" to videoTourDurationSec,
            "baseMonthlyRateUsd" to baseMonthlyRateUsd,
            "avatarEngagementViews" to avatarEngagementViews,
            "avatarInquiryClicks" to avatarInquiryClicks,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    companion object {
        const val COLLECTION_PATH = "workspace_listings"

        fun fromFirestoreMap(docId: String, data: Map<String, Any?>): SpaceListing {
            val spaceTypeStr = data["spaceType"] as? String ?: SpaceType.PRIVATE_OFFICE.name
            val spaceType = runCatching { SpaceType.valueOf(spaceTypeStr) }.getOrDefault(SpaceType.PRIVATE_OFFICE)

            val govStr = data["governorate"] as? String ?: Governorate.BEIRUT.name
            val gov = runCatching { Governorate.valueOf(govStr) }.getOrDefault(Governorate.BEIRUT)

            val equipList = (data["equipment"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.let { map ->
                    val catStr = map["category"] as? String ?: EquipmentCategory.WORKSPACES.name
                    val cat = runCatching { EquipmentCategory.valueOf(catStr) }.getOrDefault(EquipmentCategory.WORKSPACES)
                    EquipmentItem(
                        id = map["id"] as? String ?: UUID.randomUUID().toString(),
                        name = map["name"] as? String ?: "Equipment Item",
                        category = cat,
                        quantity = (map["quantity"] as? Number)?.toInt() ?: 1,
                        description = map["description"] as? String ?: ""
                    )
                }
            } ?: emptyList()

            val formulasList = (data["rentalFormulas"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.let { map ->
                    val typeStr = map["type"] as? String ?: RentalFormulaType.FULL_MONTH.name
                    val type = runCatching { RentalFormulaType.valueOf(typeStr) }.getOrDefault(RentalFormulaType.FULL_MONTH)
                    RentalFormula(
                        id = map["id"] as? String ?: ("FRM-" + UUID.randomUUID().toString().take(4)),
                        type = type,
                        rateUsd = (map["rateUsd"] as? Number)?.toDouble() ?: 300.0,
                        scheduleDescription = map["scheduleDescription"] as? String ?: "Formula Slot",
                        daysOfWeek = (map["daysOfWeek"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
                        startHour = map["startHour"] as? String ?: "08:00",
                        endHour = map["endHour"] as? String ?: "18:00",
                        totalWeeklyHours = (map["totalWeeklyHours"] as? Number)?.toInt() ?: 40,
                        daysCountRequired = (map["daysCountRequired"] as? Number)?.toInt() ?: 1,
                        minHours = (map["minHours"] as? Number)?.toInt() ?: 2,
                        shiftName = map["shiftName"] as? String ?: "General Shift"
                    )
                }
            } ?: emptyList()

            val rulesMap = data["rules"] as? Map<*, *>
            val rules = if (rulesMap != null) {
                PremisesRules(
                    smokingAllowed = rulesMap["smokingAllowed"] as? Boolean ?: false,
                    foodAllowed = rulesMap["foodAllowed"] as? Boolean ?: true,
                    petsAllowed = rulesMap["petsAllowed"] as? Boolean ?: false,
                    visitorPolicy = rulesMap["visitorPolicy"] as? String ?: "Clients welcomed in reception lounge",
                    offHoursAccess = rulesMap["offHoursAccess"] as? Boolean ?: true,
                    sharedAmenities = (rulesMap["sharedAmenities"] as? List<*>)?.mapNotNull { it as? String }
                        ?: listOf("Receptionist Desk", "Client Waiting Lounge", "Kitchenette", "Restroom")
                )
            } else PremisesRules()

            val scheduleMap = data["schedule"] as? Map<*, *>
            val schedule = if (scheduleMap != null) {
                val blackoutList = (scheduleMap["blackoutSlots"] as? List<*>)?.mapNotNull { bItem ->
                    (bItem as? Map<*, *>)?.let { bMap ->
                        BlackoutSlot(
                            id = bMap["id"] as? String ?: UUID.randomUUID().toString(),
                            dayOfWeek = bMap["dayOfWeek"] as? String ?: "Sunday",
                            startTime = bMap["startTime"] as? String ?: "18:00",
                            endTime = bMap["endTime"] as? String ?: "22:00",
                            reason = bMap["reason"] as? String ?: "Maintenance"
                        )
                    }
                } ?: emptyList()

                SpaceOperatingSchedule(
                    openingHour = scheduleMap["openingHour"] as? String ?: "08:00",
                    closingHour = scheduleMap["closingHour"] as? String ?: "20:00",
                    operatingDays = (scheduleMap["operatingDays"] as? List<*>)?.mapNotNull { it as? String }
                        ?: listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
                    blackoutSlots = blackoutList,
                    isSundayOperating = scheduleMap["isSundayOperating"] as? Boolean ?: false
                )
            } else SpaceOperatingSchedule()

            val subsList = (data["subdivisions"] as? List<*>)?.mapNotNull { sItem ->
                (sItem as? Map<*, *>)?.let { sMap ->
                    val lvlStr = sMap["type"] as? String ?: Level2Type.ROOMS.name
                    val lvlType = runCatching { Level2Type.valueOf(lvlStr) }.getOrDefault(Level2Type.ROOMS)
                    val stratsList = (sMap["rentalStrategies"] as? List<*>)?.mapNotNull { stratItem ->
                        (stratItem as? Map<*, *>)?.let { stratMap ->
                            val rStr = stratMap["strategy"] as? String ?: RentalStrategy.MONTHLY.name
                            val rStrat = runCatching { RentalStrategy.valueOf(rStr) }.getOrDefault(RentalStrategy.MONTHLY)
                            SubdivisionStrategy(
                                strategy = rStrat,
                                rateUsd = (stratMap["rateUsd"] as? Number)?.toDouble() ?: 200.0,
                                minDuration = (stratMap["minDuration"] as? Number)?.toInt() ?: 1,
                                availableHoursOrShifts = stratMap["availableHoursOrShifts"] as? String ?: ""
                            )
                        }
                    } ?: emptyList()

                    Subdivision(
                        id = sMap["id"] as? String ?: ("SUB-" + UUID.randomUUID().toString().take(6)),
                        name = sMap["name"] as? String ?: "Subdivision Unit",
                        type = lvlType,
                        imageUrls = (sMap["imageUrls"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        amenities = (sMap["amenities"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        rentalStrategies = stratsList
                    )
                }
            } ?: emptyList()

            return SpaceListing(
                id = docId,
                title = data["title"] as? String ?: "Executive Workspace",
                spaceType = spaceType,
                governorate = gov,
                district = data["district"] as? String ?: "Beirut",
                streetAddress = data["streetAddress"] as? String ?: "Beirut Central District",
                floorInfo = data["floorInfo"] as? String ?: "Floor 1",
                lat = (data["lat"] as? Number)?.toDouble() ?: 33.8938,
                lng = (data["lng"] as? Number)?.toDouble() ?: 35.5018,
                isShared = data["isShared"] as? Boolean ?: false,
                complementarySpecialties = (data["complementarySpecialties"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                residentPractitioners = (data["residentPractitioners"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                essentialFacilities = (data["essentialFacilities"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                equipment = equipList,
                rentalFormulas = formulasList,
                rules = rules,
                schedule = schedule,
                ownerId = data["ownerId"] as? String ?: "",
                ownerName = data["ownerName"] as? String ?: "Workspace Host",
                ownerPhone = data["ownerPhone"] as? String ?: "",
                ownerEmail = data["ownerEmail"] as? String ?: "",
                ownershipProofUrl = data["ownershipProofUrl"] as? String,
                isVerified = data["isVerified"] as? Boolean ?: true,
                isActiveSubscription = data["isActiveSubscription"] as? Boolean ?: true,
                isOwnerSuspended = data["isOwnerSuspended"] as? Boolean ?: false,
                subscriptionExpiryMillis = (data["subscriptionExpiryMillis"] as? Number)?.toLong() ?: (System.currentTimeMillis() + 30L * 24 * 3600 * 1000),
                imageUrls = (data["imageUrls"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                videoTourDurationSec = (data["videoTourDurationSec"] as? Number)?.toInt() ?: 10,
                baseMonthlyRateUsd = (data["baseMonthlyRateUsd"] as? Number)?.toDouble() ?: 450.0,
                avatarEngagementViews = (data["avatarEngagementViews"] as? Number)?.toInt() ?: 0,
                avatarInquiryClicks = (data["avatarInquiryClicks"] as? Number)?.toInt() ?: 0,
                subdivisions = subsList
            )
        }
    }
}

enum class TransactionStatus {
    SUCCESS,
    PENDING,
    FAILED
}

data class WhishTransaction(
    val id: String,
    val orderId: String,
    val amountUsd: Double,
    val currency: String = "USD",
    val status: TransactionStatus,
    val timestamp: Long,
    val payerName: String,
    val payerPhone: String,
    val channelId: String = MerchantConfig.WHISH_CHANNEL_ID,
    val sourceEmail: String = MerchantConfig.WHISH_MERCHANT_EMAIL,
    val signatureHash: String,
    val spaceId: String,
    val spaceTitle: String,
    val daysGranted: Int = 30,
    val userId: String = ""
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "orderId" to orderId,
            "amountUsd" to amountUsd,
            "currency" to currency,
            "status" to status.name,
            "timestamp" to timestamp,
            "payerName" to payerName,
            "payerPhone" to payerPhone,
            "channelId" to channelId,
            "sourceEmail" to sourceEmail,
            "signatureHash" to signatureHash,
            "spaceId" to spaceId,
            "spaceTitle" to spaceTitle,
            "daysGranted" to daysGranted,
            "userId" to userId
        )
    }

    companion object {
        const val COLLECTION_PATH = "whish_transactions"

        fun fromFirestoreMap(docId: String, data: Map<String, Any?>): WhishTransaction {
            val statusStr = data["status"] as? String ?: TransactionStatus.PENDING.name
            val stat = runCatching { TransactionStatus.valueOf(statusStr) }.getOrDefault(TransactionStatus.PENDING)
            return WhishTransaction(
                id = docId,
                orderId = data["orderId"] as? String ?: "",
                amountUsd = (data["amountUsd"] as? Number)?.toDouble() ?: 0.0,
                currency = data["currency"] as? String ?: "USD",
                status = stat,
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                payerName = data["payerName"] as? String ?: "",
                payerPhone = data["payerPhone"] as? String ?: "",
                channelId = data["channelId"] as? String ?: MerchantConfig.WHISH_CHANNEL_ID,
                sourceEmail = data["sourceEmail"] as? String ?: MerchantConfig.WHISH_MERCHANT_EMAIL,
                signatureHash = data["signatureHash"] as? String ?: "",
                spaceId = data["spaceId"] as? String ?: "",
                spaceTitle = data["spaceTitle"] as? String ?: "",
                daysGranted = (data["daysGranted"] as? Number)?.toInt() ?: 30,
                userId = data["userId"] as? String ?: ""
            )
        }
    }
}

enum class UserRole(val displayName: String) {
    SPECIALIST("Specialist"),
    PRO_HOST("Pro Host"),
    ADMIN("Super Administrator")
}

data class AuditSecurityLog(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String,
    val details: String,
    val actorEmail: String = "system@prohost.app",
    val severity: String = "INFO", // INFO, WARN, SECURE
    val ipAddress: String = "127.0.0.1"
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "timestamp" to timestamp,
            "actionType" to actionType,
            "details" to details,
            "actorEmail" to actorEmail,
            "severity" to severity,
            "ipAddress" to ipAddress
        )
    }

    companion object {
        const val COLLECTION_PATH = "audit_security_logs"

        fun fromFirestoreMap(docId: String, data: Map<String, Any?>): AuditSecurityLog {
            return AuditSecurityLog(
                id = docId,
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                actionType = data["actionType"] as? String ?: "SYSTEM_EVENT",
                details = data["details"] as? String ?: "",
                actorEmail = data["actorEmail"] as? String ?: "admin@prohost.lb",
                severity = data["severity"] as? String ?: "INFO",
                ipAddress = data["ipAddress"] as? String ?: "127.0.0.1"
            )
        }
    }
}

enum class SubscriptionBillingInterval(val displayName: String, val monthsDuration: Int) {
    HOURLY("Hourly Slot", 0),
    SHIFT("Shift Basis", 0),
    DAY_PER_WEEK("Day per Week", 1),
    MONTHLY("Monthly Subscription", 1),
    QUARTERLY("Quarterly Pass (3 Months)", 3),
    SEMESTER("Semester (6 Months)", 6),
    ANNUAL("Annual Corporate (12 Months)", 12)
}

/**
 * Flexible Multi-Tier Subscription & Workspace Formula Model
 * Collection: "subscription_formulas"
 */
data class SubscriptionFormula(
    val id: String = "SUB-FRM-" + java.util.UUID.randomUUID().toString().take(6).uppercase(),
    val title: String,
    val type: RentalFormulaType = RentalFormulaType.FULL_MONTH,
    val billingInterval: SubscriptionBillingInterval = SubscriptionBillingInterval.MONTHLY,
    val priceUsd: Double,
    val description: String,
    val daysPerWeek: Int = 5,
    val hoursPerDay: Int = 8,
    val startHour: String = "08:00",
    val endHour: String = "18:00",
    val targetSpecialties: List<String> = emptyList(),
    val includedPerks: List<String> = listOf(
        "High-speed Fiber Wi-Fi",
        "Receptionist & Waiting Lounge Access",
        "24/7 Generator & Solar Power Backup",
        "Syndicate Verified Space Guarantee",
        "Meeting Room Credits"
    ),
    val discountPercent: Double = 0.0,
    val isFeatured: Boolean = false,
    val isActive: Boolean = true,
    val spaceId: String? = null
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "title" to title,
            "type" to type.name,
            "billingInterval" to billingInterval.name,
            "priceUsd" to priceUsd,
            "description" to description,
            "daysPerWeek" to daysPerWeek,
            "hoursPerDay" to hoursPerDay,
            "startHour" to startHour,
            "endHour" to endHour,
            "targetSpecialties" to targetSpecialties,
            "includedPerks" to includedPerks,
            "discountPercent" to discountPercent,
            "isFeatured" to isFeatured,
            "isActive" to isActive,
            "spaceId" to spaceId,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    companion object {
        const val COLLECTION_PATH = "subscription_formulas"

        fun fromFirestoreMap(docId: String, data: Map<String, Any?>): SubscriptionFormula {
            val typeStr = data["type"] as? String ?: RentalFormulaType.FULL_MONTH.name
            val type = runCatching { RentalFormulaType.valueOf(typeStr) }.getOrDefault(RentalFormulaType.FULL_MONTH)

            val intervalStr = data["billingInterval"] as? String ?: SubscriptionBillingInterval.MONTHLY.name
            val interval = runCatching { SubscriptionBillingInterval.valueOf(intervalStr) }.getOrDefault(SubscriptionBillingInterval.MONTHLY)

            return SubscriptionFormula(
                id = docId,
                title = data["title"] as? String ?: "Pro Flexible Plan",
                type = type,
                billingInterval = interval,
                priceUsd = (data["priceUsd"] as? Number)?.toDouble() ?: 250.0,
                description = data["description"] as? String ?: "Flexible verified workspace membership",
                daysPerWeek = (data["daysPerWeek"] as? Number)?.toInt() ?: 5,
                hoursPerDay = (data["hoursPerDay"] as? Number)?.toInt() ?: 8,
                startHour = data["startHour"] as? String ?: "08:00",
                endHour = data["endHour"] as? String ?: "18:00",
                targetSpecialties = (data["targetSpecialties"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                includedPerks = (data["includedPerks"] as? List<*>)?.mapNotNull { it as? String } ?: listOf(
                    "High-speed Fiber Wi-Fi",
                    "Receptionist Access",
                    "Continuous Power Backup"
                ),
                discountPercent = (data["discountPercent"] as? Number)?.toDouble() ?: 0.0,
                isFeatured = data["isFeatured"] as? Boolean ?: false,
                isActive = data["isActive"] as? Boolean ?: true,
                spaceId = data["spaceId"] as? String
            )
        }
    }
}

/**
 * A registered account. There is no admin-reviewed "accreditation" concept anymore —
 * [isVerified] means only "this account's phone number was confirmed via Firebase
 * Phone Auth SMS OTP at registration" (set server-side, see assignInitialRole.ts,
 * from the ID token's phone_number claim — never a manually-toggled admin flag).
 * [idDocumentUrl] (ID/passport, required at registration) is kept on file with no
 * review workflow; a PRO_HOST's proof of ownership / right-to-rent is uploaded per
 * listing instead (see [SpaceListing.ownershipProofUrl]), not on the user profile.
 * [createdAtMillis]/[lastSignInAtMillis] are written only by assignInitialRole.ts —
 * the account-creation and last-sign-in audit trail Admin's Users Directory export
 * relies on (see ProHostRepository.exportUsersToCsv/exportUsersToJson).
 */
data class AppUser(
    val id: String,
    val email: String,
    val fullName: String,
    val role: UserRole,
    val specialty: String,
    val phone: String,
    val profilePictureUrl: String? = null,
    val idDocumentUrl: String? = null,
    val country: String = "Lebanon",
    val governorate: String = "",
    val city: String = "",
    val isVerified: Boolean = false,
    val subscriptionExpiryMillis: Long? = null,
    val ownerPackageTier: OwnerPackageTier = OwnerPackageTier.PAY_AS_YOU_GO,
    val ownerPackageExpiryMillis: Long? = null,
    val paygListingsBoughtCount: Int = 0,
    // Server-only, written exclusively by assignInitialRole.ts — createdAtMillis is set
    // once, the first time this uid ever gets a role claim; lastSignInAtMillis is
    // refreshed on every subsequent call (every sign-in). Never included in
    // [toFirestoreMap] so a client write can never touch either field, even by accident.
    val createdAtMillis: Long? = null,
    val lastSignInAtMillis: Long? = null,
    // Server-only, written exclusively by setAccountSuspended.ts — the state between
    // "exists" and "deleted" Admin never had before. Enforced both at sign-in
    // (assignInitialRole.ts rejects it) and at write time (firestore.rules' isSuspended()
    // re-reads this field live, so a suspension takes effect on the very next attempt
    // rather than waiting for the caller's ID token to refresh).
    val isSuspended: Boolean = false,
    // Server-only, written exclusively by functions/src/listings/listingCountTracker.ts —
    // a read-only projection of the same counter firestore.rules' withinListingLimit()
    // already protects. Exists on this model purely so the client can fail fast (grey
    // out "Add New Workspace Listing" once at the Package-2 cap) instead of only finding
    // out after completing the whole create-listing wizard; the rule stays the real gate.
    val activeListingCount: Int = 0,
    // Client-writable — a personal shortlist, not a protected/server-only field, so it's
    // simply absent from firestore.rules' user_profiles protected-key list and writable
    // by the owner like any other profile field. See ProHostRepository.toggleSavedSpace.
    val savedSpaceIds: List<String> = emptyList()
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "email" to email,
            "fullName" to fullName,
            "role" to role.name,
            "specialty" to specialty,
            "phone" to phone,
            "profilePictureUrl" to profilePictureUrl,
            "idDocumentUrl" to idDocumentUrl,
            "country" to country,
            "governorate" to governorate,
            "city" to city,
            "isVerified" to isVerified,
            "subscriptionExpiryMillis" to subscriptionExpiryMillis,
            "ownerPackageTier" to ownerPackageTier.name,
            "ownerPackageExpiryMillis" to ownerPackageExpiryMillis,
            "paygListingsBoughtCount" to paygListingsBoughtCount,
            "savedSpaceIds" to savedSpaceIds,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    companion object {
        const val COLLECTION_PATH = "user_profiles"

        fun fromFirestoreMap(docId: String, data: Map<String, Any?>): AppUser {
            val roleStr = data["role"] as? String ?: UserRole.SPECIALIST.name
            val role = runCatching { UserRole.valueOf(roleStr) }.getOrDefault(UserRole.SPECIALIST)

            val pkgTierStr = data["ownerPackageTier"] as? String ?: OwnerPackageTier.PAY_AS_YOU_GO.name
            val pkgTier = runCatching { OwnerPackageTier.valueOf(pkgTierStr) }.getOrDefault(OwnerPackageTier.PAY_AS_YOU_GO)

            return AppUser(
                id = docId,
                email = data["email"] as? String ?: "",
                fullName = data["fullName"] as? String ?: "Member",
                role = role,
                specialty = data["specialty"] as? String ?: "",
                phone = data["phone"] as? String ?: "",
                profilePictureUrl = data["profilePictureUrl"] as? String,
                idDocumentUrl = data["idDocumentUrl"] as? String,
                country = data["country"] as? String ?: "Lebanon",
                governorate = data["governorate"] as? String ?: "",
                city = data["city"] as? String ?: "",
                isVerified = data["isVerified"] as? Boolean ?: false,
                subscriptionExpiryMillis = (data["subscriptionExpiryMillis"] as? Number)?.toLong(),
                ownerPackageTier = pkgTier,
                ownerPackageExpiryMillis = (data["ownerPackageExpiryMillis"] as? Number)?.toLong(),
                paygListingsBoughtCount = (data["paygListingsBoughtCount"] as? Number)?.toInt() ?: 0,
                createdAtMillis = (data["createdAtMillis"] as? Number)?.toLong(),
                lastSignInAtMillis = (data["lastSignInAtMillis"] as? Number)?.toLong(),
                isSuspended = data["isSuspended"] as? Boolean ?: false,
                activeListingCount = (data["activeListingCount"] as? Number)?.toInt() ?: 0,
                savedSpaceIds = (data["savedSpaceIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }
    }
}

enum class OwnerPackageTier(
    val tierNumber: Int,
    val title: String,
    val subtitle: String,
    val maxListings: Int,
    val badgeName: String
) {
    PAY_AS_YOU_GO(
        tierNumber = 1,
        title = "Package 1: Pay As You Go",
        subtitle = "Per-listing billing based on workspace type configured at Admin Console",
        maxListings = 0, // indicates dynamic per-listing billing
        badgeName = "Pay As You Go"
    ),
    LIMITED_3_TIER(
        tierNumber = 2,
        title = "Package 2: Pro (3 Listings Limit)",
        subtitle = "Host and operate up to 3 active workspaces under a bundled monthly fee",
        maxListings = 3,
        badgeName = "3-Listing Pro"
    ),
    UNLIMITED_TIER(
        tierNumber = 3,
        title = "Package 3: Enterprise (All-In Unlimited)",
        subtitle = "Publish unlimited active workspace listings with priority platform exposure",
        maxListings = Int.MAX_VALUE,
        badgeName = "All-In Unlimited"
    )
}

data class AdminPricingState(
    val monthlySubscriptionFeeUsd: Double = 1.80,
    val baselineFeeUsd: Double = 1.80,
    val presetOptions: List<Double> = listOf(1.00, 1.50, 1.80, 2.50, 3.00, 5.00, 10.00),
    val isPackagingGovernanceActive: Boolean = true,
    val governanceTag: String = "HOST-PACKAGING-TIERS-V2-ACTIVE",
    val paygPrivateOfficeUsd: Double = 1.50,
    val paygCenterUsd: Double = 3.50,
    val paygPolyclinicUsd: Double = 2.80,
    val paygCoworkingUsd: Double = 1.80,
    val paygExecutiveBoardroomUsd: Double = 2.20,
    val paygConsultationSuiteUsd: Double = 1.60,
    val package2Limit: Int = 3,
    val package2MonthlyFeeUsd: Double = 3.99,
    val package3MonthlyFeeUsd: Double = 8.99,
    val merchantChannelId: String = MerchantConfig.WHISH_CHANNEL_ID,
    val merchantSource: String = MerchantConfig.WHISH_MERCHANT_EMAIL,
    val merchantSecretKeyMasked: String = "whish_sec_994a****87x"
) {
    fun getPaygFeeForType(type: SpaceType): Double {
        return when (type) {
            SpaceType.PRIVATE_OFFICE -> paygPrivateOfficeUsd
            SpaceType.CENTER -> paygCenterUsd
            SpaceType.POLYCLINIC -> paygPolyclinicUsd
            SpaceType.COWORKING_SPACE -> paygCoworkingUsd
        }
    }

    fun getPackageFee(tier: OwnerPackageTier): Double {
        return when (tier) {
            OwnerPackageTier.PAY_AS_YOU_GO -> monthlySubscriptionFeeUsd
            OwnerPackageTier.LIMITED_3_TIER -> package2MonthlyFeeUsd
            OwnerPackageTier.UNLIMITED_TIER -> package3MonthlyFeeUsd
        }
    }

    /**
     * The initiateWhishPayment Cloud Function reads pricing from this same document
     * (functions/src/lib/pricing.ts) to compute the real amount server-side — never
     * trusting a client-supplied amount. Admin pricing changes must persist here or
     * the server keeps charging its hardcoded fallback defaults forever.
     */
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "monthlySubscriptionFeeUsd" to monthlySubscriptionFeeUsd,
            "baselineFeeUsd" to baselineFeeUsd,
            "presetOptions" to presetOptions,
            "isPackagingGovernanceActive" to isPackagingGovernanceActive,
            "governanceTag" to governanceTag,
            "paygPrivateOfficeUsd" to paygPrivateOfficeUsd,
            "paygCenterUsd" to paygCenterUsd,
            "paygPolyclinicUsd" to paygPolyclinicUsd,
            "paygCoworkingUsd" to paygCoworkingUsd,
            "paygExecutiveBoardroomUsd" to paygExecutiveBoardroomUsd,
            "paygConsultationSuiteUsd" to paygConsultationSuiteUsd,
            "package2Limit" to package2Limit,
            "package2MonthlyFeeUsd" to package2MonthlyFeeUsd,
            "package3MonthlyFeeUsd" to package3MonthlyFeeUsd,
            "merchantChannelId" to merchantChannelId,
            "merchantSource" to merchantSource,
            "merchantSecretKeyMasked" to merchantSecretKeyMasked,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    companion object {
        const val COLLECTION_PATH = "system_metadata"
        const val DOCUMENT_ID = "pricing"

        fun fromFirestoreMap(data: Map<String, Any?>): AdminPricingState {
            val defaults = AdminPricingState()
            return AdminPricingState(
                monthlySubscriptionFeeUsd = (data["monthlySubscriptionFeeUsd"] as? Number)?.toDouble() ?: defaults.monthlySubscriptionFeeUsd,
                baselineFeeUsd = (data["baselineFeeUsd"] as? Number)?.toDouble() ?: defaults.baselineFeeUsd,
                presetOptions = (data["presetOptions"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: defaults.presetOptions,
                isPackagingGovernanceActive = data["isPackagingGovernanceActive"] as? Boolean ?: defaults.isPackagingGovernanceActive,
                governanceTag = data["governanceTag"] as? String ?: defaults.governanceTag,
                paygPrivateOfficeUsd = (data["paygPrivateOfficeUsd"] as? Number)?.toDouble() ?: defaults.paygPrivateOfficeUsd,
                paygCenterUsd = (data["paygCenterUsd"] as? Number)?.toDouble() ?: defaults.paygCenterUsd,
                paygPolyclinicUsd = (data["paygPolyclinicUsd"] as? Number)?.toDouble() ?: defaults.paygPolyclinicUsd,
                paygCoworkingUsd = (data["paygCoworkingUsd"] as? Number)?.toDouble() ?: defaults.paygCoworkingUsd,
                paygExecutiveBoardroomUsd = (data["paygExecutiveBoardroomUsd"] as? Number)?.toDouble() ?: defaults.paygExecutiveBoardroomUsd,
                paygConsultationSuiteUsd = (data["paygConsultationSuiteUsd"] as? Number)?.toDouble() ?: defaults.paygConsultationSuiteUsd,
                package2Limit = (data["package2Limit"] as? Number)?.toInt() ?: defaults.package2Limit,
                package2MonthlyFeeUsd = (data["package2MonthlyFeeUsd"] as? Number)?.toDouble() ?: defaults.package2MonthlyFeeUsd,
                package3MonthlyFeeUsd = (data["package3MonthlyFeeUsd"] as? Number)?.toDouble() ?: defaults.package3MonthlyFeeUsd,
                merchantChannelId = data["merchantChannelId"] as? String ?: defaults.merchantChannelId,
                merchantSource = data["merchantSource"] as? String ?: defaults.merchantSource,
                merchantSecretKeyMasked = data["merchantSecretKeyMasked"] as? String ?: defaults.merchantSecretKeyMasked
            )
        }
    }
}

data class FCMAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val category: String // "BOOKING_ACCEPTANCE" or "PAYMENT_REMINDER"
)

/**
 * Dynamic Space Architecture Schema Configuration Model
 * Allows Super Admin to inspect and customize database-level mapping of
 * Listing Types, Subcategories, Amenities, Equipment, Specialties, and Strategies.
 */
data class SchemaItem(
    val id: String,
    val name: String,
    val description: String = "",
    val category: String, // "SPACE_TYPE", "SUBCATEGORY", "AMENITY", "EQUIPMENT", "SPECIALTY", "RENTAL_STRATEGY"
    val iconName: String = "Category",
    val isEnabled: Boolean = true,
    val isSystemDefault: Boolean = true
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "category" to category,
        "iconName" to iconName,
        "isEnabled" to isEnabled,
        "isSystemDefault" to isSystemDefault
    )

    companion object {
        fun fromFirestoreMap(data: Map<String, Any?>): SchemaItem = SchemaItem(
            id = data["id"] as? String ?: "",
            name = data["name"] as? String ?: "",
            description = data["description"] as? String ?: "",
            category = data["category"] as? String ?: "",
            iconName = data["iconName"] as? String ?: "Category",
            isEnabled = data["isEnabled"] as? Boolean ?: true,
            isSystemDefault = data["isSystemDefault"] as? Boolean ?: true
        )
    }
}

data class SpaceArchitectureSchema(
    val spaceTypes: List<SchemaItem> = emptyList(),
    val subcategories: List<SchemaItem> = emptyList(),
    val amenities: List<SchemaItem> = emptyList(),
    val equipmentCategories: List<SchemaItem> = emptyList(),
    val specialties: List<SchemaItem> = emptyList(),
    val rentalStrategies: List<SchemaItem> = emptyList()
) {
    val totalItemsCount: Int
        get() = spaceTypes.size + subcategories.size + amenities.size + equipmentCategories.size + specialties.size + rentalStrategies.size

    val activeItemsCount: Int
        get() = spaceTypes.count { it.isEnabled } +
                subcategories.count { it.isEnabled } +
                amenities.count { it.isEnabled } +
                equipmentCategories.count { it.isEnabled } +
                specialties.count { it.isEnabled } +
                rentalStrategies.count { it.isEnabled }

    val allItems: List<SchemaItem>
        get() = spaceTypes + subcategories + amenities + equipmentCategories + specialties + rentalStrategies

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "spaceTypes" to spaceTypes.map { it.toFirestoreMap() },
        "subcategories" to subcategories.map { it.toFirestoreMap() },
        "amenities" to amenities.map { it.toFirestoreMap() },
        "equipmentCategories" to equipmentCategories.map { it.toFirestoreMap() },
        "specialties" to specialties.map { it.toFirestoreMap() },
        "rentalStrategies" to rentalStrategies.map { it.toFirestoreMap() }
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestoreMap(data: Map<String, Any?>): SpaceArchitectureSchema {
            fun list(key: String): List<SchemaItem> =
                (data[key] as? List<Map<String, Any?>>)?.map { SchemaItem.fromFirestoreMap(it) } ?: emptyList()
            return SpaceArchitectureSchema(
                spaceTypes = list("spaceTypes"),
                subcategories = list("subcategories"),
                amenities = list("amenities"),
                equipmentCategories = list("equipmentCategories"),
                specialties = list("specialties"),
                rentalStrategies = list("rentalStrategies")
            )
        }
    }
}

/**
 * Database Architecture: Orders Table
 * Tracks overall shopping cart or service purchase.
 */
data class Order(
    val id: String = "ORD-" + java.util.UUID.randomUUID().toString().take(8).uppercase(),
    val userId: String,
    val totalAmount: Double,
    val currency: String = "USD", // USD or LBP
    val createdAt: Long = System.currentTimeMillis()
)


