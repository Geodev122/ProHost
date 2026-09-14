package com.example.util

/**
 * Builds the public, shareable URL for a listing — the same
 * hopebearer-award.com domain already used for the Whish payment App Link
 * (see AndroidManifest.xml's autoVerify intent-filter and
 * functions/src/listings/shareLanding.ts, which serves this exact path with
 * real Open Graph/Twitter Card meta tags for link-preview thumbnails). A
 * device with ProHost installed intercepts this URL via the App Link and
 * opens the listing directly (MainActivity.handleIncomingIntent); anyone
 * else sees the branded landing page instead.
 */
object ShareLinks {
    private const val LISTING_BASE_URL = "https://hopebearer-award.com/listing"

    fun forListing(spaceId: String): String = "$LISTING_BASE_URL/$spaceId"
}
