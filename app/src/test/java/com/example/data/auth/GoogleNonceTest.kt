package com.example.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleNonceTest {
    private fun jwt(payloadJson: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return listOf("""{"alg":"RS256"}""", payloadJson, "signature").mapIndexed { i, part ->
            if (i == 2) part else enc.encodeToString(part.toByteArray())
        }.joinToString(".")
    }

    @Test fun `a token carrying the nonce matches`() {
        val nonce = GoogleNonce.newNonce()
        assertTrue(GoogleNonce.idTokenMatchesNonce(jwt("""{"sub":"1","nonce":"$nonce","email_verified":true}"""), nonce))
    }

    @Test fun `a token carrying another nonce is refused`() {
        assertFalse(GoogleNonce.idTokenMatchesNonce(jwt("""{"nonce":"replayed-from-another-request"}"""), GoogleNonce.newNonce()))
    }

    @Test fun `a token without a nonce claim is refused`() {
        assertFalse(GoogleNonce.idTokenMatchesNonce(jwt("""{"sub":"1"}"""), GoogleNonce.newNonce()))
    }

    @Test fun `an empty expected nonce never matches`() {
        assertFalse(GoogleNonce.idTokenMatchesNonce(jwt("""{"nonce":""}"""), ""))
    }

    @Test fun `malformed tokens are refused, not thrown`() {
        val nonce = GoogleNonce.newNonce()
        assertFalse(GoogleNonce.idTokenMatchesNonce("", nonce))
        assertFalse(GoogleNonce.idTokenMatchesNonce("only.two", nonce))
        assertFalse(GoogleNonce.idTokenMatchesNonce("a.%%%not-base64%%%.c", nonce))
        assertFalse(GoogleNonce.idTokenMatchesNonce(jwt("not json at all"), nonce))
    }

    @Test fun `nonces are long, url-safe and unique`() {
        val a = GoogleNonce.newNonce()
        val b = GoogleNonce.newNonce()
        assertEquals(43, a.length)
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(a, b)
    }
}
