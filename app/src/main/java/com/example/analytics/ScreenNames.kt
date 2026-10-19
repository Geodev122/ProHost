package com.example.analytics

import com.example.data.model.UserRole

/**
 * GA4 screen names. The app's navigation is hand-rolled state (tab ids, overlays), so this is the one
 * place that turns that state into readable, stable screen names — never raw tab ids.
 * Sheets and dialogs report themselves with [com.example.ui.components.TrackScreen].
 */
object ScreenNames {
    const val SPLASH = "splash"
    const val SUSPENDED = "suspended"
    const val WHATSAPP_GATE = "whatsapp_number_gate"
    const val MANAGE_LISTING = "manage_listing"
    const val SPACE_DETAILS = "space_details"

    /** Admin Console inner tabs, by index (AdminConsoleScreen). */
    val ADMIN_TABS = listOf("packages", "users", "listings", "analytics", "schema", "security", "demo")

    fun adminTab(index: Int): String = "admin_" + (ADMIN_TABS.getOrNull(index) ?: "tab_$index")

    /** Readable name for a shell tab id (AppNavTab.id). */
    fun forTab(tabId: String?, role: UserRole?, exploreMap: Boolean): String = when (tabId) {
        "search_map", null -> if (exploreMap) "explore_map" else "explore_list"
        "pro_rentals" -> "my_rentals"
        "pro_profile" -> "profile"
        "admin_profile" -> "admin_profile"
        "manage_listings" -> "my_listings"
        "owner_requests" -> "renting_requests"
        "owner_progress" -> "renting_progress"
        "stats" -> if (role == UserRole.ADMIN) "admin_analytics" else "owner_analytics"
        "owner_subscriptions" -> "premium"
        "my_favorites" -> "saved"
        "admin_console" -> "admin_console"
        else -> tabId
    }

    /** Login steps (LoginAuthScreen.AuthStep names). */
    fun forAuthStep(step: String): String = when (step) {
        "EMAIL_ENTRY" -> "auth_email"
        "EMAIL_OTP" -> "auth_email_code"
        "EMAIL_LINK_SENT" -> "auth_email_link_sent"
        "REGISTRATION_FORM" -> "registration"
        else -> "auth_" + step.lowercase()
    }

    /** Drawer dialogs (activeDrawerTabDialog ids). */
    fun forDrawerDialog(id: String): String = when (id) {
        "fcm_alerts" -> "notifications"
        "legal_documents" -> "legal"
        else -> id
    }
}
