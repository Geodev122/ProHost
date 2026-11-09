package com.example.data.model

// Space-architecture schema models (moved from :app DataModels.kt, migration plan P5; package unchanged). Shared with the admin web console.

/**
 * Dynamic Space Architecture Schema Configuration Model
 * Allows Super Admin to inspect and customize database-level mapping of
 * Listing Types, Subcategories, Amenities, Equipment, Specialties, and Strategies.
 */
// Schema category constants — single source of truth for all category strings
object SchemaCategory {
    const val SPACE_TYPE = "SPACE_TYPE"
    const val DIVISION_TYPE = "DIVISION_TYPE"   // was SUBCATEGORY
    const val FACILITY = "FACILITY"             // was AMENITY — whole-space facilities
    const val AMENITY = "AMENITY"               // subdivision-level amenities/equipment/features
    const val RENTAL_STRATEGY = "RENTAL_STRATEGY"
}

data class SchemaItem(
    val id: String,
    val name: String,
    val description: String = "",
    val category: String, // use SchemaCategory constants
    val iconName: String = "Category",
    val isEnabled: Boolean = true,
    val isSystemDefault: Boolean = true,
    // Only meaningful for SPACE_TYPE: max subdivisions a listing may declare. Null = unlimited.
    val maxSubdivisions: Int? = null,
    // AMENITY only: which DIVISION_TYPE ids this applies to. Empty = all division types.
    val scopedToIds: List<String> = emptyList(),
    // AMENITY only: grouping label (e.g. "Equipment", "Access", "Comfort", "Clinical").
    val amenityGroup: String = "",
    // DIVISION_TYPE only: true = this division type can use per-attendee pricing mode.
    val supportsAttendeeMode: Boolean = false,
    // SPACE_TYPE only: admin-chosen hex color for map markers, e.g. "#5B9BFF". Null = auto-assigned from palette.
    val markerColor: String? = null
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "category" to category,
        "iconName" to iconName,
        "isEnabled" to isEnabled,
        "isSystemDefault" to isSystemDefault,
        "maxSubdivisions" to maxSubdivisions,
        "scopedToIds" to scopedToIds,
        "amenityGroup" to amenityGroup,
        "supportsAttendeeMode" to supportsAttendeeMode,
        "markerColor" to markerColor
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestoreMap(data: Map<String, Any?>): SchemaItem = SchemaItem(
            id = data["id"] as? String ?: "",
            name = data["name"] as? String ?: "",
            description = data["description"] as? String ?: "",
            category = data["category"] as? String ?: "",
            iconName = data["iconName"] as? String ?: "Category",
            isEnabled = data["isEnabled"] as? Boolean ?: true,
            isSystemDefault = data["isSystemDefault"] as? Boolean ?: true,
            maxSubdivisions = (data["maxSubdivisions"] as? Number)?.toInt(),
            scopedToIds = (data["scopedToIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            amenityGroup = data["amenityGroup"] as? String ?: "",
            supportsAttendeeMode = data["supportsAttendeeMode"] as? Boolean ?: false,
            markerColor = data["markerColor"] as? String
        )
    }
}

data class AttendeePackage(
    val id: String,
    val name: String,
    val description: String = "",
    val pricePerAttendeeUsd: Double,
    val inclusions: List<String> = emptyList(),
    val minAttendees: Int = 1,
    val maxAttendees: Int? = null,
    val isEnabled: Boolean = true,
    val isSystemDefault: Boolean = true
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "pricePerAttendeeUsd" to pricePerAttendeeUsd,
        "inclusions" to inclusions,
        "minAttendees" to minAttendees,
        "maxAttendees" to maxAttendees,
        "isEnabled" to isEnabled,
        "isSystemDefault" to isSystemDefault
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestoreMap(data: Map<String, Any?>): AttendeePackage = AttendeePackage(
            id = data["id"] as? String ?: "",
            name = data["name"] as? String ?: "",
            description = data["description"] as? String ?: "",
            pricePerAttendeeUsd = (data["pricePerAttendeeUsd"] as? Number)?.toDouble() ?: 0.0,
            inclusions = (data["inclusions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            minAttendees = (data["minAttendees"] as? Number)?.toInt() ?: 1,
            maxAttendees = (data["maxAttendees"] as? Number)?.toInt(),
            isEnabled = data["isEnabled"] as? Boolean ?: true,
            isSystemDefault = data["isSystemDefault"] as? Boolean ?: true
        )
    }
}

data class SpaceArchitectureSchema(
    val spaceTypes: List<SchemaItem> = emptyList(),
    val divisionTypes: List<SchemaItem> = emptyList(),   // was: subcategories
    val facilities: List<SchemaItem> = emptyList(),       // was: amenities — whole-space
    val amenities: List<SchemaItem> = emptyList(),        // new: subdivision-level
    val rentalStrategies: List<SchemaItem> = emptyList(),
    val attendeePackages: List<AttendeePackage> = emptyList()
) {
    val totalItemsCount: Int
        get() = spaceTypes.size + divisionTypes.size + facilities.size + amenities.size + rentalStrategies.size + attendeePackages.size

    val activeItemsCount: Int
        get() = spaceTypes.count { it.isEnabled } +
                divisionTypes.count { it.isEnabled } +
                facilities.count { it.isEnabled } +
                amenities.count { it.isEnabled } +
                rentalStrategies.count { it.isEnabled } +
                attendeePackages.count { it.isEnabled }

    val allItems: List<SchemaItem>
        get() = spaceTypes + divisionTypes + facilities + amenities + rentalStrategies

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "spaceTypes" to spaceTypes.map { it.toFirestoreMap() },
        "divisionTypes" to divisionTypes.map { it.toFirestoreMap() },
        "facilities" to facilities.map { it.toFirestoreMap() },
        "amenities" to amenities.map { it.toFirestoreMap() },
        "rentalStrategies" to rentalStrategies.map { it.toFirestoreMap() },
        "attendeePackages" to attendeePackages.map { it.toFirestoreMap() }
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestoreMap(data: Map<String, Any?>): SpaceArchitectureSchema {
            fun list(key: String): List<SchemaItem> =
                (data[key] as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let { m -> SchemaItem.fromFirestoreMap(m) } } ?: emptyList()
            fun pkgList(key: String): List<AttendeePackage> =
                (data[key] as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let { m -> AttendeePackage.fromFirestoreMap(m) } } ?: emptyList()
            // New documents have a "facilities" key; old documents stored whole-space items
            // under "amenities". When the new key is absent, treat all old "amenities" as
            // facilities and leave the new amenities list empty to avoid duplication.
            val isNewFormat = data.containsKey("facilities")
            val rawAmenities = list("amenities")
            return SpaceArchitectureSchema(
                spaceTypes = list("spaceTypes"),
                divisionTypes = list("divisionTypes").ifEmpty { list("subcategories") },
                facilities = if (isNewFormat) list("facilities") else rawAmenities,
                amenities = if (isNewFormat) rawAmenities.filter { it.category == SchemaCategory.AMENITY } else emptyList(),
                rentalStrategies = list("rentalStrategies"),
                attendeePackages = pkgList("attendeePackages")
            )
        }
    }
}
