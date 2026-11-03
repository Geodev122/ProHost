package com.example.data.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A one-time value for each "Sign in with Google" attempt: it goes into the Credential Manager request (`setNonce`), Google
 * copies it into the ID token's `nonce` claim, and [idTokenMatchesNonce] checks it before the token is handed to Firebase, so an
 * ID token that was replayed or injected from another request is refused. Firebase itself does not check this claim for
 * Google tokens; the check is local and pure.
 */
object GoogleNonce {
    private val random = SecureRandom()

    /** 32 random bytes, URL-safe base64 without padding (43 characters). */
    fun newNonce(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** True only when [idToken] is a JWT whose payload carries exactly [nonce]; false on any parse problem. */
    fun idTokenMatchesNonce(idToken: String, nonce: String): Boolean {
        if (nonce.isEmpty()) return false
        val claim = nonceClaim(idToken) ?: return false
        return MessageDigest.isEqual(claim.toByteArray(Charsets.UTF_8), nonce.toByteArray(Charsets.UTF_8))
    }

    private fun nonceClaim(idToken: String): String? {
        val parts = idToken.split('.')
        if (parts.size != 3) return null
        return try {
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
            // The payload is a small JSON object; read the one string claim without pulling in a JSON model.
            Regex("\"nonce\"\\s*:\\s*\"([^\"\\\\]*)\"").find(payload)?.groupValues?.get(1)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
