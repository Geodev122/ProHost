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
     * Uploads a registrant's ID document (national ID / passport) to
     * `id_documents/{uid}.{ext}` — kept on file with no admin review workflow, required
     * once at registration for every account. Only the owning user or an Admin can read
     * this path — see storage.rules. Returns the download URL, or null if Storage is
     * unavailable or the upload fails.
     */
    suspend fun uploadIdDocument(
        uid: String,
        fileUri: Uri,
        fileExtension: String,
        onProgress: (Float) -> Unit = {}
    ): String? = uploadAndGetUrl(
        ref = storage?.reference?.child("id_documents/$uid/document.$fileExtension"),
        fileUri = fileUri,
        onProgress = onProgress
    )

    /**
     * Uploads a registrant's profile picture to `profile_pictures/{uid}.{ext}`. Publicly
     * readable (shown wherever a member's identity is displayed to others, e.g. a
     * booking request) but writable only by the account owner or an Admin.
     */
    suspend fun uploadProfilePicture(
        uid: String,
        fileUri: Uri,
        fileExtension: String,
        onProgress: (Float) -> Unit = {}
    ): String? = uploadAndGetUrl(
        ref = storage?.reference?.child("profile_pictures/$uid/photo.$fileExtension"),
        fileUri = fileUri,
        onProgress = onProgress
    )

    /**
     * Uploads a Pro Host's proof of ownership / right to rent a specific space to
     * `listing_ownership_docs/{spaceId}/ownership_proof.{ext}` — required per listing
     * at creation time, kept on file with no admin review workflow (see
     * SpaceListing.ownershipProofUrl). A separate path from listing photos
     * (`listings/{spaceId}/...`) since this can be a PDF, not just an image — see
     * storage.rules. Uses the same "listing owner, Firestore doc may not exist yet"
     * bootstrapping rule as listing photos.
     */
    suspend fun uploadOwnershipProofDocument(
        spaceId: String,
        fileUri: Uri,
        fileExtension: String,
        onProgress: (Float) -> Unit = {}
    ): String? = uploadAndGetUrl(
        ref = storage?.reference?.child("listing_ownership_docs/$spaceId/ownership_proof.$fileExtension"),
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
