package com.example.util

import kotlin.concurrent.Volatile

/** The branded sounds the app plays. Platform code maps each to its own audio file. */
enum class AppSound { LISTING_PUBLISHED, BOOKING_REQUEST_IN, BOOKING_UPDATE, PURCHASE_SUCCESS }

/** Plays an [AppSound]. Fire-and-forget; never throws (a device that cannot play it simply stays silent). One implementation per platform. */
interface SoundPlayer {
    fun play(sound: AppSound)

    companion object {
        /** Registered once by `ProHostApplication`; tests set their own. */
        @Volatile
        var current: SoundPlayer? = null

        fun require(): SoundPlayer = current ?: error("SoundPlayer is not initialised (ProHostApplication.onCreate)")
    }
}
