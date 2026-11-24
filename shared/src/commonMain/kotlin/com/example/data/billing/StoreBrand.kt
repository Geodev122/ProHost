package com.example.data.billing

import kotlin.concurrent.Volatile

/** The subscription store of this device: Google Play on Android, the App Store on iPhone, none on the web console. */
enum class AppStoreKind { GOOGLE_PLAY, APP_STORE, NONE }

/**
 * Store naming and the "Manage subscription" route (owner, 24 Nov 2026: manage must open the store the plan was bought in,
 * per device). Android keeps every text exactly as before ([current] stays GOOGLE_PLAY there); iOS sets APP_STORE in its
 * seams, so the shared texts that name Google Play read as the App Store on iPhone (App Review 2.3.10: no other platform's
 * store in the app's UI).
 */
object StoreBrand {
    @Volatile
    var current: AppStoreKind = AppStoreKind.GOOGLE_PLAY

    const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"
    const val APP_STORE_SUBSCRIPTIONS_URL = "https://apps.apple.com/account/subscriptions"
    const val PLAY_ORDER_HISTORY_URL = "https://play.google.com/store/account/orderhistory"
    const val APPLE_ORDER_HISTORY_URL = "https://reportaproblem.apple.com"

    /** The store that bills [entitlementSource] (profile mirror), or null for grants and members without a store plan. */
    fun storeOf(entitlementSource: String?): AppStoreKind? = when (entitlementSource) {
        "google_play" -> AppStoreKind.GOOGLE_PLAY
        "app_store" -> AppStoreKind.APP_STORE
        else -> null
    }

    /** Where "Manage subscription" goes for a plan from [entitlementSource] on a [device]. */
    fun manageRoute(entitlementSource: String?, device: AppStoreKind = current): ManageRoute {
        val store = storeOf(entitlementSource) ?: device
        return when {
            store == device && device != AppStoreKind.NONE -> ManageRoute.STORE_SHEET
            store == AppStoreKind.GOOGLE_PLAY -> ManageRoute.PLAY_WEB
            store == AppStoreKind.APP_STORE -> ManageRoute.APP_STORE_WEB
            else -> ManageRoute.NONE
        }
    }

    /** [text] with Google Play's name and places replaced by the App Store's when [store] is the App Store; unchanged otherwise. */
    fun text(text: String, store: AppStoreKind = current): String {
        if (store != AppStoreKind.APP_STORE || !(text.contains("Google") || text.contains("Play Store"))) return text
        var s = text
            .replace("Google Play › Payments & subscriptions", "Settings › Apple Account › Subscriptions")
            .replace("Google Play account", "Apple Account")
            .replace("Google account", "Apple Account")
            .replace("Google Play Store", "App Store")
            .replace("Play Store", "App Store")
        s = SENTENCE_POSSESSIVE.replace(s) { "${it.groupValues[1]}The App Store's" }
        s = SENTENCE_START.replace(s) { "${it.groupValues[1]}The App Store" }
        s = AFTER_PREPOSITION.replace(s) { "${it.groupValues[1]} the App Store" }
        return s.replace("Google Play", "App Store")
    }

    private val SENTENCE_POSSESSIVE = Regex("""(^|[.!?]\s+|\n)Google Play's""")
    private val SENTENCE_START = Regex("""(^|[.!?]\s+|\n)Google Play\b""")
    private val AFTER_PREPOSITION = Regex("""\b(in|In|via|through|from|to|on|with|by|of|at|reach) Google Play\b""")
}

enum class ManageRoute {
    /** The device's own store UI (Play subscription center deep link / App Store manage sheet). */
    STORE_SHEET,
    /** A Google Play plan seen on another device: Play's subscriptions page on the web. */
    PLAY_WEB,
    /** An App Store plan seen on another device: Apple's subscriptions page on the web. */
    APP_STORE_WEB,
    NONE,
}
