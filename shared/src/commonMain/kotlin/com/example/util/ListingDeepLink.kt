package com.example.util

/**
 * A listing link: https://pro-host.tech/listing/{spaceId}?room={subdivisionId} (share links and the Instagram agent's
 * "View" buttons; see functions/src/listings/shareLanding.ts). Pure, so it is unit-tested without Android.
 */
object ListingDeepLink {
    data class Target(val spaceId: String, val roomId: String?)

    private val ID = Regex("^[A-Za-z0-9_-]{1,80}$")

    fun parse(scheme: String?, host: String?, path: String?, room: String?): Target? {
        if (scheme != "https" || host != "pro-host.tech" || path?.startsWith("/listing/") != true) return null
        val id = path.removePrefix("/listing/").trim('/').takeIf { ID.matches(it) } ?: return null
        return Target(id, room?.trim()?.takeIf { ID.matches(it) })
    }
}
