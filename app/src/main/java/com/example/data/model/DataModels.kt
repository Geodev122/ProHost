package com.example.data.model

import com.example.data.config.MerchantConfig
import java.util.UUID

enum class SpaceType(val displayName: String, val iconName: String) {
    PRIVATE_OFFICE("Private Office", "Apartment"),
    CENTER("Center", "Business"),
    POLYCLINIC("Polyclinic", "LocalHospital"),
    COWORKING_SPACE("Co-working Space", "Groups")
}

/**
 * The legacy enum value an admin-defined Space Category id maps onto, if any —
 * the four seeded schema ids (ST-01..ST-04) and the bare enum names (what the
 * pickers fall back to when the schema is empty). Null for any other category:
 * an admin-added one has no legacy equivalent, which is exactly the case the
 * closed-enum filters could never express.
 */
fun legacySpaceTypeForCategoryId(categoryId: String?): SpaceType? = when (categoryId) {
    "ST-01", SpaceType.PRIVATE_OFFICE.name -> SpaceType.PRIVATE_OFFICE
    "ST-02", SpaceType.CENTER.name -> SpaceType.CENTER
    "ST-03", SpaceType.POLYCLINIC.name -> SpaceType.POLYCLINIC
    "ST-04", SpaceType.COWORKING_SPACE.name -> SpaceType.COWORKING_SPACE
    else -> null
}

/**
 * Category-filter match for Discovery and the Admin listings table. A null
 * [categoryId] means "all". Matches on the real spaceCategoryId first; for a
 * listing written before spaceCategoryId existed (or whose id came from the
 * empty-schema fallback list), falls back to comparing the legacy SpaceType both
 * sides map onto, so the four original categories keep matching old data.
 */
fun SpaceListing.matchesCategory(categoryId: String?): Boolean {
    if (categoryId == null) return true
    if (spaceCategoryId == categoryId) return true
    val legacy = legacySpaceTypeForCategoryId(categoryId) ?: return false
    val ownId = spaceCategoryId
    return if (ownId != null) legacySpaceTypeForCategoryId(ownId) == legacy else spaceType == legacy
}

// Shared with both the listing-creation facility toggles and the Discovery
// filter sheet, so a facility a host offers is spelled identically to the
// one a specialist filters by.
object FacilityCatalog {
    val standard = listOf(
        "24/7 Generator Electricity",
        "Continuous Water Supply",
        "High-Speed Fiber Wi-Fi",
        "HVAC Climate Control",
        "Daily Professional Cleaning",
        "Dedicated Underground Parking",
        "Client Accessibility / Elevator",
        "Reception & Admin Support"
    )
}

enum class Level2Type(val displayName: String, val iconName: String) {
    ROOMS("Room", "MeetingRoom"),
    OFFICE("Office", "Business"),
    CONFERENCE_ROOM("Conference Room", "CoPresent"),
    THEATER_TRAINING("Theater / Training Room", "School"),
    DESK_IN_SHARED_AREA("Desk in Shared Area", "Desk"),
    GYM("Gym", "FitnessCenter"),
    TRAINING_ROOM("Training Room", "Groups"),
    SPORTS_AREA("Sports Area", "SportsSoccer"),
    STUDIO("Studio", "Videocam"),
    STORAGE("Storage", "Inventory2")
}

// Legacy pair, kept only so Firestore documents written before RentalPricingConfig
// existed still deserialize (see Subdivision.fromLegacyMap / SpaceListing's own
// legacy-formula synthesis). Never written by new saves — RentalStrategyType is the
// live enum for every new SubdivisionStrategy/RentalFormula equivalent.
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

/** The one rental-pricing-strategy enum, replacing the formerly-duplicated
 *  RentalStrategy (subdivision-level) and RentalFormulaType (listing-level) — both
 *  modeled the same four real-world concepts under different names, with
 *  RentalBookingDialog previously hand-mapping one to the other on every use. */
enum class RentalStrategyType(val displayName: String) {
    MONTHLY("Monthly"),
    HOURLY("Per-Hour"),
    SHIFT_BASED("Shift-Based"),
    DAY_BASED("Day-Based")
}

data class MonthYearRange(val fromMonth: Int, val fromYear: Int, val toMonth: Int, val toYear: Int)

data class MonthlyConfig(
    val rateUsd: Double = 0.0,
    val fromMonth: Int = 1,
    val fromYear: Int = 2026,
    val isIndefinite: Boolean = true,
    val toMonth: Int? = null,
    val toYear: Int? = null,
    val excludedRanges: List<MonthYearRange> = emptyList()
)

/** Key format "<3-letter-day>|<hour-of-day-int>", e.g. "Mon|9" -> 25.0. A day/hour
 *  combination absent from this map is blank/unavailable — deliberately the only
 *  source of truth; a "select all hours for a day at one price" bulk-fill action in
 *  the host editor writes N identical entries into this same map rather than storing
 *  a second, parallel representation of the same fact. */
data class HourlyConfig(val cellPrices: Map<String, Double> = emptyMap())

enum class ShiftName(val displayName: String) {
    MORNING("Morning"), MID("Mid"), EVENING("Evening"), NIGHT("Night")
}

// Collapsed from 3 separate recurrence-tier prices (one-time / same-day-every-week /
// same-day-every-month) to a single flat price per shift — admin now prices a shift
// once; the specialist configures occurrence count (which real calendar dates, how
// many) at booking time instead of picking which of 3 pre-set commitment tiers to
// pay under. See RentalBookingDialog's calendar-date picker and
// SpaceCalculationUtils.buildBookableSlots' SHIFT_BASED branch, which now populates
// RentableSlot.pricesByRecurrence with a single BookingRecurrence.FLAT entry.
data class ShiftDefinition(
    val name: ShiftName,
    val startHour: Int = 8,
    val endHour: Int = 12,
    val isUnavailable: Boolean = false,
    val price: Double = 0.0
)

data class ShiftBasedConfig(
    val shifts: List<ShiftDefinition> = ShiftName.values().map { ShiftDefinition(name = it) },
    // day (3-letter) -> which shift names are offered that day, e.g. "Mon" -> ["MORNING", "EVENING"]
    val distribution: Map<String, List<String>> = emptyMap()
)

// Single flat price per day (mirrors ShiftDefinition.price's item 7b rework,
// same request applied to Day-Based) — a host now prices a day once; the
// specialist configures real occurrences (one-time on a specific date, or
// weekly-recurring on the same weekday up to a chosen end date) at booking
// time instead of picking which of 3 pre-set commitment tiers to pay under.
data class DayPricing(
    val price: Double = 0.0
)

data class DayBasedConfig(
    val useFacilityHours: Boolean = true,
    val customStartHour: Int? = null,
    val customEndHour: Int? = null,
    // day (3-letter) -> its 3 independent prices. A day absent here is "Not Available".
    val distribution: Map<String, DayPricing> = emptyMap()
)

/** Exactly one of [monthly]/[hourly]/[shiftBased]/[dayBased] is non-null, selected by
 *  [strategyType] — an explicit type-tag, not sealed-class polymorphism, so this
 *  round-trips through toFirestoreMap()/fromFirestoreMap() as one flat map with one
 *  active nested sub-map, mechanically, like every other nested object in this file. */
