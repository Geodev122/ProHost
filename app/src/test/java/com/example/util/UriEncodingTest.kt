package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The shared [uriEncode] gives exactly what `android.net.Uri.encode` gave the support e-mail. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UriEncodingTest {
    @Test
    fun matchesAndroidUriEncode() {
        val samples = listOf(
            "ProHost Support — SPECIALIST account",
            "Account: Rewa Rammal (rewa@example.com)\nAccount code: U-ABC123\n\nDescribe your question or issue below:\n",
            "Account: جورج النجار (x+y@mail.com)", "a-b_c.d!e~f'g(h)i*j", "100% & more ? = # / : ;", "",
        )
        for (s in samples) assertEquals(s, android.net.Uri.encode(s), uriEncode(s))
    }
}
