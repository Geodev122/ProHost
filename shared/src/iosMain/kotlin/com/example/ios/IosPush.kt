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

/** Where a notification tap should land; the shell reads it like Android's intent extras. */
data class PushTarget(val tab: String?, val bookingId: String?, val spaceId: String?)

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

    /** A tapped notification's data keys (the server's `targetTab`, `bookingId`, `spaceId`). */
    fun openNotification(targetTab: String?, bookingId: String?, spaceId: String?) {
        pendingTarget = PushTarget(
            tab = targetTab?.takeIf { it.isNotBlank() },
            bookingId = bookingId?.takeIf { it.isNotBlank() },
            spaceId = spaceId?.takeIf { it.isNotBlank() }
        )
    }
}
