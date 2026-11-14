package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import com.example.ui.navigation.ProHostAppRoot
import com.example.ui.theme.FontScaleCap
import com.example.ui.theme.ProHostTheme
import com.example.util.InAppUpdateManager
import com.example.util.NotificationPermissionManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var targetTab by mutableStateOf<String?>(null)
    private var targetBookingId by mutableStateOf<String?>(null)
    private var targetSpaceId by mutableStateOf<String?>(null)
    private var targetRoomId by mutableStateOf<String?>(null)
    private var emailVerifiedDeepLink by mutableStateOf(false)
    private var inAppUpdateManager: InAppUpdateManager? = null

    // Text grows with the phone's setting only up to "Large" (FontScaleCap), dialogs and sheets included.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FontScaleCap.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() — installs the real, consistently-themed
        // system splash (androidx.core.splashscreen backport) on every API level down
        // to minSdk, replacing the raw un-themed starting window that used to be the
        // only thing visible pre-Compose on API < 31 (see themes.xml's
        // Theme.MyApplication.Starting doc comment for the full root cause).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        com.example.ui.util.AndroidPlatformActions.attach(this) // intents start from this Activity (see PlatformActions)
        // Screenshots and screen recording are allowed on purpose (owner's choice): no FLAG_SECURE.
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        // Initialize Google Play In-App Updates safely
        try {
            val manager = InAppUpdateManager(this)
            manager.checkForAppUpdate(preferImmediate = false)
            inAppUpdateManager = manager
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "InAppUpdateManager initialization failed: ${e.message}")
        }

        // Only POST_NOTIFICATIONS is asked up front. Location is requested in context by the map
        // picker; photos use the system pickers and the camera app, which need no permission.
        // The system prompt waits until the first-launch analytics choice is made, so the two
        // prompts never stack on top of each other.
        try {
            val notificationPermission = NotificationPermissionManager(this)
            lifecycleScope.launch {
                com.example.analytics.AnalyticsConsent.state
                    .first { it != com.example.analytics.ConsentState.UNKNOWN }
                notificationPermission.requestIfNeeded()
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "NotificationPermissionManager request failed: ${e.message}")
        }

        setContent {
            ProHostTheme {
                ProHostAppRoot(
                    deepLinkTab = targetTab,
                    deepLinkBookingId = targetBookingId,
                    deepLinkSpaceId = targetSpaceId,
                    deepLinkRoomId = targetRoomId,
                    emailVerifiedDeepLink = emailVerifiedDeepLink,
                    inAppUpdateManager = inAppUpdateManager
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            inAppUpdateManager?.onResume()
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "InAppUpdateManager.onResume warning: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.example.ui.util.AndroidPlatformActions.detach(this)
        try {
            inAppUpdateManager?.onDestroy()
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "InAppUpdateManager.onDestroy warning: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    // Acquisition analytics: notification taps carry target_tab/notification_type extras;
    // links carry a URI. Only the kind of link and its tab are logged — never tokens or ids.
    private fun logIncomingIntent(intent: Intent) {
        val data = intent.data
        val fromNotification = intent.hasExtra("notification_type") ||
            (data == null && (intent.hasExtra("target_tab") || intent.hasExtra("booking_id")))
        when {
            fromNotification -> com.example.analytics.AnalyticsTracker.notificationOpen(
                intent.getStringExtra("notification_type"),
                intent.getStringExtra("target_tab")
            )
            data != null -> {
                val route = routeOf(data)
                com.example.analytics.AnalyticsTracker.deepLinkOpen(route.source, route.analyticsTarget)
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        logIncomingIntent(intent)
        val tab = intent.getStringExtra("target_tab")
        val bookingId = intent.getStringExtra("booking_id")
        if (!tab.isNullOrBlank()) {
            targetTab = tab
        }
        if (!bookingId.isNullOrBlank()) {
            targetBookingId = bookingId
        }
        intent.getStringExtra("space_id")?.takeIf { it.isNotBlank() }?.let { targetSpaceId = it }

        // Link routing is pure and shared with iOS (com.example.util.DeepLinkRouter, cases in tests/vectors/deep-links.json):
        // listing share links (pro-host.tech/listing/{spaceId}?room=, reached only through a verified App Link),
        // prohost://verify-email/success, prohost://redeem?code= (the code is only ever handed to Google Play's redeem
        // page) and prohost://<tab> links from notification action buttons.
        val route = intent.data?.let(::routeOf) ?: return
        route.listing?.let {
            targetSpaceId = it.spaceId
            targetRoomId = it.roomId
        }
        if (route.emailVerified) emailVerifiedDeepLink = true
        if (route.isRedeem) com.example.data.billing.PendingPromoCode.offer(route.promoCode)
        route.tab?.let { targetTab = it }
    }

    private fun routeOf(data: android.net.Uri) = com.example.util.DeepLinkRouter.route(
        com.example.util.DeepLinkRouter.Link(
            scheme = data.scheme,
            host = data.host,
            path = data.path,
            // getQueryParameter throws on non-hierarchical URIs (mailto:); such links carry no room or code.
            room = if (data.isHierarchical) data.getQueryParameter("room") else null,
            code = if (data.isHierarchical) data.getQueryParameter("code") else null,
        )
    )
}


