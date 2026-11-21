package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URLEncoder

/** The shared encoder must equal java.net.URLEncoder (UTF-8), which the WhatsApp links used before. */
class FormUrlEncodingTest {
    @Test fun `matches URLEncoder on texts the app sends`() {
        val samples = listOf(
            "", "Hello world", "Hi! I'd love to discuss availability & next steps (L-ABC123).",
            "مرحبا، أود الاستفسار عن المساحة", "Price: \$25 / hour · 50% off", "a+b=c?d#e/f\\g\"h'i",
            "line1\nline2\r\ttab", "emoji 😀👍 ok", "~!@\$^*()_-.[]{}|;:<>,`",
        )
        samples.forEach { assertEquals(it, URLEncoder.encode(it, "UTF-8"), formUrlEncode(it)) }
    }

    @Test fun `matches URLEncoder for every printable ASCII character`() {
        val all = (32..126).map { it.toChar() }.joinToString("")
        assertEquals(URLEncoder.encode(all, "UTF-8"), formUrlEncode(all))
    }
}
