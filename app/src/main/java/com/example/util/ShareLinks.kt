package com.example.util

/**
 * Builds the public, shareable URL for a listing — Firebase Hosting's own
 * default domain for this project, NOT hopebearer-award.com (that custom
 * domain is reserved for Whish's payment-gateway channel API configuration
 * only and must not be reused for anything else, listing shares included).
 * prohost-f766f.web.app serves the exact same Hosting deployment (including
 * /.well-known/assetlinks.json), so it needs no extra registration/DNS work
 * to support Android App Link verification — see AndroidManifest.xml's
 * dedicated autoVerify intent-filter for this host and
 * functions/src/listings/shareLanding.ts, which serves this path with real
 * Open Graph/Twitter Card meta tags for link-preview thumbnails. A device
 * with ProHost installed intercepts this URL via the App Link and opens the
 * listing directly (MainActivity.handleIncomingIntent); anyone else sees the
 * branded landing page instead, with a "Get the App" Play Store link.
 */
object ShareLinks {
    private const val LISTING_BASE_URL = "https://prohost-f766f.web.app/listing"

    fun forListing(spaceId: String): String = "$LISTING_BASE_URL/$spaceId"
}
