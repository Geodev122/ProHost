package com.example.data.model

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
    val practitionerSyndicateNumber: String = "",
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
    val isExternalPaymentSettled: Boolean = false,
    val subdivisionId: String? = null,
    val subdivisionName: String? = null,
    val selectedStrategy: String? = null
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
            "practitionerSyndicateNumber" to practitionerSyndicateNumber,
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
            "isExternalPaymentSettled" to isExternalPaymentSettled,
            "subdivisionId" to subdivisionId,
            "subdivisionName" to subdivisionName,
            "selectedStrategy" to selectedStrategy
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
                practitionerSyndicateNumber = data["practitionerSyndicateNumber"] as? String ?: "",
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
                isExternalPaymentSettled = data["isExternalPaymentSettled"] as? Boolean ?: false,
                subdivisionId = data["subdivisionId"] as? String,
                subdivisionName = data["subdivisionName"] as? String,
                selectedStrategy = data["selectedStrategy"] as? String
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
    val isVerified: Boolean = true,
    val isActiveSubscription: Boolean = true,
    val subscriptionExpiryMillis: Long = System.currentTimeMillis() + (28L * 24 * 60 * 60 * 1000),
    val imageUrls: List<String> = emptyList(),
    val videoTourDurationSec: Int = 10,
    val baseMonthlyRateUsd: Double = 450.0,
    val avatarEngagementViews: Int = 850,
    val avatarInquiryClicks: Int = 14,
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
                isVerified = data["isVerified"] as? Boolean ?: true,
                isActiveSubscription = data["isActiveSubscription"] as? Boolean ?: true,
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
    val channelId: String = "15462415",
    val sourceEmail: String = "ceo@hopebearer-award.com",
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
                channelId = data["channelId"] as? String ?: "15462415",
                sourceEmail = data["sourceEmail"] as? String ?: "ceo@hopebearer-award.com",
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
    PROFESSIONAL("Practitioner / Specialist"),
    SPACE_OWNER("Space Owner / Host"),
    ADMIN("Super Administrator");

    companion object {
        val MEDICAL_PRACTITIONER = PROFESSIONAL
    }
}

