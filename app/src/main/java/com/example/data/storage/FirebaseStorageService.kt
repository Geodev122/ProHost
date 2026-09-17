package com.example.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * FirebaseStorageService
 *
 * The single Cloud Storage access layer for ProHost: real file uploads for credential
 * documents and listing images with automatic bitmap compression to guarantee
 * fast, reliable uploads and eliminate network timeout errors.
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
     * `id_documents/{uid}.{ext}`.
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
     * Uploads a registrant's profile picture to `profile_pictures/{uid}.{ext}` with automatic compression.
     */
    suspend fun uploadProfilePicture(
        uid: String,
        fileUri: Uri,
        fileExtension: String = "jpg",
        context: Context = try { FirebaseApp.getInstance().applicationContext } catch (e: Exception) { android.os.Environment.getDataDirectory() /* fallback */ ; throw e },
        onProgress: (Float) -> Unit = {}
    ): String? = uploadCompressedImageAndGetUrl(
        context = context,
        ref = storage?.reference?.child("profile_pictures/$uid/photo.$fileExtension"),
        fileUri = fileUri,
        onProgress = onProgress
    )

    /**
     * Uploads a Pro Host's proof of ownership / right to rent.
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
     * Uploads the document a Pro Host chose to EARN the Listing Verified badge.
     */
    suspend fun uploadListingVerificationDocument(
        spaceId: String,
        fileUri: Uri,
        fileExtension: String,
        onProgress: (Float) -> Unit = {}
    ): String? = uploadAndGetUrl(
        ref = storage?.reference?.child("listing_verification_docs/$spaceId/verification_proof.$fileExtension"),
        fileUri = fileUri,
        onProgress = onProgress
    )

    /**
     * Uploads the signed leasing agreement attached to a booking request.
     */
    suspend fun uploadBookingAgreement(
        bookingId: String,
        fileUri: Uri,
        fileExtension: String,
        onProgress: (Float) -> Unit = {}
    ): String? = uploadAndGetUrl(
        ref = storage?.reference?.child("booking_agreements/$bookingId/agreement.$fileExtension"),
        fileUri = fileUri,
        onProgress = onProgress
    )

    /**
     * Uploads a listing photo to `listings/{spaceId}/{imageId}.{ext}` with automatic compression.
     */
    suspend fun uploadListingImage(
        spaceId: String,
        imageId: String,
        fileUri: Uri,
        fileExtension: String = "jpg",
        context: Context = try { FirebaseApp.getInstance().applicationContext } catch (e: Exception) { throw e },
        onProgress: (Float) -> Unit = {}
    ): String? = uploadCompressedImageAndGetUrl(
        context = context,
        ref = storage?.reference?.child("listings/$spaceId/$imageId.$fileExtension"),
        fileUri = fileUri,
        onProgress = onProgress
    )

    /**
     * Per-subdivision (room/desk) images with automatic compression.
     */
    suspend fun uploadSubdivisionImage(
        spaceId: String,
        subdivisionId: String,
        imageId: String,
        fileUri: Uri,
        fileExtension: String = "jpg",
        context: Context = try { FirebaseApp.getInstance().applicationContext } catch (e: Exception) { throw e },
        onProgress: (Float) -> Unit = {}
    ): String? = uploadCompressedImageAndGetUrl(
        context = context,
        ref = storage?.reference?.child("listings/$spaceId/sub-$subdivisionId-$imageId.$fileExtension"),
        fileUri = fileUri,
        onProgress = onProgress
    )

    /**
     * Uploads a new immutable version of an admin-managed legal document.
     */
    suspend fun uploadLegalDocumentVersion(
        docId: String,
        version: Int,
        fileUri: Uri,
        contentType: String = "text/html",
        fileExtension: String = "html"
    ): String? =
        uploadAndGetUrl(
            ref = storage?.reference?.child("legal_documents/$docId/v$version.$fileExtension"),
            fileUri = fileUri,
            onProgress = {},
            contentType = contentType
        )

    private suspend fun uploadAndGetUrl(
        ref: StorageReference?,
        fileUri: Uri,
        onProgress: (Float) -> Unit,
        contentType: String? = null
    ): String? {
        if (ref == null) {
            Log.w(TAG, "Storage unavailable; skipping upload")
            return null
        }
        return try {
            val metadata = contentType?.let {
                com.google.firebase.storage.StorageMetadata.Builder().setContentType(it).build()
            }
            val uploadTask = if (metadata != null) ref.putFile(fileUri, metadata) else ref.putFile(fileUri)
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

    private suspend fun uploadCompressedImageAndGetUrl(
        context: Context,
        ref: StorageReference?,
        fileUri: Uri,
        onProgress: (Float) -> Unit
    ): String? {
        if (ref == null) {
            Log.w(TAG, "Storage unavailable; skipping upload")
            return null
        }
        return try {
            val bytes = withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(fileUri)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                val maxDim = 1600
                var sampleSize = 1
                val width = options.outWidth
                val height = options.outHeight
                if (width > maxDim || height > maxDim) {
                    val halfW = width / 2
                    val halfH = height / 2
                    while ((halfW / sampleSize) >= maxDim && (halfH / sampleSize) >= maxDim) {
                        sampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val stream = context.contentResolver.openInputStream(fileUri)
                val bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
                stream?.close()

                val outputStream = ByteArrayOutputStream()
                bitmap?.compress(Bitmap.CompressFormat.JPEG, 82, outputStream)
                bitmap?.recycle()
                outputStream.toByteArray()
            }

            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()

            val uploadTask = ref.putBytes(bytes, metadata)
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
            Log.e(TAG, "Compressed image upload to ${ref.path} failed: ${e.message}")
            null
        }
    }
}
