package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.navigation.ProHostAppRoot
import com.example.ui.theme.ProHostTheme
import com.example.util.InAppUpdateManager
import com.example.util.NotificationPermissionManager

class MainActivity : ComponentActivity() {
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* OS handles rationale; no action needed here */ }

    private var targetTab by mutableStateOf<String?>(null)
    private var targetBookingId by mutableStateOf<String?>(null)
    private var targetSpaceId by mutableStateOf<String?>(null)
    private var emailVerifiedDeepLink by mutableStateOf(false)
    private var inAppUpdateManager: InAppUpdateManager? = null
    private var backgroundedAtMillis: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() — installs the real, consistently-themed
        // system splash (androidx.core.splashscreen backport) on every API level down
        // to minSdk, replacing the raw un-themed starting window that used to be the
        // only thing visible pre-Compose on API < 31 (see themes.xml's
        // Theme.MyApplication.Starting doc comment for the full root cause).
        installSplashScreen()
        super.onCreate(savedInstanceState)
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

        // Request POST_NOTIFICATIONS (Android 13+) so booking/payment push alerts
        // actually show — covers both a fresh sign-in and an already-signed-in
        // returning user, who never sees LoginAuthScreen's own permission prompts.
        try {
            NotificationPermissionManager(this).requestIfNeeded()
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "NotificationPermissionManager request failed: ${e.message}")
        }

        // Request location, camera, and media permissions up-front so they are
        // available for the map, listing photos, and profile picture flows without
        // a second dialog mid-task. Centralised here; LoginAuthScreen's duplicate
        // location request has been removed.
        try {
            val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE
            permLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA,
                mediaPermission
            ))
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Permission request failed: ${e.message}")
        }

        setContent {
            ProHostTheme {
                ProHostAppRoot(
                    deepLinkTab = targetTab,
                    deepLinkBookingId = targetBookingId,
                    deepLinkSpaceId = targetSpaceId,
                    emailVerifiedDeepLink = emailVerifiedDeepLink,
                    inAppUpdateManager = inAppUpdateManager
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        backgroundedAtMillis = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        try {
            inAppUpdateManager?.onResume()
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "InAppUpdateManager.onResume warning: ${e.message}")
        }
        // Trigger PIN re-auth if the app was in the background for more than 60 seconds (H1).
        if (backgroundedAtMillis > 0L && System.currentTimeMillis() - backgroundedAtMillis > 60_000L) {
            backgroundedAtMillis = 0L
            // Signal the ViewModel so the nav graph can gate behind PIN entry.
            try {
                val viewModel = androidx.lifecycle.ViewModelProvider(this)[com.example.ui.viewmodel.ProHostViewModel::class.java]
                viewModel.requestPinReauth()
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "PIN re-auth signal failed: ${e.message}")
            }
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

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val tab = intent.getStringExtra("target_tab")
        val bookingId = intent.getStringExtra("booking_id")
        if (!tab.isNullOrBlank()) {
            targetTab = tab
        }
        if (!bookingId.isNullOrBlank()) {
            targetBookingId = bookingId
        }

        // Return from the Whish checkout page, via either of two paths:
        // 1. The https App Link (hopebearer-award.com/payment/...), which only reaches
        //    us if Android's OS-level Digital Asset Links verification succeeded.
        // 2. The prohost://payment fallback scheme, which needs no such verification —
        //    it's what public/payment/success.html and failure.html link to when the
        //    App Link above didn't intercept the redirect in the browser at all.
        // Neither carries the specific transaction — checkWhishStatus polling, already
        // running since initiateWhishPayment was called, is what actually confirms the
        // result. This just brings the right tab to the front; role-based resolution of
        // "payment_return" happens in ProHostNavGraph.
        val data = intent.data
        val isWhishAppLinkReturn = data != null && data.scheme == "https" &&
            data.host == "hopebearer-award.com" && data.path?.startsWith("/payment") == true
        val isWhishFallbackReturn = data != null && data.scheme == "prohost" && data.host == "payment"
        if (isWhishAppLinkReturn || isWhishFallbackReturn) {
            targetTab = "payment_return"
        }

        // A tapped listing share link (pro-host.tech/listing/{spaceId} — see
        // functions/src/listings/shareLanding.ts; deliberately NOT
        // hopebearer-award.com, which is reserved for Whish's payment channel only)
        // only reaches here when the OS's App Link verification succeeded and the
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
    }
}


