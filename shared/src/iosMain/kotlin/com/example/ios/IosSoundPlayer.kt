package com.example.ios

import com.example.util.AppLog
import com.example.util.AppSound
import com.example.util.SoundPlayer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.Foundation.NSBundle

/**
 * iOS [SoundPlayer]: the same four WAVs as Android (bundled from `ios/ProHost/Resources/Sounds`), played with AVAudioPlayer in
 * the ambient category, so the silent switch mutes them and other audio keeps playing. Never throws.
 */
@OptIn(ExperimentalForeignApi::class)
object IosSoundPlayer : SoundPlayer {
    /** AVAudioPlayer stops when released: keep the last player of each sound alive. */
    private val playing = mutableMapOf<AppSound, AVAudioPlayer>()
    private var sessionReady = false

    override fun play(sound: AppSound) {
        try {
            if (!sessionReady) {
                AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, error = null)
                sessionReady = true
            }
            val url = NSBundle.mainBundle.URLForResource(fileOf(sound), withExtension = "wav") ?: return
            val player = AVAudioPlayer(contentsOfURL = url, error = null)
            player.prepareToPlay()
            player.play()
            playing[sound] = player
        } catch (e: Exception) {
            AppLog.w("IosSoundPlayer", "Failed to play ${sound.name}: ${e.message}")
        }
    }

    private fun fileOf(sound: AppSound): String = when (sound) {
        AppSound.LISTING_PUBLISHED -> "listing_published"
        AppSound.BOOKING_REQUEST_IN -> "booking_request_in"
        AppSound.BOOKING_UPDATE -> "booking_update"
        AppSound.PURCHASE_SUCCESS -> "purchase_success"
    }
}
