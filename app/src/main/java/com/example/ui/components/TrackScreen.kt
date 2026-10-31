package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.analytics.AnalyticsConsent
import com.example.analytics.AnalyticsTracker

/**
 * Reports a sheet, dialog or step as its own GA4 screen_view while it is shown, and re-reports the
 * screen underneath when it closes. Put it at the top of the sheet/dialog content (names: ScreenNames,
 * snake_case, never ids). Consent-gated inside AnalyticsTracker; re-sent when consent is granted.
 */
@Composable
fun TrackScreen(name: String, screenClass: String = "Sheet", params: Map<String, Any?> = emptyMap()) {
    val consent by AnalyticsConsent.state.collectAsState()
    DisposableEffect(name, consent) {
        AnalyticsTracker.pushScreen(name, screenClass, params)
        onDispose { AnalyticsTracker.popScreen(name) }
    }
}
