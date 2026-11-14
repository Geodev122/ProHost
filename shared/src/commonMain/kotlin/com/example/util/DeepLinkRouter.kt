package com.example.util

/**
 * Where an incoming link sends the app. Pure (no Android `Uri`, no iOS `URL`): each platform splits the link into its
 * parts and applies the [Route]. The cases live in tests/vectors/deep-links.json so Android and iOS route identically.
 *
 *  - https://pro-host.tech/listing/{spaceId}?room={roomId}  → listing page ([ListingDeepLink])
 *  - prohost://verify-email/success                         → "email verified" return
 *  - prohost://redeem?code=XXXX                             → Premium with the Redeem sheet pre-filled
 *  - prohost://{owner_subscriptions|profile|owner_hub|my_bookings|discovery} → that tab
 */
object DeepLinkRouter {
    data class Link(val scheme: String?, val host: String?, val path: String?, val room: String? = null, val code: String? = null)

    data class Route(
        /** Analytics `deep_link_open` source; never contains ids or tokens. */
        val source: String,
        /** Analytics target: "space_details" for a share link, otherwise the link's host. */
        val analyticsTarget: String?,
        /** Tab to open, or null to leave the current target alone. */
        val tab: String? = null,
        val listing: ListingDeepLink.Target? = null,
        val emailVerified: Boolean = false,
        /** True for a redeem link: the platform hands [promoCode] (may be null) to the pending-promo holder. */
        val isRedeem: Boolean = false,
        val promoCode: String? = null,
    )

    fun route(link: Link): Route {
        val isProhost = link.scheme == "prohost"
        val isShare = link.host == "pro-host.tech" && link.path?.startsWith("/listing/") == true
        val source = when {
            isShare -> "share_link"
            isProhost && link.host == "verify-email" -> "email"
            isProhost && link.host == "redeem" -> "promo_link"
            isProhost -> "prohost_scheme"
            else -> "web_link"
        }
        val emailVerified = isProhost && link.host == "verify-email" && link.path?.startsWith("/success") == true
        val isRedeem = isProhost && link.host == "redeem"
        // A tab link wins over the redeem default; neither applies to the email-verified return.
        val tabFromHost = if (isProhost && !emailVerified) TAB_BY_HOST[link.host] else null
        return Route(
            source = source,
            analyticsTarget = if (isShare) "space_details" else link.host,
            tab = tabFromHost ?: if (isRedeem) "owner_subscriptions" else null,
            listing = ListingDeepLink.parse(link.scheme, link.host, link.path, link.room),
            emailVerified = emailVerified,
            isRedeem = isRedeem,
            promoCode = if (isRedeem) link.code else null,
        )
    }

    private val TAB_BY_HOST = mapOf(
        "owner_subscriptions" to "owner_subscriptions",
        "profile" to "pro_profile",
        "owner_hub" to "manage_listings",
        "my_bookings" to "pro_rentals",
        "discovery" to "search_map",
    )
}