data class RentalPricingConfig(
    val strategyType: RentalStrategyType,
    val monthly: MonthlyConfig? = null,
    val hourly: HourlyConfig? = null,
    val shiftBased: ShiftBasedConfig? = null,
    val dayBased: DayBasedConfig? = null
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "strategyType" to strategyType.name,
        "monthly" to monthly?.let {
            mapOf(
                "rateUsd" to it.rateUsd, "fromMonth" to it.fromMonth, "fromYear" to it.fromYear,
                "isIndefinite" to it.isIndefinite, "toMonth" to it.toMonth, "toYear" to it.toYear,
                "excludedRanges" to it.excludedRanges.map { r ->
                    mapOf("fromMonth" to r.fromMonth, "fromYear" to r.fromYear, "toMonth" to r.toMonth, "toYear" to r.toYear)
                }
            )
        },
        "hourly" to hourly?.let { mapOf("cellPrices" to it.cellPrices) },
        "shiftBased" to shiftBased?.let { sbc ->
            mapOf(
                "shifts" to sbc.shifts.map { s ->
                    mapOf(
                        "name" to s.name.name, "startHour" to s.startHour, "endHour" to s.endHour,
                        "isUnavailable" to s.isUnavailable,
                        "price" to s.price
                    )
                },
                "distribution" to sbc.distribution
            )
        },
        "dayBased" to dayBased?.let { dbc ->
            mapOf(
                "useFacilityHours" to dbc.useFacilityHours, "customStartHour" to dbc.customStartHour,
                "customEndHour" to dbc.customEndHour,
                "distribution" to dbc.distribution.mapValues { (_, v) -> mapOf("price" to v.price) }
            )
        }
    )

    /** Whether this config carries at least one real, non-zero price — mirrors
     *  functions/src/listings/publishValidation.ts's structuredConfigHasRealPrice
     *  exactly, so the client's Publish gate can never disagree with the server-side
     *  re-check that demotes an underpriced listing straight back to Draft. Used to
     *  close a real gap: Publish used to only check the pin/ownership-doc/photo were
     *  present, never that any actual price was configured, so a listing could flip
     *  live with a "published successfully" toast and then get silently demoted
     *  moments later. */
    fun hasRealPrice(): Boolean = when (strategyType) {
        RentalStrategyType.MONTHLY -> (monthly?.rateUsd ?: 0.0) > 0.0
        RentalStrategyType.HOURLY -> hourly?.cellPrices?.values?.any { it > 0.0 } ?: false
        RentalStrategyType.SHIFT_BASED -> {
            val shiftsByName = shiftBased?.shifts?.associateBy { it.name.name } ?: emptyMap()
            shiftBased?.distribution?.values?.any { shiftNames ->
                shiftNames.any { name ->
                    val shift = shiftsByName[name]
                    shift != null && !shift.isUnavailable && shift.price > 0.0
                }
            } ?: false
        }
        RentalStrategyType.DAY_BASED -> dayBased?.distribution?.values?.any { it.price > 0.0 } ?: false
    }

    companion object {
        fun default(): RentalPricingConfig = RentalPricingConfig(RentalStrategyType.MONTHLY, monthly = MonthlyConfig())

        fun fromFirestoreMap(data: Map<*, *>): RentalPricingConfig {
            val typeStr = data["strategyType"] as? String ?: RentalStrategyType.MONTHLY.name
            val type = runCatching { RentalStrategyType.valueOf(typeStr) }.getOrDefault(RentalStrategyType.MONTHLY)
            val monthly = (data["monthly"] as? Map<*, *>)?.let { m ->
                MonthlyConfig(
                    rateUsd = (m["rateUsd"] as? Number)?.toDouble() ?: 0.0,
                    fromMonth = (m["fromMonth"] as? Number)?.toInt() ?: 1,
                    fromYear = (m["fromYear"] as? Number)?.toInt() ?: 2026,
                    isIndefinite = m["isIndefinite"] as? Boolean ?: true,
                    toMonth = (m["toMonth"] as? Number)?.toInt(),
                    toYear = (m["toYear"] as? Number)?.toInt(),
                    excludedRanges = (m["excludedRanges"] as? List<*>)?.mapNotNull { r ->
                        (r as? Map<*, *>)?.let {
                            MonthYearRange(
                                fromMonth = (it["fromMonth"] as? Number)?.toInt() ?: 1,
                                fromYear = (it["fromYear"] as? Number)?.toInt() ?: 2026,
                                toMonth = (it["toMonth"] as? Number)?.toInt() ?: 1,
                                toYear = (it["toYear"] as? Number)?.toInt() ?: 2026
                            )
                        }
                    } ?: emptyList()
                )
            }
            val hourly = (data["hourly"] as? Map<*, *>)?.let { h ->
                val prices = (h["cellPrices"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    (k as? String)?.let { key -> (v as? Number)?.toDouble()?.let { key to it } }
                }?.toMap() ?: emptyMap()
                HourlyConfig(cellPrices = prices)
            }
            val shiftBased = (data["shiftBased"] as? Map<*, *>)?.let { sb ->
                val shifts = (sb["shifts"] as? List<*>)?.mapNotNull { s ->
                    (s as? Map<*, *>)?.let {
                        val nameStr = it["name"] as? String ?: ShiftName.MORNING.name
                        val name = runCatching { ShiftName.valueOf(nameStr) }.getOrDefault(ShiftName.MORNING)
                        // New shape writes a single "price" field directly; an older
                        // document instead carries a nested "pricing" map with 3
                        // legacy recurrence-tier fields (one-time/weekly/monthly),
                        // which fromLegacyShape's SHIFT branch always set to the same
                        // flat value anyway — read whichever of the 3 is nonzero
                        // first, so an old document keeps its real price instead of
                        // reading 0 until the host happens to re-save it.
                        val price = (it["price"] as? Number)?.toDouble() ?: run {
                            val pricingMap = it["pricing"] as? Map<*, *>
                            (pricingMap?.get("oneTimePrice") as? Number)?.toDouble()
                                ?.takeIf { p -> p > 0.0 }
                                ?: (pricingMap?.get("sameDayEveryWeekPrice") as? Number)?.toDouble()?.takeIf { p -> p > 0.0 }
                                ?: (pricingMap?.get("monthlyRecurrencePrice") as? Number)?.toDouble()
                                ?: 0.0
                        }
                        ShiftDefinition(
                            name = name,
                            startHour = (it["startHour"] as? Number)?.toInt() ?: 8,
                            endHour = (it["endHour"] as? Number)?.toInt() ?: 12,
                            isUnavailable = it["isUnavailable"] as? Boolean ?: false,
                            price = price
                        )
                    }
                } ?: ShiftName.values().map { ShiftDefinition(name = it) }
                val distribution = (sb["distribution"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    (k as? String)?.let { key -> (v as? List<*>)?.mapNotNull { it as? String }?.let { key to it } }
                }?.toMap() ?: emptyMap()
                ShiftBasedConfig(shifts = shifts, distribution = distribution)
            }
            val dayBased = (data["dayBased"] as? Map<*, *>)?.let { db ->
                val distribution = (db["distribution"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    (k as? String)?.let { key ->
                        (v as? Map<*, *>)?.let {
                            // New shape writes a single "price" field directly; an older
                            // document instead carries the 3 legacy recurrence-tier
                            // fields (one-time/monthly/weekly) — read whichever of the 3
                            // is nonzero first, mirroring ShiftDefinition's identical
                            // backward-compat read above.
                            val price = (it["price"] as? Number)?.toDouble() ?: run {
                                (it["oneTimePrice"] as? Number)?.toDouble()?.takeIf { p -> p > 0.0 }
                                    ?: (it["sameDayEachWeekPrice"] as? Number)?.toDouble()?.takeIf { p -> p > 0.0 }
                                    ?: (it["sameDayEachMonthPrice"] as? Number)?.toDouble()
                                    ?: 0.0
                            }
                            key to DayPricing(price = price)
                        }
                    }
                }?.toMap() ?: emptyMap()
                DayBasedConfig(
                    useFacilityHours = db["useFacilityHours"] as? Boolean ?: true,
                    customStartHour = (db["customStartHour"] as? Number)?.toInt(),
                    customEndHour = (db["customEndHour"] as? Number)?.toInt(),
                    distribution = distribution
                )
            }
            return RentalPricingConfig(type, monthly, hourly, shiftBased, dayBased)
        }

        /** Old model allowed a LIST of formulas/strategies; new model is exactly one
         *  strategy per division, so this takes the first — documented, intentional
         *  narrowing. A listing that offered e.g. both Hourly and Full-Month shows
         *  only its first formula until the host re-opens the pricing editor and
         *  explicitly re-picks; it never crashes and never silently drops the listing.
         *  Called both from fromFirestoreMap (a document with no "pricing" key at
         *  all) and eagerly from every write path that still constructs a
         *  RentalFormula/SubdivisionStrategy directly (CreateListingDialog,
         *  SubdivisionEditorSection), so "pricing" is always correct the moment
         *  it's first written, not only on a later read of a genuinely old
         *  pre-existing document. */
        fun fromLegacyFormula(formula: RentalFormula?): RentalPricingConfig {
            if (formula == null) return default()
            val oldTypeStr = formula.type.name
            val startHour = formula.startHour.substringBefore(':').trim().toIntOrNull() ?: 8
            val endHour = formula.endHour.substringBefore(':').trim().toIntOrNull() ?: 18
            return fromLegacyShape(oldTypeStr, formula.rateUsd, formula.daysOfWeek, startHour, endHour)
        }

        fun fromLegacySubdivisionStrategy(strategy: SubdivisionStrategy?): RentalPricingConfig {
            if (strategy == null) return default()
            val hoursOrShifts = strategy.availableHoursOrShifts
            val startHour = hoursOrShifts.substringBefore('-').trim().substringBefore(':').trim().toIntOrNull() ?: 8
            val endHour = hoursOrShifts.substringAfter('-', "").trim().substringBefore(':').trim().toIntOrNull() ?: 18
            val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
            val mappedType = when (strategy.strategy) {
                RentalStrategy.HOURLY -> "HOURLY"
                RentalStrategy.SHIFT_BASED -> "SHIFT"
                RentalStrategy.DAILY -> "DAY_PER_WEEK"
                RentalStrategy.MONTHLY -> "FULL_MONTH"
            }
            return fromLegacyShape(mappedType, strategy.rateUsd, days, startHour, endHour)
        }

        private fun fromLegacyShape(oldTypeStr: String, rate: Double, days: List<String>, startHour: Int, endHour: Int): RentalPricingConfig =
            when (oldTypeStr) {
                "HOURLY" -> RentalPricingConfig(
                    strategyType = RentalStrategyType.HOURLY,
                    hourly = HourlyConfig(cellPrices = days.flatMap { day ->
                        (startHour until endHour).map { "$day|$it" to rate }
                    }.toMap())
                )
                "SHIFT" -> RentalPricingConfig(
                    strategyType = RentalStrategyType.SHIFT_BASED,
                    shiftBased = ShiftBasedConfig(
                        shifts = ShiftName.values().map {
                            ShiftDefinition(
                                name = it, startHour = startHour, endHour = endHour,
                                isUnavailable = it != ShiftName.MORNING,
                                price = rate
                            )
                        },
                        distribution = days.associateWith { listOf(ShiftName.MORNING.name) }
                    )
                )
                "DAY_PER_WEEK" -> RentalPricingConfig(
                    strategyType = RentalStrategyType.DAY_BASED,
                    dayBased = DayBasedConfig(
                        useFacilityHours = false, customStartHour = startHour, customEndHour = endHour,
                        distribution = days.associateWith { DayPricing(price = rate) }
                    )
                )
                else -> RentalPricingConfig(
                    strategyType = RentalStrategyType.MONTHLY,
                    monthly = MonthlyConfig(rateUsd = rate, isIndefinite = true)
                )
            }
    }
}

data class Subdivision(
    val id: String = "SUB-" + java.util.UUID.randomUUID().toString().take(6).uppercase(),
    val name: String,
    val type: Level2Type,
    val imageUrls: List<String> = emptyList(),
    val amenities: List<String> = emptyList(),
    val pricing: RentalPricingConfig = RentalPricingConfig.default(),
    // Legacy, read-only: populated only when deserializing a document saved before
    // RentalPricingConfig existed and never re-saved since. New saves always leave
    // this empty — pricing lives in [pricing] instead. Kept only so
    // toFirestoreMap()/older-client compatibility isn't silently broken.
    val rentalStrategies: List<SubdivisionStrategy> = emptyList(),
    // Null (the common case) means this division follows the whole space's own
    // SpaceOperatingSchedule (SpaceListing.schedule) — e.g. a single exam room that
    // closes earlier than the rest of a Polyclinic, or a room open Sunday when the
    // building otherwise isn't. Non-null overrides operating days/hours/blackouts
    // for this division only; SpaceCalculationUtils.buildAllSlotsForSpace is the one
    // place that resolves this (scheduleOverride ?: space.schedule) into real slots,
    // so every screen that reads slots automatically respects it.
    val scheduleOverride: SpaceOperatingSchedule? = null
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
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "openingHour" to openingHour,
        "closingHour" to closingHour,
        "operatingDays" to operatingDays,
        "isSundayOperating" to isSundayOperating,
        "blackoutSlots" to blackoutSlots.map {
            mapOf(
                "id" to it.id, "dayOfWeek" to it.dayOfWeek,
                "startTime" to it.startTime, "endTime" to it.endTime, "reason" to it.reason
            )
        }
    )

    companion object {
        // Shared by both SpaceListing.schedule (top-level, defaults to
        // SpaceOperatingSchedule() when absent) and Subdivision.scheduleOverride
        // (nullable — absent genuinely means "no override", not "use defaults").
        fun fromFirestoreMap(map: Map<*, *>?): SpaceOperatingSchedule? {
            if (map == null) return null
            val blackoutList = (map["blackoutSlots"] as? List<*>)?.mapNotNull { bItem ->
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
            return SpaceOperatingSchedule(
                openingHour = map["openingHour"] as? String ?: "08:00",
                closingHour = map["closingHour"] as? String ?: "20:00",
                operatingDays = (map["operatingDays"] as? List<*>)?.mapNotNull { it as? String }
                    ?: listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
                blackoutSlots = blackoutList,
                isSundayOperating = map["isSundayOperating"] as? Boolean ?: false
            )
        }
    }
}

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
    // Real ISO calendar dates ("2026-09-15") the specialist committed to for a
    // Shift-Based booking — only this, not selectedDays' weekday-only granularity,
    // is precise enough to lock the exact dates a shift was accepted for rather
    // than the whole weekday indefinitely. Empty for every other strategy and for
    // bookings made before this field existed (isSlotLocked/findAcceptConflict
    // fall back to weekday-level locking in that case).
    val selectedCalendarDates: List<String> = emptyList(),
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
    val cancelledByRole: String? = null,
    // Record-keeping only — rent settlement happens entirely outside the app
    // (cash/Whish-direct/wire transfer between host and specialist), so ProHost
    // has no way to know whether or when it actually happened. A simple mutual
    // acknowledgment closes that gap a little without reintroducing in-app
    // payment processing: either side can mark their own flag, independently.
    val paymentAcknowledgedByHost: Boolean = false,
    val paymentAcknowledgedBySpecialist: Boolean = false
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
            "selectedCalendarDates" to selectedCalendarDates,
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
            "replacesBookingId" to replacesBookingId,
            "agreementUrl" to agreementUrl,
            "cancellationReasonCode" to cancellationReasonCode,
            "cancellationNote" to cancellationNote,
            "cancelledByRole" to cancelledByRole,
            "paymentAcknowledgedByHost" to paymentAcknowledgedByHost,
            "paymentAcknowledgedBySpecialist" to paymentAcknowledgedBySpecialist
        )
    }

    companion object {
        const val COLLECTION_PATH = "booking_requests"

        @Suppress("UNCHECKED_CAST")
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
                selectedCalendarDates = (data["selectedCalendarDates"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
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
                replacesBookingId = data["replacesBookingId"] as? String,
                agreementUrl = data["agreementUrl"] as? String,
                cancellationReasonCode = data["cancellationReasonCode"] as? String,
                cancellationNote = data["cancellationNote"] as? String,
                cancelledByRole = data["cancelledByRole"] as? String,
                paymentAcknowledgedByHost = data["paymentAcknowledgedByHost"] as? Boolean ?: false,
                paymentAcknowledgedBySpecialist = data["paymentAcknowledgedBySpecialist"] as? Boolean ?: false
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

/** Which of the two optional proofs a Pro Host uploaded to earn the Listing Verified
 *  badge — see [SpaceListing.verificationDocUrl]'s doc comment. Neither is mandatory
 *  to publish; both are mandatory only to earn the badge. */
enum class ListingVerificationDocType { RERENTAL_AUTHORIZATION, SELF_OWNERSHIP_PROOF }

/** A listing's own lifecycle state, independent of the owner's account status
 *  ([SpaceListing.isOwnerSuspended]) or the owner's package/subscription status
 *  ([SpaceListing.isActiveSubscription]) — this is the one the host controls
 *  themselves (Save as Draft / Publish / Pause / Resume). */
enum class ListingStatus { DRAFT, ACTIVE, PAUSED }

/** One row of the Admin Console's hashtag usage analytics (spec 1.4) — read-only,
 *  never round-tripped back to Firestore, so no toFirestoreMap/fromFirestoreMap pair. */
data class HashtagUsageEntry(
    val tag: String,
    val count: Int,
    val governorate: String,
    val lastUsedAtMillis: Long
)

/** Which branch the host took at the mandatory pre-Step-1 ownership gate (spec 1.5)
 *  — purely informational, recorded alongside [SpaceListing.ownershipProofUrl].
 *  Distinct from [ListingVerificationDocType], which records the optional,
 *  post-publish "Listing Verified" badge path instead; a listing can have both, one,
 *  or neither set, and they're never derived from one another. */
enum class OwnershipRole { OWNER, RERENTER }

data class SpaceListing(
    val id: String,
    val title: String,
    // Max 100 characters, enforced client-side (CreateListingDialog) — not a
    // Firestore-rules-level constraint, matching how every other free-text field on
    // this model (visitorPolicy, scheduleDescription, etc.) is validated.
    val description: String = "",
    val spaceType: SpaceType,
    // Real category identity once the admin-defined Space Category system is in use —
    // a SchemaItem.id from SpaceArchitectureSchema.spaceTypes (category == "SPACE_TYPE").
    // Null for listings created before this field existed, or if the schema was empty
    // at creation time and the wizard fell back to the legacy 4-value picker. [spaceType]
    // above is kept in sync (best-effort mapping, see CreateListingDialog.buildListing)
    // purely so every existing legacy reader (Discovery filters, badges, PAYG fee lookup
    // for the original 4 types) keeps working unchanged; it is no longer the source of
    // truth for display name/icon/pricing once [spaceCategoryId] is set.
    val spaceCategoryId: String? = null,
    // Denormalized snapshot of the SchemaItem's name at the moment this listing was
    // created/saved — survives the admin later renaming or deleting that SchemaItem,
    // the same reasoning as ownerName/ownerPhone being denormalized elsewhere on this
    // model rather than re-joined live.
    val spaceCategoryName: String? = null,
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
    val pricing: RentalPricingConfig = RentalPricingConfig.default(),
    // Legacy, read-only: populated only when deserializing a document saved before
    // RentalPricingConfig existed and never re-saved since. New saves always leave
    // this empty — pricing lives in [pricing] instead.
    val rentalFormulas: List<RentalFormula> = emptyList(),
    val rules: PremisesRules,
    val schedule: SpaceOperatingSchedule = SpaceOperatingSchedule(),
    val ownerId: String,
    val ownerName: String,
    val ownerPhone: String,
    val ownerEmail: String,
    // Proof of ownership / right to rent this specific space out, uploaded by the
    // Pro Host at listing-creation time. Kept on file — nobody reviews/approves it,
    // there is no admin accreditation workflow anymore (see AppUser's doc comment).
    // Required to publish; NOT the same thing as verificationDocUrl below, which is
    // optional and earns the badge rather than gating anything.
    val ownershipProofUrl: String? = null,
    // Which branch (owner / re-renter) the host took at the mandatory pre-Step-1
    // gate that produced ownershipProofUrl — purely informational.
    val ownershipDocRole: OwnershipRole? = null,
    // Earns the Listing Verified badge (isVerified below) — a sibling to
    // ownershipProofUrl, deliberately not a reuse of it: ownershipProofUrl is
    // required-but-unchecked at publish time, this is optional-but-checked
    // (requestListingVerification only flips isVerified once this is non-null).
    // Either RERENTAL_AUTHORIZATION (a statement signed by the real property owner
    // granting this Pro Host permission to re-rent the space — see the downloadable
    // template in legal/RerentalAuthorizationTemplate.kt) or SELF_OWNERSHIP_PROOF
    // (the Pro Host is the real owner, not re-renting) qualifies.
    val verificationDocUrl: String? = null,
    val verificationDocType: ListingVerificationDocType? = null,
    // Defaults false for newly-created listings — genuinely earned via
    // requestListingVerification (Admin SDK only, gated on verificationDocUrl) or an
    // Admin override, not set unconditionally true at creation like it used to be.
    // Existing listings keep whatever value they already had; only the default for
    // new creates changed.
    val isVerified: Boolean = false,
    val isActiveSubscription: Boolean = true,
    // Mirrored by setAccountSuspended.ts (Admin SDK) onto every listing this owner
    // has when their account is suspended/reactivated. Hides the listing from
    // public Discovery (firestore.rules). Now included in toFirestoreMap() below
    // (echoing back whatever value this object already holds, always synced from
    // the live listener — see updateUser's doc comment on why echoing a
    // server-authoritative field back unchanged is safe, unlike echoing a
    // possibly-stale one) — required so every listing always has a real,
    // queryable value: FirestoreService's non-admin Discovery listener now
    // filters ON this field directly (isOwnerSuspended == false) to satisfy
    // firestore.rules' read rule, and Firestore's equality filters never match a
    // document where the field is simply absent — a listing saved before this
    // field existed would need a one-time backfill (the admin-triggered tool
    // that did this was removed once confirmed no live listing still lacked it).
    val isOwnerSuspended: Boolean = false,
    // Mirrored true onto every listing owned by a PRO_HOST whose package lapsed
    // with no renewal (functions/src/packages/expirePackages.ts's downgrade
    // path) — hides the listing from a fresh Discovery browse
    // (DiscoveryViewModel's isLiveListing filter) without unpublishing it or
    // touching status/isActiveSubscription. Deliberately does NOT block a
    // specialist who already has an ACCEPTED booking at this listing from
    // still reaching it via My Bookings — SpaceDetailsScreen shows a "host is
    // in verification process" note there instead. Cleared automatically the
    // moment the host's package renews (entitlements.ts's
    // restoreListingsAfterRenewal). Admin-SDK-only, same protected-field
    // pattern as isOwnerSuspended — see firestore.rules.
    val isOwnerPackageLapsed: Boolean = false,
    // Set by reviewIdDocument.ts (Admin SDK) when an admin rejects a PRO_HOST's
    // ID document. Hides listings from Discovery until the host re-submits a valid
    // ID (submitIdDocument.ts clears it). Same server-only pattern as isOwnerSuspended.
    val isOwnerIdRejected: Boolean = false,
    val subscriptionExpiryMillis: Long = 0L,
    val imageUrls: List<String> = emptyList(),
    val videoTourDurationSec: Int = 10,
    val baseMonthlyRateUsd: Double = 450.0,
    val avatarEngagementViews: Int = 0,
    val avatarInquiryClicks: Int = 0,
    val subdivisions: List<Subdivision> = emptyList(),
    // Denormalized from the owner's own AppUser.idDocumentUrl at listing-creation
    // time — the ID-Verified badge is an account-level fact, but user_profiles' read
    // rule only lets a user read their own profile, so a Specialist viewing this
    // listing has no other way to see whether the host has ID Verified status.
    // Mirrors the existing pattern of denormalizing ownerName/ownerPhone/etc. onto
    // the listing for exactly the same cross-role-visibility reason.
    val ownerIsIdVerified: Boolean = false,
    val ownerProfilePictureUrl: String? = null,
    // The host's own lifecycle control (Draft while building it, Active once
    // published, Paused to take it off the market without deleting it) — distinct
    // from isActiveSubscription (billing) and isOwnerSuspended (moderation), which
    // the host doesn't control themselves.
    val status: ListingStatus = ListingStatus.ACTIVE,
    // Set only by the server (onWorkspaceListingPublishValidation, functions/src/
    // listings/publishValidation.ts) the moment it demotes an invalid ACTIVE
    // listing back to Draft — e.g. a Draft auto-published by a settled payment
    // that never actually had real pricing configured. Purely a UI hint, not a
    // gate: the host's own next save always writes this back to emptyList()
    // (see toFirestoreMap below), and the server re-populates it truthfully if
    // the listing is still genuinely invalid.
    val publishBlockedReasons: List<String> = emptyList(),
    // Server-stamped once, at creation, by onWorkspaceListingCreated
    // (functions/src/listings/listingCountTracker.ts) — never written by the
    // client (absent from toFirestoreMap below, same convention as
    // isOwnerSuspended) so it can't be backdated/spoofed. Null for any listing
    // created before this field existed; it is not backfilled retroactively.
    // Feeds the Owners & Payments tab's "listings published by date" chart.
    val createdAtMillis: Long? = null,
    // Server-stamped only (functions/src/users/favoritesSync.ts, a Firestore trigger
    // diffing AppUser.savedSpaceIds on every change) — never written by the client
    // (absent from toFirestoreMap below), so a host can't inflate their own count.
    // Surfaced on OwnerAnalyticsScreen as the concrete "listing performance" signal
    // behind a specialist pressing/unpressing the heart icon.
    val favoriteCount: Int = 0
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "title" to title,
            "description" to description,
            "spaceType" to spaceType.name,
            "spaceCategoryId" to spaceCategoryId,
            "spaceCategoryName" to spaceCategoryName,
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
            "pricing" to pricing.toFirestoreMap(),
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
                    "pricing" to sub.pricing.toFirestoreMap(),
                    "rentalStrategies" to sub.rentalStrategies.map { strat ->
                        mapOf(
                            "strategy" to strat.strategy.name,
                            "rateUsd" to strat.rateUsd,
                            "minDuration" to strat.minDuration,
                            "availableHoursOrShifts" to strat.availableHoursOrShifts
                        )
                    },
                    "scheduleOverride" to sub.scheduleOverride?.toFirestoreMap()
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
            "schedule" to schedule.toFirestoreMap(),
            "ownerId" to ownerId,
            "ownerName" to ownerName,
            "ownerPhone" to ownerPhone,
            "ownerEmail" to ownerEmail,
            "ownershipProofUrl" to ownershipProofUrl,
            "ownershipDocRole" to ownershipDocRole?.name,
            "verificationDocUrl" to verificationDocUrl,
            "verificationDocType" to verificationDocType?.name,
            "isVerified" to isVerified,
            "isActiveSubscription" to isActiveSubscription,
            "isOwnerSuspended" to isOwnerSuspended,
            "isOwnerPackageLapsed" to isOwnerPackageLapsed,
            "subscriptionExpiryMillis" to subscriptionExpiryMillis,
            "imageUrls" to imageUrls,
            "videoTourDurationSec" to videoTourDurationSec,
            "baseMonthlyRateUsd" to baseMonthlyRateUsd,
            "avatarEngagementViews" to avatarEngagementViews,
            "avatarInquiryClicks" to avatarInquiryClicks,
            "ownerIsIdVerified" to ownerIsIdVerified,
            "status" to status.name,
            "publishBlockedReasons" to publishBlockedReasons,
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

            val schedule = SpaceOperatingSchedule.fromFirestoreMap(data["schedule"] as? Map<*, *>) ?: SpaceOperatingSchedule()

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

                    val subPricing = (sMap["pricing"] as? Map<*, *>)?.let { RentalPricingConfig.fromFirestoreMap(it) }
                        ?: RentalPricingConfig.fromLegacySubdivisionStrategy(stratsList.firstOrNull())

                    Subdivision(
                        id = sMap["id"] as? String ?: ("SUB-" + UUID.randomUUID().toString().take(6)),
                        name = sMap["name"] as? String ?: "Subdivision Unit",
                        type = lvlType,
                        imageUrls = (sMap["imageUrls"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        amenities = (sMap["amenities"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        pricing = subPricing,
                        rentalStrategies = stratsList,
                        scheduleOverride = SpaceOperatingSchedule.fromFirestoreMap(sMap["scheduleOverride"] as? Map<*, *>)
                    )
                }
            } ?: emptyList()

            return SpaceListing(
                id = docId,
                title = data["title"] as? String ?: "Executive Workspace",
                description = data["description"] as? String ?: "",
                spaceType = spaceType,
                spaceCategoryId = data["spaceCategoryId"] as? String,
                spaceCategoryName = data["spaceCategoryName"] as? String,
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
                pricing = (data["pricing"] as? Map<*, *>)?.let { RentalPricingConfig.fromFirestoreMap(it) }
                    ?: RentalPricingConfig.fromLegacyFormula(formulasList.firstOrNull()),
                rentalFormulas = formulasList,
                rules = rules,
                schedule = schedule,
                ownerId = data["ownerId"] as? String ?: "",
                ownerName = data["ownerName"] as? String ?: "Workspace Host",
                ownerPhone = data["ownerPhone"] as? String ?: "",
                ownerEmail = data["ownerEmail"] as? String ?: "",
                ownershipProofUrl = data["ownershipProofUrl"] as? String,
                ownershipDocRole = (data["ownershipDocRole"] as? String)?.let {
                    runCatching { OwnershipRole.valueOf(it) }.getOrNull()
                },
                verificationDocUrl = data["verificationDocUrl"] as? String,
                verificationDocType = (data["verificationDocType"] as? String)?.let {
                    runCatching { ListingVerificationDocType.valueOf(it) }.getOrNull()
                },
                // Existing documents predating this field have no isVerified key at all —
                // fromFirestoreMap's `?: true` used to mean "assume verified"; that's
                // wrong for a badge that must now be earned, so a missing key here reads
                // as not-yet-verified rather than silently grandfathering every existing
                // listing in as verified.
                isVerified = data["isVerified"] as? Boolean ?: false,
                isActiveSubscription = data["isActiveSubscription"] as? Boolean ?: true,
                isOwnerSuspended = data["isOwnerSuspended"] as? Boolean ?: false,
                isOwnerPackageLapsed = data["isOwnerPackageLapsed"] as? Boolean ?: false,
                isOwnerIdRejected = data["isOwnerIdRejected"] as? Boolean ?: false,
                subscriptionExpiryMillis = (data["subscriptionExpiryMillis"] as? Number)?.toLong() ?: 0L,
                imageUrls = (data["imageUrls"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                videoTourDurationSec = (data["videoTourDurationSec"] as? Number)?.toInt() ?: 10,
                baseMonthlyRateUsd = (data["baseMonthlyRateUsd"] as? Number)?.toDouble() ?: 450.0,
                avatarEngagementViews = (data["avatarEngagementViews"] as? Number)?.toInt() ?: 0,
                avatarInquiryClicks = (data["avatarInquiryClicks"] as? Number)?.toInt() ?: 0,
                ownerIsIdVerified = data["ownerIsIdVerified"] as? Boolean ?: false,
                ownerProfilePictureUrl = data["ownerProfilePictureUrl"] as? String,
                status = (data["status"] as? String)?.let {
                    runCatching { ListingStatus.valueOf(it) }.getOrNull()
                } ?: ListingStatus.ACTIVE,
                publishBlockedReasons = (data["publishBlockedReasons"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                subdivisions = subsList,
                createdAtMillis = (data["createdAtMillis"] as? Number)?.toLong(),
                favoriteCount = (data["favoriteCount"] as? Number)?.toInt() ?: 0
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
    val userId: String = "",
    // Written by initiateWhishPayment.ts for every transaction — today always
    // "OWNER_PACKAGE" (PAYG and the old flat-fee "SUBSCRIPTION" purposes were
    // retired in Phase 15). targetId is the purchased PackagePlan's id.
    val purpose: String = "",
    val targetId: String? = null
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
            "userId" to userId,
            "purpose" to purpose,
            "targetId" to targetId
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
                userId = data["userId"] as? String ?: "",
                purpose = data["purpose"] as? String ?: "",
                targetId = data["targetId"] as? String
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
    // Server-only, written exclusively by grantEntitlement() (functions/src/lib/
    // entitlements.ts) — the id of the admin-defined PackagePlan (package_plans/main)
    // this account currently holds, or null if no active package. Replaces the old
    // closed OwnerPackageTier enum; a package's real price/listing-limit/validity are
    // resolved live from package_plans/main by this id, not stored redundantly here.
    val ownerPackageId: String? = null,
    // Server-only. Real, enforced expiry (functions/src/packages/expirePackages.ts,
    // an hourly scheduled function, clears ownerPackageId/this field once it lapses;
    // firestore.rules' withinListingLimit() also treats an expired-but-not-yet-swept
    // package as "no package" directly, closing the gap between lapse and the next sweep).
    val ownerPackageExpiryMillis: Long? = null,
    // Server-only, written exclusively by assignInitialRole.ts — createdAtMillis is set
    // once, the first time this uid ever gets a role claim; lastSignInAtMillis is
    // refreshed on every subsequent call (every sign-in). Never included in
    // [toFirestoreMap] so a client write can never touch either field, even by accident.
    val createdAtMillis: Long? = null,
    val lastSignInAtMillis: Long? = null,
    // Server-only, written exclusively by assignInitialRole.ts on the same
    // account-creation branch that stamps createdAtMillis — the record that this
    // account actually accepted the Terms of Use/Privacy Policy at registration,
    // not just that a checkbox rendered on the screen. consentVersion is the
    // LegalContent.EFFECTIVE_DATE string in effect at the moment of acceptance
    // (Cloud Functions can't import the Kotlin LegalContent object directly, so
    // this is a small manually-kept-in-sync literal there, same reasoning as
    // functions/src/lib/roles.ts's AppRole mirroring UserRole) — kept so a future
    // material change to the legal documents can tell which users accepted which
    // version, without needing to re-prompt everyone retroactively.
    val tosAcceptedAtMillis: Long? = null,
    val consentVersion: String? = null,
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
    val savedSpaceIds: List<String> = emptyList(),
    // Server-only KYC fields — all written exclusively by Cloud Functions (Admin SDK);
    // firestore.rules blocks direct client writes to these fields.
    // kycLevel: recomputeKycLevel.ts trigger keeps this in sync (0–3).
    // emailVerified: verifyEmailLink HTTP function sets this on link click.
    // idDocumentVerificationStatus: submitIdDocument (PENDING_REVIEW) and reviewIdDocument
    //   (APPROVED | REJECTED) are the only writers.
    val kycLevel: Int = 0,
    val emailVerified: Boolean = false,
    val idDocumentVerificationStatus: String? = null  // null | PENDING_REVIEW | APPROVED | REJECTED
) {
    // Full map — only for admin/server-side contexts (e.g. bootstrapping a new profile
    // from an admin console write). NEVER use for client-initiated profile updates;
    // firestore.rules blocks writes to protected fields (role, isVerified, ownerPackageId,
    // ownerPackageExpiryMillis, isSuspended, etc.), and including them in a client write
    // causes the *entire* write to be rejected silently whenever a Cloud Function has
    // updated one of those fields since the client last read the document.
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
            // ownerPackageId and ownerPackageExpiryMillis are written only by Cloud Functions
            // (playBillingRtdn, grantPackageToUser, expirePackages) — never by client writes.
            // Including them here would overwrite entitlement state on any admin-context full
            // document write, so they are intentionally excluded.
            "savedSpaceIds" to savedSpaceIds,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    // Safe client-side profile update map — only fields the user is allowed to edit.
    // Excludes every field guarded by firestore.rules' protectedKeys list so this map
    // never triggers a rule rejection, even after a Cloud Function has updated
    // ownerPackageId, role, isVerified, or any other server-owned field.
    fun toEditableFieldsMap(): Map<String, Any?> {
        return mapOf(
            "fullName" to fullName,
            "specialty" to specialty,
            "phone" to phone,
            "profilePictureUrl" to profilePictureUrl,
            "idDocumentUrl" to idDocumentUrl,
            "country" to country,
            "governorate" to governorate,
            "city" to city,
            "savedSpaceIds" to savedSpaceIds,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    companion object {
        const val COLLECTION_PATH = "user_profiles"

        fun fromFirestoreMap(docId: String, data: Map<String, Any?>): AppUser {
            val roleStr = data["role"] as? String ?: UserRole.SPECIALIST.name
            val role = runCatching { UserRole.valueOf(roleStr) }.getOrDefault(UserRole.SPECIALIST)

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
                ownerPackageId = data["ownerPackageId"] as? String,
                ownerPackageExpiryMillis = (data["ownerPackageExpiryMillis"] as? Number)?.toLong(),
                createdAtMillis = (data["createdAtMillis"] as? Number)?.toLong(),
                lastSignInAtMillis = (data["lastSignInAtMillis"] as? Number)?.toLong(),
                tosAcceptedAtMillis = (data["tosAcceptedAtMillis"] as? Number)?.toLong(),
                consentVersion = data["consentVersion"] as? String,
                isSuspended = data["isSuspended"] as? Boolean ?: false,
                activeListingCount = (data["activeListingCount"] as? Number)?.toInt() ?: 0,
                savedSpaceIds = (data["savedSpaceIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                kycLevel = (data["kycLevel"] as? Number)?.toInt() ?: 0,
                emailVerified = data["emailVerified"] as? Boolean ?: false,
                idDocumentVerificationStatus = data["idDocumentVerificationStatus"] as? String
            )
        }
    }
}

/**
 * A pending ID document review entry in the id_review_queue Firestore collection.
 * Created by submitIdDocument Cloud Function; read by the Admin Console ID Review tab.
 */
data class IdReviewEntry(
    val userId: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val role: String,
    val storageUrl: String,
    val submittedAt: Long,
    val status: String,          // PENDING_REVIEW | APPROVED | REJECTED
    val reviewedAt: Long? = null,
    val rejectionReason: String? = null
) {
    companion object {
        const val COLLECTION_PATH = "id_review_queue"

        fun fromFirestoreMap(docId: String, data: Map<String, Any?>): IdReviewEntry = IdReviewEntry(
            userId = docId,
            fullName = data["fullName"] as? String ?: "Unknown",
            email = data["email"] as? String ?: "",
            phone = data["phone"] as? String ?: "",
            role = data["role"] as? String ?: "SPECIALIST",
            storageUrl = data["storageUrl"] as? String ?: "",
            submittedAt = (data["submittedAt"] as? Number)?.toLong() ?: 0L,
            status = data["status"] as? String ?: "PENDING_REVIEW",
            reviewedAt = (data["reviewedAt"] as? Number)?.toLong(),
            rejectionReason = data["rejectionReason"] as? String
        )
    }
}

/**
 * An admin-defined, purchasable Pro Host package — replaces the old closed
 * OwnerPackageTier enum (2 hardcoded tiers) entirely. An admin can create/edit/
 * enable-disable/delete any number of these via the Admin Console's Packages
 * Configuration card (mirrors SchemaItem's own admin-CRUD pattern). [listingLimit]
 * null means unlimited; [validityDays] drives real, enforced expiry
 * (functions/src/packages/expirePackages.ts) rather than the old hardcoded,
 * never-actually-checked 30-day constant.
 */
data class PackagePlan(
    val id: String,
    val name: String,
    val description: String = "",
    val badgeName: String = "",
    val priceUsd: Double = 0.0,
    val listingLimit: Int? = null,
    val validityDays: Int = 30,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    /** Admin-set: featured plans receive a "Most Popular" highlight on the Subscriptions screen. */
    val isFeatured: Boolean = false,
    /** Subscription product ID in Google Play Console (e.g. "prohost_starter_30d"). Empty means Whish-only. */
    val googlePlayProductId: String = ""
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "badgeName" to badgeName,
        "priceUsd" to priceUsd,
        "listingLimit" to listingLimit,
        "validityDays" to validityDays,
        "isEnabled" to isEnabled,
        "sortOrder" to sortOrder,
        "isFeatured" to isFeatured,
        "googlePlayProductId" to googlePlayProductId
    )

    companion object {
        fun fromFirestoreMap(id: String, data: Map<String, Any?>): PackagePlan = PackagePlan(
            id = id,
            name = data["name"] as? String ?: "Package",
            description = data["description"] as? String ?: "",
            badgeName = data["badgeName"] as? String ?: "",
            priceUsd = (data["priceUsd"] as? Number)?.toDouble() ?: 0.0,
            listingLimit = (data["listingLimit"] as? Number)?.toInt(),
            validityDays = (data["validityDays"] as? Number)?.toInt() ?: 30,
            isEnabled = data["isEnabled"] as? Boolean ?: true,
            sortOrder = (data["sortOrder"] as? Number)?.toInt() ?: 0,
            isFeatured = data["isFeatured"] as? Boolean ?: false,
            googlePlayProductId = data["googlePlayProductId"] as? String ?: ""
        )
    }
}

/**
 * Wraps the admin-managed package catalog, stored keyed by id (a map, not a
 * list) inside package_plans/main — the map shape lets firestore.rules resolve
 * a specific package via a plain .get(id, null) chain, the same well-supported
 * idiom used throughout this app's rules, rather than a list-filter/scan.
 */
data class PackagePlanCatalog(val packages: Map<String, PackagePlan> = emptyMap()) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "packages" to packages.mapValues { it.value.toFirestoreMap() }
    )

    companion object {
        const val COLLECTION_PATH = "package_plans"
        const val DOCUMENT_ID = "main"

        val DEFAULT_CATALOG = PackagePlanCatalog(
            mapOf(
                "package_growth_mrr" to PackagePlan(
                    id = "package_growth_mrr",
                    name = "Growth Plan",
                    description = "Host 1 active workspace listing",
                    badgeName = "Growth",
                    priceUsd = 4.99,
                    listingLimit = 1,
                    validityDays = 30,
                    isEnabled = true,
                    sortOrder = 0
                ),
                "package_pro_mrr" to PackagePlan(
                    id = "package_pro_mrr",
                    name = "Pro Plan",
                    description = "Host up to 3 active workspace listings",
                    badgeName = "Pro",
                    priceUsd = 16.99,
                    listingLimit = 3,
                    validityDays = 30,
                    isEnabled = true,
                    sortOrder = 1
                ),
                "package_enterprise_mrr" to PackagePlan(
                    id = "package_enterprise_mrr",
                    name = "Enterprise",
                    description = "Unlimited active workspace listings",
                    badgeName = "Enterprise",
                    priceUsd = 38.99,
                    listingLimit = null,
                    validityDays = 30,
                    isEnabled = true,
                    sortOrder = 2
                )
            )
        )

        @Suppress("UNCHECKED_CAST")
        fun fromFirestoreMap(data: Map<String, Any?>): PackagePlanCatalog {
            val raw = data["packages"] as? Map<String, Any?> ?: emptyMap()
            val packages = raw.mapNotNull { (id, value) ->
                (value as? Map<String, Any?>)?.let { id to PackagePlan.fromFirestoreMap(id, it) }
            }.toMap()
            return if (packages.isEmpty()) DEFAULT_CATALOG else PackagePlanCatalog(packages)
        }
    }
}

/**
 * Resolves an active package for [AppUser], falling back gracefully to a synthetic active
 * plan if the user's stored ownerPackageId was removed or modified by an admin update,
 * provided their package expiry timestamp is still in the future.
 */
fun AppUser.resolveActivePackage(catalog: PackagePlanCatalog): PackagePlan? {
    val pkgId = ownerPackageId ?: return null
    val plan = catalog.packages[pkgId]
    if (plan != null) return plan
    val expiry = ownerPackageExpiryMillis
    if (expiry != null && expiry > System.currentTimeMillis()) {
        return PackagePlan(
            id = pkgId,
            name = "Pro Host Plan",
            description = "Active subscription membership",
            badgeName = "Pro",
            priceUsd = 0.0,
            listingLimit = null,
            validityDays = 30,
            isEnabled = true
        )
    }
    return null
}

/**
 * One published, immutable version of an admin-managed legal document (Privacy
 * Policy / Terms of Use / Revocation Policy — see LegalContent.kt's own id
 * strings, which this [docId] matches exactly: "privacy_policy", "terms_of_use",
 * "revocation_policy"). Stored two ways in Firestore under legal_documents/{docId}:
 * this exact map as the "current" pointer on the parent document (fast, single-read
 * lookup for the common case — "what's the latest version?"), and again as an
 * append-only history entry in the legal_documents/{docId}/versions/{version}
 * subcollection (versions/{version}'s own doc id is [version].toString(), so a
 * re-upload can never collide with or overwrite a prior one). The uploaded HTML
 * file itself lives at Storage path legal_documents/{docId}/v{version}.html —
 * also create-only, never updated/deleted, for the same "never lose a prior
 * version" reason.
 */
data class LegalDocumentVersion(
    val version: Int,
    val url: String,
    val fileName: String? = null,
    val uploadedAtMillis: Long = 0L,
    val uploadedByEmail: String = "",
    // "text/html" for the 3 HTML policy docs, "application/pdf" for the
    // re-rental authorization template. Defaults to "text/html" so every
    // version written before this field existed still round-trips correctly.
    val contentType: String = "text/html"
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "version" to version,
        "url" to url,
        "fileName" to fileName,
        "uploadedAtMillis" to uploadedAtMillis,
        "uploadedByEmail" to uploadedByEmail,
        "contentType" to contentType
    )

    companion object {
        const val COLLECTION_PATH = "legal_documents"

        // The 3 HTML policy doc ids — matches LegalContent's own id strings
        // exactly. These fully replace their Kotlin-hardcoded content: with no
        // admin version published, LegalDocumentDialog shows a placeholder
        // rather than falling back to the hardcoded copy.
        val ADMIN_MANAGED_DOC_IDS = setOf("privacy_policy", "terms_of_use", "revocation_policy")

        // The re-rental authorization template PDF — matches
        // LegalContent.rerentalAuthorizationTemplate.id. Unlike the 3 HTML
        // docs above, this ADDS an admin-uploadable PDF on top of the existing
        // Kotlin-hardcoded template rather than replacing it: with no admin
        // PDF published, the "Download Authorization Template" button falls
        // back to generating one from the hardcoded content
        // (LegalPdfGenerator), since this is a load-bearing part of the
        // listing-ownership-verification flow that must never show a
        // placeholder/dead end.
        const val RERENTAL_TEMPLATE_DOC_ID = "rerental_authorization_template"

        fun fromFirestoreMap(data: Map<String, Any?>): LegalDocumentVersion? {
            val version = (data["version"] as? Number)?.toInt() ?: return null
            val url = data["url"] as? String ?: return null
            return LegalDocumentVersion(
                version = version,
                url = url,
                fileName = data["fileName"] as? String,
                uploadedAtMillis = (data["uploadedAtMillis"] as? Number)?.toLong() ?: 0L,
                uploadedByEmail = data["uploadedByEmail"] as? String ?: "",
                contentType = data["contentType"] as? String ?: "text/html"
            )
        }
    }
}

data class AdminPricingState(
    val monthlySubscriptionFeeUsd: Double = 1.80,
    val baselineFeeUsd: Double = 1.80,
    val presetOptions: List<Double> = listOf(1.00, 1.50, 1.80, 2.50, 3.00, 5.00, 10.00),
    val governanceTag: String = "HOST-PACKAGING-TIERS-V2-ACTIVE"
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "monthlySubscriptionFeeUsd" to monthlySubscriptionFeeUsd,
            "baselineFeeUsd" to baselineFeeUsd,
            "presetOptions" to presetOptions,
            "governanceTag" to governanceTag,
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
                governanceTag = data["governanceTag"] as? String ?: defaults.governanceTag
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
    val category: String, // "SPACE_TYPE", "SUBCATEGORY", "AMENITY", "EQUIPMENT", "RENTAL_STRATEGY"
    val iconName: String = "Category",
    val isEnabled: Boolean = true,
    val isSystemDefault: Boolean = true,
    // Only meaningful for category == "SPACE_TYPE": the maximum number of subdivisions
    // a listing under this category may declare in CreateListingDialog's Step 3. Null
    // means "no cap configured yet" (unlimited in practice, matching pre-existing
    // behavior for every category before this field existed).
    val maxSubdivisions: Int? = null
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "category" to category,
        "iconName" to iconName,
        "isEnabled" to isEnabled,
        "isSystemDefault" to isSystemDefault,
        "maxSubdivisions" to maxSubdivisions
    )

    companion object {
        fun fromFirestoreMap(data: Map<String, Any?>): SchemaItem = SchemaItem(
            id = data["id"] as? String ?: "",
            name = data["name"] as? String ?: "",
            description = data["description"] as? String ?: "",
            category = data["category"] as? String ?: "",
            iconName = data["iconName"] as? String ?: "Category",
            isEnabled = data["isEnabled"] as? Boolean ?: true,
            isSystemDefault = data["isSystemDefault"] as? Boolean ?: true,
            maxSubdivisions = (data["maxSubdivisions"] as? Number)?.toInt()
        )
    }
}

data class SpaceArchitectureSchema(
    val spaceTypes: List<SchemaItem> = emptyList(),
    val subcategories: List<SchemaItem> = emptyList(),
    val amenities: List<SchemaItem> = emptyList(),
    val equipmentCategories: List<SchemaItem> = emptyList(),
    val rentalStrategies: List<SchemaItem> = emptyList()
) {
    val totalItemsCount: Int
        get() = spaceTypes.size + subcategories.size + amenities.size + equipmentCategories.size + rentalStrategies.size

    val activeItemsCount: Int
        get() = spaceTypes.count { it.isEnabled } +
                subcategories.count { it.isEnabled } +
                amenities.count { it.isEnabled } +
                equipmentCategories.count { it.isEnabled } +
                rentalStrategies.count { it.isEnabled }

    val allItems: List<SchemaItem>
        get() = spaceTypes + subcategories + amenities + equipmentCategories + rentalStrategies

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "spaceTypes" to spaceTypes.map { it.toFirestoreMap() },
        "subcategories" to subcategories.map { it.toFirestoreMap() },
        "amenities" to amenities.map { it.toFirestoreMap() },
        "equipmentCategories" to equipmentCategories.map { it.toFirestoreMap() },
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


