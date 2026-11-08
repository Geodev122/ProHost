package com.example.util

import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P2.5g: each sound maps to the file the screens always played, and playing never throws (silence is the failure mode). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoundPlayerTest {
    @Test
    fun eachSoundIsTheFileTheScreenUsedToPlay() {
        assertEquals(R.raw.listing_published, AndroidSoundPlayer.rawIdOf(AppSound.LISTING_PUBLISHED))
        assertEquals(R.raw.booking_request_in, AndroidSoundPlayer.rawIdOf(AppSound.BOOKING_REQUEST_IN))
        assertEquals(R.raw.booking_update, AndroidSoundPlayer.rawIdOf(AppSound.BOOKING_UPDATE))
        assertEquals(R.raw.purchase_success, AndroidSoundPlayer.rawIdOf(AppSound.PURCHASE_SUCCESS))
        assertEquals(AppSound.entries.size, AppSound.entries.map { AndroidSoundPlayer.rawIdOf(it) }.toSet().size)
    }

    @Test
    fun playingNeverThrows() {
        val player = AndroidSoundPlayer(ApplicationProvider.getApplicationContext())
        AppSound.entries.forEach { player.play(it) }
    }
}
