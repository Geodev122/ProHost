package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.MainActivity
import com.example.data.model.FCMAlert
import com.example.data.repository.ProHostRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class ProHostMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // The token is a credential for pushing to this device: it is stored only on the
        // user's own profile, never logged or written to the audit trail.
        // Persist the token to this user's profile so a Cloud Function can actually
        // reach this device with a real push later (see notifications/*.ts). A no-op
        // if nobody's signed in yet — the post-login/registration path in
        // ProHostViewModel backfills the token once a session exists.
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val repository = ProHostRepository.getInstance()
        serviceScope.launch {
            repository.registerFcmToken(uid, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("ProHostMessaging", "FCM push notification received from payload structure")

        // Parse alert title and body from the FCM Remote Message package
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "ProHost Notification"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "New alert received."
        val categoryType = remoteMessage.data["category"] ?: "GENERAL"
        val targetTab = remoteMessage.data["targetTab"]
        val bookingId = remoteMessage.data["bookingId"]
        val spaceId = remoteMessage.data["spaceId"]

        // Store inside the active singleton repository to immediately push updates to standard Compose UI flow
        val repository = ProHostRepository.getInstance()
        // Same id as the stored notification (push.ts), so the Firestore snapshot of the
        // notification centre replaces this instant copy instead of duplicating it.
        val alert = FCMAlert(
            id = remoteMessage.data["notificationId"] ?: java.util.UUID.randomUUID().toString(),
            title = title,
            body = body,
            category = categoryType,
            targetTab = targetTab,
            bookingId = bookingId,
            spaceId = spaceId
        )
        repository.addFCMAlert(alert)

        // Show standard physical status bar notification, deep-linking to the relevant
        // screen (and, when present, an instant WhatsApp reply) on tap — this used to
        // drop targetTab/bookingId/WhatsApp context on the floor even though
        // showPhysicalNotification already supported all of it.
        showPhysicalNotification(
            applicationContext,
            title,
            body,
            targetTab = targetTab,
            bookingId = bookingId,
            spaceId = spaceId,
            notificationType = categoryType
        )
    }

    companion object {
        private const val CHANNEL_ID = "prospace_realtime_alerts"
        private const val CHANNEL_NAME = "ProHost Real-time Alerts"
        private const val CHANNEL_DESCRIPTION = "Receives real-time updates regarding suite rental contracts, booking states, and host due reminders."

        // Subscription and billing notices get their own channel so people can tune them
        // separately (Android settings) from booking traffic.
        private const val BILLING_CHANNEL_ID = "prohost_billing"
        private const val BILLING_CHANNEL_NAME = "ProHost Premium & billing"
        private const val BILLING_CHANNEL_DESCRIPTION = "Subscription activations, renewals, payment problems and expiry. Admins also receive subscriber changes here."
        private val BILLING_CATEGORIES = setOf("PACKAGE_ACTIVATED", "PACKAGE_RENEWED", "PACKAGE_EXPIRED", "ADMIN_SUBSCRIPTION")

        /**
         * Core notification routine triggered by real-time Firebase Cloud Messaging and in-app system events.
         * Supports deep-linking directly to target screens.
         */
        /**
         * Registers the alerts channel. Called from ProHostApplication.onCreate so the
         * channel exists (and shows in system settings) before the first push arrives —
         * the manifest names it as FCM's default channel.
         */
        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            notificationManager.createNotificationChannel(
                NotificationChannel(BILLING_CHANNEL_ID, BILLING_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = BILLING_CHANNEL_DESCRIPTION
                }
            )
        }

        fun showPhysicalNotification(
            context: Context,
            title: String,
            body: String,
            targetTab: String? = null,
            bookingId: String? = null,
            spaceId: String? = null,
            notificationType: String? = null
        ) {
            // Without POST_NOTIFICATIONS (Android 13+) notify() is silently dropped; the
            // alert is still in the in-app alerts list either way.
            if (!com.example.util.NotificationPermissionManager.isGranted(context)) return
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ensureNotificationChannel(context)

            // Create a pending intent to open the MainActivity when clicked with target tab deep linking
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (targetTab != null) {
                    putExtra("target_tab", targetTab)
                }
                if (bookingId != null) {
                    putExtra("booking_id", bookingId)
                }
                if (spaceId != null) {
                    putExtra("space_id", spaceId)
                }
                putExtra("notification_type", notificationType ?: "GENERAL")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Build system bar notification targeting standard safe system alert asset icons
            val channelId = if (notificationType in BILLING_CATEGORIES) BILLING_CHANNEL_ID else CHANNEL_ID
            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
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
                    "owner_subscriptions" -> "Manage subscription"
                    "admin_console" -> "Open Admin Console"
                    "manage_listings" -> "My Listings"
                    else -> "Open Screen"
                }
                notificationBuilder.addAction(
                    android.R.drawable.ic_menu_view,
                    actionTitle,
                    pendingIntent
                )
            }

            notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
        }
    }
}
