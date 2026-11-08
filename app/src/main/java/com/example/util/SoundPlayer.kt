package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.R

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

/** Android [SoundPlayer]: a one-shot `MediaPlayer` as a notification-usage sonification (what each screen did inline), released when it completes. */
class AndroidSoundPlayer(context: Context) : SoundPlayer {
    private val app = context.applicationContext

    override fun play(sound: AppSound) {
        try {
            val mp = MediaPlayer.create(app, rawIdOf(sound))
            mp?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp?.setOnCompletionListener { it.release() }
            mp?.start()
        } catch (e: Exception) {
            Log.e("SoundPlayback", "Failed to play sound", e)
        }
    }

    companion object {
        fun rawIdOf(sound: AppSound): Int = when (sound) {
            AppSound.LISTING_PUBLISHED -> R.raw.listing_published
            AppSound.BOOKING_REQUEST_IN -> R.raw.booking_request_in
            AppSound.BOOKING_UPDATE -> R.raw.booking_update
            AppSound.PURCHASE_SUCCESS -> R.raw.purchase_success
        }
    }
}
