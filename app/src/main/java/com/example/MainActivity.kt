package com.example

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
import com.example.ui.theme.ProHostTheme
import com.example.util.InAppUpdateManager
import com.example.util.NotificationPermissionManager

class MainActivity : ComponentActivity() {
    private var targetTab by mutableStateOf<String?>(null)
    private var targetBookingId by mutableStateOf<String?>(null)
    private var targetSpaceId by mutableStateOf<String?>(null)
    private var emailVerifiedDeepLink by mutableStateOf(false)
    private var emailSignInLink by mutableStateOf<String?>(null)
    // One-click email OTP magic link: `prohost://emailotp/verified?token=<customToken>`
    private var emailOtpToken by mutableStateOf<String?>(null)
    private var inAppUpdateManager: InAppUpdateManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() — installs the real, consistently-themed
        // system splash (androidx.core.splashscreen backport) on every API level down
        // to minSdk, replacing the raw un-themed starting window that used to be the
        // only thing visible pre-Compose on API < 31 (see themes.xml's
        // Theme.MyApplication.Starting doc comment for the full root cause).
        installSplashScreen()
        super.onCreate(savedInstanceState)
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
        try {
            NotificationPermissionManager(this).requestIfNeeded()
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "NotificationPermissionManager request failed: ${e.message}")
        }

        setContent {
            ProHostTheme {
                ProHostAppRoot(
                    deepLinkTab = targetTab,
                    deepLinkBookingId = targetBookingId,
                    deepLinkSpaceId = targetSpaceId,
                    emailVerifiedDeepLink = emailVerifiedDeepLink,
                    emailSignInLink = emailSignInLink,
                    onEmailSignInLinkConsumed = { emailSignInLink = null },
                    emailOtpToken = emailOtpToken,
                    onEmailOtpTokenConsumed = { emailOtpToken = null },
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
                val source = when {
                    data.host == "pro-host.tech" && data.path?.startsWith("/listing/") == true -> "share_link"
                    data.scheme == "prohost" && (data.host == "verify-email" || data.host == "emailotp") -> "email"
                    data.scheme == "prohost" && data.host == "redeem" -> "promo_link"
                    data.path?.contains("emaillink") == true -> "email"
                    data.scheme == "prohost" -> "prohost_scheme"
                    else -> "web_link"
                }
                val target = if (source == "share_link") "space_details" else data.host
                com.example.analytics.AnalyticsTracker.deepLinkOpen(source, target)
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

        val data = intent.data
        // A tapped listing share link (pro-host.tech/listing/{spaceId} — see
        // functions/src/listings/shareLanding.ts) only reaches here when the OS's App Link verification succeeded and the
        // app is installed; a link-preview crawler or a user without the app never
        // hits this code path at all, they see the landing page's own HTML instead.
        // ProHostNavGraph resolves the id against the live spaces list once the user
        // is signed in and it's loaded.
        val isListingShareLink = data != null && data.scheme == "https" &&
            data.host == "pro-host.tech" && data.path?.startsWith("/listing/") == true
        if (isListingShareLink) {
            targetSpaceId = data.path?.removePrefix("/listing/")?.trim('/')?.takeIf { it.isNotBlank() }
        }

        // Email verification return: prohost://verify-email/success (from verifyEmailLink
        // Cloud Function — redirects the browser to this deep link on success).
        val isEmailVerified = data != null && data.scheme == "prohost" &&
            data.host == "verify-email" && data.path?.startsWith("/success") == true
        if (isEmailVerified) {
            emailVerifiedDeepLink = true
        }

        // One-click email OTP magic link: prohost://emailotp/verified?token=<customToken>
        // Sent by the clickEmailOtpLink Cloud Function after it validates the OTP code.
        val isEmailOtp = data != null && data.scheme == "prohost" && data.host == "emailotp"
        if (isEmailOtp) {
            val token = data?.getQueryParameter("token")
            if (!token.isNullOrBlank()) {
                emailOtpToken = token
            }
        }

        // Promo-code links: prohost://redeem?code=XXXX — open Subscriptions with the Redeem
        // dialog pre-filled (the code is only ever handed to Google Play's redeem page).
        if (data != null && data.scheme == "prohost" && data.host == "redeem") {
            com.example.data.billing.PendingPromoCode.offer(data.getQueryParameter("code"))
            targetTab = "owner_subscriptions"
        }

        // Tab deep links: prohost://<tabId> — sent by notification action buttons and
        // external links to navigate directly to a specific tab inside the app.
        if (data != null && data.scheme == "prohost" && !isEmailVerified && !isEmailOtp) {
            val tabFromHost = when (data.host) {
                "owner_subscriptions" -> "owner_subscriptions"
                "profile" -> "pro_profile"
                "owner_hub" -> "manage_listings"
                "my_bookings" -> "pro_rentals"
                "discovery" -> "search_map"
                else -> null
            }
            if (tabFromHost != null) {
                targetTab = tabFromHost
            }
        }

        // Firebase Auth email sign-in link: https://prohost-f766f.web.app/emaillink?oobCode=...
        // Sent by the sendSignInEmailLink Cloud Function; the OS delivers this to the app
        // via the App Link intent filter (autoVerify=true) in the manifest.
        if (data != null) {
            try {
                val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
                if (firebaseAuth.isSignInWithEmailLink(data.toString())) {
                    emailSignInLink = data.toString()
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Email link check failed: ${e.message}")
            }
        }
    }
}


