package com.example.util

/**
 * Builds the public, shareable URL for a listing — pro-host.tech, ProHost's
 * own marketing/App-Link domain (a real registered domain, connected as a
 * custom domain on this same Firebase Hosting site, already carrying the
 * marketing landing page and legal docs). NOT the raw
 * prohost-f766f.web.app Firebase default either, now that a real branded
 * domain exists. pro-host.tech serves the exact same Hosting deployment
 * (including /.well-known/assetlinks.json), so it needs no extra setup to
 * support Android App Link verification — see AndroidManifest.xml's
 * dedicated autoVerify intent-filter for this host and
 * functions/src/listings/shareLanding.ts, which serves this path with real
 * Open Graph/Twitter Card meta tags for link-preview thumbnails. A device
 * with ProHost installed intercepts this URL via the App Link and opens the
 * listing directly (MainActivity.handleIncomingIntent); anyone else sees the
 * branded landing page instead, with a "Get the App" Play Store link.
 */
object ShareLinks {
    private const val LISTING_BASE_URL = "https://pro-host.tech/listing"

    fun forListing(spaceId: String): String = "$LISTING_BASE_URL/$spaceId"
}
