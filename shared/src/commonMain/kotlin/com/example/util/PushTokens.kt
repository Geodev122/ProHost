package com.example.util

import kotlin.concurrent.Volatile

/**
 * This device's push token, as shared code needs it (sign-out keeps another phone's token). Android: Firebase Messaging
 * (`ProHostApplication`); iOS: FCM over APNs once push lands (P7). Null when none is available (also the default, as in unit tests).
 */
object PushTokens {
    @Volatile
    var fetch: suspend () -> String? = { null }

    suspend fun current(): String? = fetch()
}
