package com.example.util

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Encapsulates Google Play Integrity API operations.
 * Requests cryptographically signed integrity tokens from Google Play Services
 * using cloud project number 646730915838 to verify device & binary genuineness.
 */
class PlayIntegrityManager(private val context: Context) {

    private val tag = "PlayIntegrityManager"
    private val cloudProjectNumber = 646730915838L

    private val integrityManager by lazy {
        IntegrityManagerFactory.create(context)
    }

    /**
     * Requests a Play Integrity token whose nonce is bound to [uid] (the server rejects a
     * token whose nonce belongs to another user, so a captured token can't be replayed).
     * Returns null for debug builds: they aren't installed from Play, so their verdict is
     * always UNRECOGNIZED_VERSION and the server would block testers' sign-in.
     */
    suspend fun requestIntegrityToken(uid: String): String? {
        if (com.example.BuildConfig.DEBUG) return null
        return try {
            val raw = "$uid:${UUID.randomUUID()}".toByteArray(Charsets.UTF_8)
            val nonce = android.util.Base64.encodeToString(
                raw,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
            val request = IntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .setNonce(nonce)
                .build()
            val response: IntegrityTokenResponse = integrityManager.requestIntegrityToken(request).await()
            response.token()
        } catch (e: Exception) {
            Log.w(tag, "Play Integrity token request failed: ${e.message}", e)
            null
        }
    }
}
