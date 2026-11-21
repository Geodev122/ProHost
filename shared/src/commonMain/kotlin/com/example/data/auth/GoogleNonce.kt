package com.example.data.auth

import com.example.util.secureRandomBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A one-time value for each "Sign in with Google" attempt: it goes into the Credential Manager request (`setNonce`), Google
 * copies it into the ID token's `nonce` claim, and [idTokenMatchesNonce] checks it before the token is handed to Firebase, so an
 * ID token that was replayed or injected from another request is refused. Firebase itself does not check this claim for
 * Google tokens; the check is local and pure.
 */
@OptIn(ExperimentalEncodingApi::class)
object GoogleNonce {
    // URL-safe alphabet; encodes without padding and decodes with or without it (as Android's URL_SAFE | NO_PADDING did).
    private val urlSafe = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /** 32 random bytes, URL-safe base64 without padding (43 characters). */
    fun newNonce(): String {
        return urlSafe.encode(secureRandomBytes(32))
    }

    /** True only when [idToken] is a JWT whose payload carries exactly [nonce]; false on any parse problem. */
    fun idTokenMatchesNonce(idToken: String, nonce: String): Boolean {
        if (nonce.isEmpty()) return false
        val claim = nonceClaim(idToken) ?: return false
        return constantTimeEquals(claim.encodeToByteArray(), nonce.encodeToByteArray())
    }

    private fun nonceClaim(idToken: String): String? {
        val parts = idToken.split('.')
        if (parts.size != 3) return null
        return try {
            val payload = urlSafe.decode(parts[1]).decodeToString()
            // The payload is a small JSON object; read the one string claim without pulling in a JSON model.
            Regex("\"nonce\"\\s*:\\s*\"([^\"\\\\]*)\"").find(payload)?.groupValues?.get(1)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Same contract as MessageDigest.isEqual: time depends only on the lengths, not on where the bytes differ. */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
