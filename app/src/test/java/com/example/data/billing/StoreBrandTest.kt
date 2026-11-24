package com.example.data.billing

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreBrandTest {
    @Test
    fun manageRouteFollowsTheStoreThatBillsThePlan() {
        assertEquals(ManageRoute.STORE_SHEET, StoreBrand.manageRoute("google_play", AppStoreKind.GOOGLE_PLAY))
        assertEquals(ManageRoute.STORE_SHEET, StoreBrand.manageRoute("app_store", AppStoreKind.APP_STORE))
        assertEquals(ManageRoute.PLAY_WEB, StoreBrand.manageRoute("google_play", AppStoreKind.APP_STORE))
        assertEquals(ManageRoute.APP_STORE_WEB, StoreBrand.manageRoute("app_store", AppStoreKind.GOOGLE_PLAY))
        // Grants and members without a plan: the device's own store.
        assertEquals(ManageRoute.STORE_SHEET, StoreBrand.manageRoute("admin_forced", AppStoreKind.APP_STORE))
        assertEquals(ManageRoute.STORE_SHEET, StoreBrand.manageRoute(null, AppStoreKind.GOOGLE_PLAY))
        assertEquals(ManageRoute.NONE, StoreBrand.manageRoute(null, AppStoreKind.NONE))
    }

    @Test
    fun androidTextsAreUntouched() {
        val s = "Cancel in Google Play before 3 May and you won't be charged."
        assertEquals(s, StoreBrand.text(s, AppStoreKind.GOOGLE_PLAY))
    }

    @Test
    fun iosTextsNameTheAppStore() {
        val ios = AppStoreKind.APP_STORE
        assertEquals("Cancel in the App Store before 3 May.", StoreBrand.text("Cancel in Google Play before 3 May.", ios))
        assertEquals("The App Store couldn't renew your subscription.", StoreBrand.text("Google Play couldn't renew your subscription.", ios))
        assertEquals("The App Store's checkout shows the offer.", StoreBrand.text("Google Play's checkout shows the offer.", ios))
        assertEquals("Payment is charged to your Apple Account.", StoreBrand.text("Payment is charged to your Google Play account.", ios))
        assertEquals("Cancel anytime in Settings › Apple Account › Subscriptions.", StoreBrand.text("Cancel anytime in Google Play › Payments & subscriptions.", ios))
        assertEquals("Renews automatically via the App Store", StoreBrand.text("Renews automatically via Google Play", ios))
        assertEquals("Manage subscription in the App Store", StoreBrand.text("Manage subscription in Google Play", ios))
        assertEquals("ألغِ اشتراكك في App Store أولًا", StoreBrand.text("ألغِ اشتراكك في Google Play أولًا", ios))
    }
}
