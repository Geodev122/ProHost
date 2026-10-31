package com.example.analytics

import com.example.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenNamesTest {
    @Test
    fun `tab ids become readable screen names`() {
        assertEquals("explore_map", ScreenNames.forTab("search_map", UserRole.SPECIALIST, exploreMap = true))
        assertEquals("explore_list", ScreenNames.forTab("search_map", UserRole.SPECIALIST, exploreMap = false))
        assertEquals("my_rentals", ScreenNames.forTab("pro_rentals", UserRole.SPECIALIST, exploreMap = false))
        assertEquals("premium", ScreenNames.forTab("owner_subscriptions", UserRole.PRO_HOST, exploreMap = false))
        assertEquals("owner_analytics", ScreenNames.forTab("stats", UserRole.PRO_HOST, exploreMap = false))
        assertEquals("admin_analytics", ScreenNames.forTab("stats", UserRole.ADMIN, exploreMap = false))
        assertEquals("saved", ScreenNames.forTab("my_favorites", UserRole.SPECIALIST, exploreMap = false))
    }

    @Test
    fun `admin tabs, login steps and drawer dialogs have names`() {
        assertEquals("admin_packages", ScreenNames.adminTab(0))
        assertEquals("admin_demo", ScreenNames.adminTab(6))
        assertEquals("admin_tab_9", ScreenNames.adminTab(9))
        assertEquals("auth_welcome", ScreenNames.forAuthStep("WELCOME"))
        assertEquals("auth_providers", ScreenNames.forAuthStep("PROVIDERS"))
        assertEquals("auth_email", ScreenNames.forAuthStep("EMAIL"))
        assertEquals("auth_password", ScreenNames.forAuthStep("PASSWORD"))
        assertEquals("auth_forgot_password", ScreenNames.forAuthStep("FORGOT_PASSWORD"))
        assertEquals("auth_reset_sent", ScreenNames.forAuthStep("RESET_SENT"))
        assertEquals("registration_profile", ScreenNames.forAuthStep("REG_PROFILE"))
        assertEquals("registration_place", ScreenNames.forAuthStep("REG_PLACE"))
        assertEquals("registration_whatsapp", ScreenNames.forAuthStep("REG_WHATSAPP"))
        assertEquals("notifications", ScreenNames.forDrawerDialog("fcm_alerts"))
    }
}
