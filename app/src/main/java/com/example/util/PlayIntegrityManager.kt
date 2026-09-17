package com.example.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
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
     * Checks whether Google Play Services is available and ready for Integrity API calls.
     */
    fun isIntegrityAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val result = availability.isGooglePlayServicesAvailable(context)
        return result == ConnectionResult.SUCCESS
    }

    /**
     * Generates a web-safe Base64 SHA-256 hashed nonce from a raw payload string.
     */
    fun generateHashedNonce(rawPayload: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(rawPayload.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Requests a Play Integrity token signed by Google Play.
     * Pass an optional [customNonce] or auto-generate a unique Base64 nonce.
     */
    suspend fun requestIntegrityToken(customNonce: String? = null): Result<String> {
        return try {
            val nonce = customNonce ?: generateHashedNonce(UUID.randomUUID().toString())
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
