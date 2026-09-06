package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.navigation.ProSpaceAppRoot
import com.example.ui.theme.ProSpaceTheme
import com.example.util.InAppUpdateManager

class MainActivity : ComponentActivity() {
    private var targetTab by mutableStateOf<String?>(null)
    private var targetBookingId by mutableStateOf<String?>(null)
    private lateinit var inAppUpdateManager: InAppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        // Initialize Google Play In-App Updates
        inAppUpdateManager = InAppUpdateManager(this)
        inAppUpdateManager.checkForAppUpdate(preferImmediate = false)

        setContent {
            ProSpaceTheme {
                ProSpaceAppRoot(
                    deepLinkTab = targetTab,
                    deepLinkBookingId = targetBookingId,
                    inAppUpdateManager = inAppUpdateManager
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::inAppUpdateManager.isInitialized) {
            inAppUpdateManager.onResume()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::inAppUpdateManager.isInitialized) {
            inAppUpdateManager.onDestroy()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // One-tap "WhatsApp Reply" notification action (ProSpaceMessagingService) is
        // routed through here instead of launching wa.me directly from the tray, so the
        // handoff gets a real audit-log entry like every other in-app WhatsApp action.
        val whatsappUri = intent.getStringExtra("whatsapp_uri")
        if (!whatsappUri.isNullOrBlank()) {
            com.example.data.repository.ProSpaceRepository.getInstance().addAuditLog(
                actionType = "WHATSAPP_CONTACT_INITIATED",
                details = intent.getStringExtra("whatsapp_audit_detail") ?: "WhatsApp reply opened from a notification action.",
                severity = "INFO"
            )
            startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(whatsappUri)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            return
        }

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
        // resolution of "payment_return" happens in ProSpaceNavGraph.
        val data = intent.data
        if (data != null && data.scheme == "https" && data.host == "hopebearer-award.com" &&
            data.path?.startsWith("/payment") == true
        ) {
            targetTab = "payment_return"
        }
    }
}


