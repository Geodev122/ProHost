package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.FCMAlert
import com.example.data.repository.ProSpaceRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.UUID

class ProSpaceMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("ProSpaceMessaging", "New FCM token registered: $token")
        
        // Log to our system audit trails for full admin traceability
        val repository = ProSpaceRepository.getInstance()
        repository.addAuditLog(
            actionType = "FCM_TOKEN_REGISTERED",
            details = "New Firebase Cloud Messaging device registration signature generated successfully: ${token.take(16)}...",
            severity = "SECURE"
        )
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("ProSpaceMessaging", "FCM push notification received from payload structure")

        // Parse alert title and body from the FCM Remote Message package
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "ProSpace Notification"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "New alert received."
        val categoryType = remoteMessage.data["category"] ?: "BOOKING_ACCEPTANCE"

        // Store inside the active singleton repository to immediately push updates to standard Compose UI flow
        val repository = ProSpaceRepository.getInstance()
        val alert = FCMAlert(
            title = title,
            body = body,
            category = categoryType
        )
        repository.addFCMAlert(alert)

        // Show standard physical status bar notification
        showPhysicalNotification(applicationContext, title, body)
    }

    companion object {
        private const val CHANNEL_ID = "prospace_realtime_alerts"
        private const val CHANNEL_NAME = "ProSpace Real-time Alerts"
        private const val CHANNEL_DESCRIPTION = "Receives real-time updates regarding suite rental contracts, booking states, and host due reminders."

        /**
         * Core notification routine triggered by real-time Firebase Cloud Messaging and in-app system events.
         * Supports deep-linking directly to target screens and instant WhatsApp reply actions.
         */
        fun showPhysicalNotification(
            context: Context,
            title: String,
            body: String,
            targetTab: String? = null,
            bookingId: String? = null,
            whatsAppPhone: String? = null,
            whatsAppMessage: String? = null
        ) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Ensure the dynamic system channel is registered on Android O and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableLights(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Create a pending intent to open the MainActivity when clicked with target tab deep linking
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (targetTab != null) {
                    putExtra("target_tab", targetTab)
                }
                if (bookingId != null) {
                    putExtra("booking_id", bookingId)
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Build system bar notification targeting standard safe system alert asset icons
            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))

            // Action 1: Deep Link to Tab Action
            if (targetTab != null) {
                val actionTitle = when (targetTab) {
                    "owner_requests" -> "View Requests"
                    "owner_progress" -> "View Progress"
                    "pro_rentals" -> "My Rentals"
                    else -> "Open Screen"
                }
                notificationBuilder.addAction(
                    android.R.drawable.ic_menu_view,
                    actionTitle,
                    pendingIntent
                )
            }

            // Action 2: Instant One-Tap WhatsApp Action
            if (!whatsAppPhone.isNullOrBlank()) {
                val cleanPhone = whatsAppPhone.replace("+", "").replace(" ", "").replace("-", "")
                val textPayload = whatsAppMessage ?: "Hello, replying regarding the ProSpace booking alert: $title"
                val encoded = java.net.URLEncoder.encode(textPayload, "UTF-8")
                val waIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$cleanPhone?text=$encoded")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val waPendingIntent = PendingIntent.getActivity(
                    context,
                    (System.currentTimeMillis() + 1).toInt(),
                    waIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                notificationBuilder.addAction(
                    android.R.drawable.stat_notify_chat,
                    "WhatsApp Reply",
                    waPendingIntent
                )
            }

            notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
        }
    }
}
