package com.example.data.model

/**
 * Saves are per room (owner, 23 Nov 2026): an `AppUser.savedSpaceIds` entry is `"<listingId>#<roomId>"` for a room of a
 * divided listing and `"<listingId>"` for a whole place. Mirrored by `functions/src/users/savedKeys.ts` (keep identical).
 */
object SavedKeys {
    const val SEPARATOR = '#'

    fun key(listingId: String, roomId: String?): String = if (roomId.isNullOrBlank()) listingId else "$listingId$SEPARATOR$roomId"

    fun listingOf(key: String): String = key.substringBefore(SEPARATOR)

    fun roomOf(key: String): String? = key.substringAfter(SEPARATOR, "").ifBlank { null }

    /** The key of [listing] with [roomId] selected: the room for a divided listing, else the whole place. */
    fun forListing(listing: SpaceListing, roomId: String?): String =
        if (listing.subdivisions.isEmpty()) listing.id else key(listing.id, roomId ?: listing.subdivisions.first().id)
}
