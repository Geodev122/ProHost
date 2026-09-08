package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.navigation.ProHostAppRoot
import com.example.ui.theme.ProHostTheme
import com.example.util.InAppUpdateManager

class MainActivity : ComponentActivity() {
    private var targetTab by mutableStateOf<String?>(null)
    private var targetBookingId by mutableStateOf<String?>(null)
    private var inAppUpdateManager: InAppUpdateManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
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

        setContent {
            ProHostTheme {
                ProHostAppRoot(
                    deepLinkTab = targetTab,
                    deepLinkBookingId = targetBookingId,
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

        // App Link return from the Whish checkout page (hopebearer-award.com/payment/...).
        // The specific transaction isn't carried in the URL — checkWhishStatus polling,
        // already running since initiateWhishPayment was called, is what actually
        // confirms the result. This just brings the right tab to the front; role-based
        // resolution of "payment_return" happens in ProHostNavGraph.
        val data = intent.data
        if (data != null && data.scheme == "https" && data.host == "hopebearer-award.com" &&
            data.path?.startsWith("/payment") == true
        ) {
            targetTab = "payment_return"
        }
    }
}