data class AuditSecurityLog(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String,
    val details: String,
    val actorEmail: String = "geo.elnajjar@gmail.com",
    val severity: String = "INFO", // INFO, WARN, SECURE
    val ipAddress: String = "192.168.1.108"
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
                actorEmail = data["actorEmail"] as? String ?: "admin@prospace.lb",
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

data class AppUser(
    val id: String,
    val email: String,
    val fullName: String,
    val role: UserRole,
    val specialty: String,
    val phone: String,
    val affiliation: String,
    val syndicateNumber: String,
    val governorate: Governorate = Governorate.BEIRUT,
    val isVerified: Boolean = true,
    val verificationStatus: MemberVerificationStatus = MemberVerificationStatus.VERIFIED,
    val verificationTier: VerificationTier = VerificationTier.TIER_2_PROFESSIONAL,
    val verificationNotes: String? = null,
    val trustScore: Int = 98,
    val subscriptionExpiryMillis: Long? = null,
    val ownerPackageTier: OwnerPackageTier = OwnerPackageTier.PAY_AS_YOU_GO,
    val ownerPackageExpiryMillis: Long? = null,
    val paygListingsBoughtCount: Int = 0
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "email" to email,
            "fullName" to fullName,
            "role" to role.name,
            "specialty" to specialty,
            "phone" to phone,
            "affiliation" to affiliation,
            "syndicateNumber" to syndicateNumber,
            "governorate" to governorate.name,
            "isVerified" to isVerified,
            "verificationStatus" to verificationStatus.name,
            "verificationTier" to verificationTier.name,
            "verificationNotes" to verificationNotes,
            "trustScore" to trustScore,
            "subscriptionExpiryMillis" to subscriptionExpiryMillis,
            "ownerPackageTier" to ownerPackageTier.name,
            "ownerPackageExpiryMillis" to ownerPackageExpiryMillis,
            "paygListingsBoughtCount" to paygListingsBoughtCount,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    companion object {
        const val COLLECTION_PATH = "user_profiles"

        fun fromFirestoreMap(docId: String, data: Map<String, Any?>): AppUser {
            val roleStr = data["role"] as? String ?: UserRole.PROFESSIONAL.name
            val role = runCatching { UserRole.valueOf(roleStr) }.getOrDefault(UserRole.PROFESSIONAL)

            val govStr = data["governorate"] as? String ?: Governorate.BEIRUT.name
            val gov = runCatching { Governorate.valueOf(govStr) }.getOrDefault(Governorate.BEIRUT)

            val vStatusStr = data["verificationStatus"] as? String ?: MemberVerificationStatus.VERIFIED.name
            val vStatus = runCatching { MemberVerificationStatus.valueOf(vStatusStr) }.getOrDefault(MemberVerificationStatus.VERIFIED)

            val vTierStr = data["verificationTier"] as? String ?: VerificationTier.TIER_2_PROFESSIONAL.name
            val vTier = runCatching { VerificationTier.valueOf(vTierStr) }.getOrDefault(VerificationTier.TIER_2_PROFESSIONAL)

            val pkgTierStr = data["ownerPackageTier"] as? String ?: OwnerPackageTier.PAY_AS_YOU_GO.name
            val pkgTier = runCatching { OwnerPackageTier.valueOf(pkgTierStr) }.getOrDefault(OwnerPackageTier.PAY_AS_YOU_GO)

            return AppUser(
                id = docId,
                email = data["email"] as? String ?: "",
                fullName = data["fullName"] as? String ?: "Member",
                role = role,
                specialty = data["specialty"] as? String ?: "Professional",
                phone = data["phone"] as? String ?: "",
                affiliation = data["affiliation"] as? String ?: "Syndicate Member",
                syndicateNumber = data["syndicateNumber"] as? String ?: "",
                governorate = gov,
                isVerified = data["isVerified"] as? Boolean ?: true,
                verificationStatus = vStatus,
                verificationTier = vTier,
                verificationNotes = data["verificationNotes"] as? String,
                trustScore = (data["trustScore"] as? Number)?.toInt() ?: 95,
                subscriptionExpiryMillis = (data["subscriptionExpiryMillis"] as? Number)?.toLong(),
                ownerPackageTier = pkgTier,
                ownerPackageExpiryMillis = (data["ownerPackageExpiryMillis"] as? Number)?.toLong(),
                paygListingsBoughtCount = (data["paygListingsBoughtCount"] as? Number)?.toInt() ?: 0
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

enum class MemberVerificationStatus(val displayName: String, val description: String) {
    UNVERIFIED("Unverified", "Upload required identity credentials to activate full workspace access"),
    PENDING_REVIEW("Under Review", "Credential documents submitted and pending compliance review"),
    VERIFIED("Verified & Accredited", "Identity, professional syndicate license, and credentials validated"),
    ACTION_REQUIRED("Action Required", "One or more documents require updated scans or re-submission")
}

enum class VerificationTier(val displayName: String, val levelNumber: Int, val badgeTitle: String) {
    TIER_1_BASIC("Basic Identity", 1, "Level 1: ID Verified"),
    TIER_2_PROFESSIONAL("Syndicate & License Accredited", 2, "Level 2: Licensed Member"),
    TIER_3_COMMERCIAL_HOST("Commercial & Premises Certified", 3, "Level 3: Verified Commercial Host")
}

enum class DocumentType(
    val title: String,
    val officialLebaneseLabel: String,
    val description: String,
    val requiredFor: List<UserRole>,
    val placeholderDocNumber: String,
    val iconName: String = "Badge"
) {
    SYNDICATE_CARD(
        title = "Syndicate / Order Membership Card",
        officialLebaneseLabel = "بطاقة انتساب النقابة (OEA / LOP / BBA)",
        description = "Official active membership card from your professional syndicate (Order of Engineers, Order of Physicians, Bar Association, etc.)",
        requiredFor = listOf(UserRole.PROFESSIONAL),
        placeholderDocNumber = "e.g. OEA-8842, LOP-7193, BBA-4421"
    ),
    NATIONAL_ID(
        title = "National Identity Card / Passport",
        officialLebaneseLabel = "الهوية اللبنانية / جواز السفر / بيان قيد إفرادي",
        description = "Government-issued biometric ID card, valid Lebanese passport, or individual civil registry extract (Bayan Qayd)",
        requiredFor = listOf(UserRole.PROFESSIONAL, UserRole.SPACE_OWNER),
        placeholderDocNumber = "e.g. ID-00192847 or PASS-RL88291"
    ),
    PRACTICE_LICENSE(
        title = "Professional Practice Permit / License",
        officialLebaneseLabel = "إذن مزاولة المهنة / ترخيص وزارة الصحة أو الأشغال",
        description = "Official decree or permit authorizing independent professional practice in the Republic of Lebanon",
        requiredFor = listOf(UserRole.PROFESSIONAL),
        placeholderDocNumber = "e.g. LIC-MOPH-2024-918"
    ),
    COMMERCIAL_REGISTER(
        title = "Commercial Register Extract (Sijil Tejari)",
        officialLebaneseLabel = "إذاعة تجارية / سجل تجاري حديث",
        description = "Official Ministry of Justice commercial registration certificate dated within the last 6 months",
        requiredFor = listOf(UserRole.SPACE_OWNER),
        placeholderDocNumber = "e.g. CR-BEI-84920"
    ),
    TITLE_DEED_OR_LEASE(
        title = "Premises Title Deed (Tabou) or Lease Contract",
        officialLebaneseLabel = "سند ملكية (طابو) أو عقد إيجار تجاري مصدق",
        description = "Certified property ownership title (Sanad Melkiyeh) or commercial lease authorizing subleasing/coworking",
        requiredFor = listOf(UserRole.SPACE_OWNER),
        placeholderDocNumber = "e.g. TABOU-ACH-402/2021"
    ),
    TAX_REGISTRATION(
        title = "Tax ID Registration (Raqam Mali)",
        officialLebaneseLabel = "شهادة التسجيل المالي (الرقم المالي)",
        description = "Ministry of Finance official tax registration certificate / financial registration number",
        requiredFor = listOf(UserRole.PROFESSIONAL, UserRole.SPACE_OWNER),
        placeholderDocNumber = "e.g. MOF-774921-601"
    )
}

enum class DocumentStatus(val displayName: String) {
    NOT_UPLOADED("Not Uploaded"),
    PENDING_REVIEW("Pending Review"),
    VERIFIED("Verified & Approved"),
    REJECTED("Revision Needed")
}

data class CredentialDocument(
    val id: String = "DOC-" + java.util.UUID.randomUUID().toString().take(8).uppercase(),
    val userId: String,
    val type: DocumentType,
    val fileName: String? = null,
    val fileSizeKb: Int = 0,
    val uploadedAt: Long? = null,
    val status: DocumentStatus = DocumentStatus.NOT_UPLOADED,
    val documentNumber: String = "",
    val issuingAuthority: String = "",
    val expiryDate: String = "",
    val rejectionReason: String? = null,
    val fileUri: String? = null,
    val verificationHash: String? = null,
    val reviewerNotes: String? = null
) {
    val isUploaded: Boolean get() = status != DocumentStatus.NOT_UPLOADED
    val isVerified: Boolean get() = status == DocumentStatus.VERIFIED
    val isPending: Boolean get() = status == DocumentStatus.PENDING_REVIEW
    val isRejected: Boolean get() = status == DocumentStatus.REJECTED

    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "type" to type.name,
            "fileName" to fileName,
            "fileSizeKb" to fileSizeKb,
            "uploadedAt" to uploadedAt,
            "status" to status.name,
            "documentNumber" to documentNumber,
            "issuingAuthority" to issuingAuthority,
            "expiryDate" to expiryDate,
            "rejectionReason" to rejectionReason,
            "fileUri" to fileUri,
            "verificationHash" to verificationHash,
            "reviewerNotes" to reviewerNotes,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    companion object {
        const val COLLECTION_PATH = "user_credentials"

        fun fromFirestoreMap(docId: String, data: Map<String, Any?>): CredentialDocument {
            val typeStr = data["type"] as? String ?: DocumentType.NATIONAL_ID.name
            val type = runCatching { DocumentType.valueOf(typeStr) }.getOrDefault(DocumentType.NATIONAL_ID)

            val statusStr = data["status"] as? String ?: DocumentStatus.NOT_UPLOADED.name
            val stat = runCatching { DocumentStatus.valueOf(statusStr) }.getOrDefault(DocumentStatus.NOT_UPLOADED)

            return CredentialDocument(
                id = docId,
                userId = data["userId"] as? String ?: "",
                type = type,
                fileName = data["fileName"] as? String,
                fileSizeKb = (data["fileSizeKb"] as? Number)?.toInt() ?: 0,
                uploadedAt = (data["uploadedAt"] as? Number)?.toLong(),
                status = stat,
                documentNumber = data["documentNumber"] as? String ?: "",
                issuingAuthority = data["issuingAuthority"] as? String ?: "",
                expiryDate = data["expiryDate"] as? String ?: "",
                rejectionReason = data["rejectionReason"] as? String,
                fileUri = data["fileUri"] as? String,
                verificationHash = data["verificationHash"] as? String,
                reviewerNotes = data["reviewerNotes"] as? String
            )
        }
    }
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
    val merchantChannelId: String = "15462415",
    val merchantSource: String = "ceo@hopebearer-award.com",
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
}

data class AvatarCampaign(
    val id: String,
    val spaceId: String,
    val spaceTitle: String,
    val instagramHandle: String = "@prospace.lebanon",
    val totalReelViews: Int = 1420,
    val linkClicks: Int = 28,
    val inquiriesGenerated: Int = 9,
    val generatedCaption: String,
    val storyOverlayTag: String,
    val lastNudgeText: String = "Your collaborative workspace Reel reached 1,420 professionals in Mount Lebanon! 9 inquiries redirected to WhatsApp."
)

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
)

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


