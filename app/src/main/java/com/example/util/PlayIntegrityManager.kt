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
     * Requests a Play Integrity token signed by Google Play.
     * Pass an optional [customNonce] or auto-generate a unique UUID nonce.
     */
    suspend fun requestIntegrityToken(customNonce: String? = null): Result<String> {
        return try {
            val nonce = customNonce ?: UUID.randomUUID().toString()
            val request = IntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .setNonce(nonce)
                .build()

            val response: IntegrityTokenResponse = integrityManager.requestIntegrityToken(request).await()
            val token = response.token()
            Log.d(tag, "Successfully obtained Play Integrity token (${token.length} chars)")
            Result.success(token)
        } catch (e: Exception) {
            Log.w(tag, "Play Integrity token request failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
