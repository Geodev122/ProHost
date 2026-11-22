package com.example.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.auth.SessionAuth
import com.example.data.repository.ProHostRepository
import com.example.util.PushTokens
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** The FCM registration token on iOS (Swift: `Messaging.messaging().token`); null until APNs has registered the device. */
interface PushTokenBridge {
    fun fcmToken(completion: (String?) -> Unit)
}

/** Where a notification tap or a link should land; the shell reads it like Android's intent extras. */
data class PushTarget(
    val tab: String?,
    val bookingId: String?,
    val spaceId: String?,
    val roomId: String? = null,
    val emailVerified: Boolean = false
)

/** Push on iOS: the token hook for the shared session code, token refreshes, and notification taps. */
object IosPush {
    private val scope = MainScope()

    /** The last tapped notification's target (snapshot state, read by `ProHostIosApp`). */
    var pendingTarget by mutableStateOf<PushTarget?>(null)
        private set

    fun install(bridge: PushTokenBridge) {
        PushTokens.fetch = { suspendCancellableCoroutine { cont -> bridge.fcmToken { cont.resume(it) } } }
    }

    /** Messaging delegate: a new token. Saved on the signed-in profile (Android: `onNewToken`); later sign-ins save it anyway. */
    fun tokenRefreshed(token: String) {
        val uid = runCatching { SessionAuth.currentUser()?.uid }.getOrNull() ?: return
        scope.launch { runCatching { ProHostRepository.getInstance().registerFcmToken(uid, token) } }
    }

    /**
     * A link the app was opened with (Universal Link `pro-host.tech/listing/…` or `prohost://…`), routed by the shared
     * [com.example.util.DeepLinkRouter] exactly like Android's `MainActivity`.
     */
    fun openUrl(url: String) {
        val components = platform.Foundation.NSURLComponents.componentsWithString(url) ?: return
        fun query(name: String): String? = components.queryItems
            ?.filterIsInstance<platform.Foundation.NSURLQueryItem>()
            ?.firstOrNull { it.name == name }?.value
        val route = com.example.util.DeepLinkRouter.route(
            com.example.util.DeepLinkRouter.Link(
                scheme = components.scheme,
                host = components.host,
                path = components.path,
                room = query("room"),
                code = query("code")
            )
        )
        if (route.isRedeem) com.example.data.billing.PendingPromoCode.offer(route.promoCode)
        if (route.listing == null && route.tab == null && !route.emailVerified) return
        val previous = pendingTarget
        pendingTarget = PushTarget(
            tab = route.tab ?: previous?.tab,
            bookingId = previous?.bookingId,
            spaceId = route.listing?.spaceId ?: previous?.spaceId,
            roomId = route.listing?.roomId ?: previous?.roomId,
            emailVerified = route.emailVerified || previous?.emailVerified == true
        )
    }

    /** A tapped notification's data keys (the server's `targetTab`, `bookingId`, `spaceId`). */
    fun openNotification(targetTab: String?, bookingId: String?, spaceId: String?, notificationId: String?) {
        // Tapping a push marks its inbox copy read, as on Android.
        notificationId?.takeIf { it.isNotBlank() }?.let {
            com.example.data.repository.ProHostRepository.getInstance().markAlertOpenedFromTray(it)
        }
        pendingTarget = PushTarget(
            tab = targetTab?.takeIf { it.isNotBlank() },
            bookingId = bookingId?.takeIf { it.isNotBlank() },
            spaceId = spaceId?.takeIf { it.isNotBlank() }
        )
    }
}
