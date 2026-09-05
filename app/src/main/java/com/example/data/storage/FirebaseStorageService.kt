package com.example.data.storage

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await

/**
 * FirebaseStorageService
 *
 * The single Cloud Storage access layer for ProHost: real file uploads for credential
 * documents and listing images. Replaces the previous simulation (a random file size, a
 * few delay() calls, and the local content:// picker Uri stored as-is — never actually
 * uploaded anywhere).
 */
class FirebaseStorageService(
    private val storage: FirebaseStorage? = try {
        FirebaseStorage.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseStorage instance unavailable: ${e.message}")
        null
    }
) {
    companion object {
        private const val TAG = "FirebaseStorageService"

        @Volatile
        private var INSTANCE: FirebaseStorageService? = null

        fun getInstance(): FirebaseStorageService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseStorageService().also { INSTANCE = it }
            }
        }
    }

    /**
     * Uploads a professional credential document to `credentials/{uid}/{docId}.{ext}`.
     * Only the owning user (or an Admin, for review) can read this path — see
     * storage.rules. Returns the download URL, or null if Storage is unavailable or the
     * upload fails.
     */
    suspend fun uploadCredentialDocument(
        uid: String,
        docId: String,
        fileUri: Uri,
        fileExtension: String,
        onProgress: (Float) -> Unit = {}
    ): String? = uploadAndGetUrl(
        ref = storage?.reference?.child("credentials/$uid/$docId.$fileExtension"),
        fileUri = fileUri,
        onProgress = onProgress
    )

    /**
     * Uploads a listing photo to `listings/{spaceId}/{imageId}.{ext}`. Publicly readable
     * (listings are shown to unauthenticated browsers of the discovery feed) but writable
     * only by the listing's owner or an Admin — see storage.rules. Returns the download
     * URL, or null if Storage is unavailable or the upload fails.
     */
    suspend fun uploadListingImage(
        spaceId: String,
        imageId: String,
        fileUri: Uri,
        fileExtension: String,
        onProgress: (Float) -> Unit = {}
    ): String? = uploadAndGetUrl(
        ref = storage?.reference?.child("listings/$spaceId/$imageId.$fileExtension"),
        fileUri = fileUri,
        onProgress = onProgress
    )

    private suspend fun uploadAndGetUrl(
        ref: StorageReference?,
        fileUri: Uri,
        onProgress: (Float) -> Unit
    ): String? {
        if (ref == null) {
            Log.w(TAG, "Storage unavailable; skipping upload")
            return null
        }
        return try {
            val uploadTask = ref.putFile(fileUri)
            uploadTask.addOnProgressListener { snapshot ->
                val progress = if (snapshot.totalByteCount > 0) {
                    snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount.toFloat()
                } else {
                    0f
                }
                onProgress(progress)
            }
            uploadTask.await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Upload to ${ref.path} failed: ${e.message}")
            null
        }
    }
}
