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
        val tab = intent.getStringExtra("target_tab")
        val bookingId = intent.getStringExtra("booking_id")
        if (!tab.isNullOrBlank()) {
            targetTab = tab
        }
        if (!bookingId.isNullOrBlank()) {
            targetBookingId = bookingId
        }
    }
}


