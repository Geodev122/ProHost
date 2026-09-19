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
        FirebaseStorage.getInstance("gs://prohost-f766f.firebasestorage.app")
    } catch (e: Exception) {
        try {
            FirebaseStorage.getInstance()
        } catch (e2: Exception) {
            Log.w(TAG, "FirebaseStorage instance unavailable: ${e2.message}")
            null
        }
    }
) {
    companion object {
        private const val TAG = "FirebaseStorageService"

        @Volatile
        var lastUploadError: String? = null

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
    ): String? {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
        } ?: return null
        return uploadCompressedImageBytesAndGetUrl(
            ref = storage?.reference?.child("profile_pictures/$uid/photo.$fileExtension"),
            rawBytes = bytes,
            onProgress = onProgress
        )
    }

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
     * Uploads a listing photo to `listings/{spaceId}/photos/{imageId}.jpg` with automatic compression.
     */
    suspend fun uploadListingImageBytes(
        spaceId: String,
        imageId: String,
        rawBytes: ByteArray,
        fileExtension: String = "jpg",
        onProgress: (Float) -> Unit = {}
    ): String? = uploadCompressedImageBytesAndGetUrl(
        ref = storage?.reference?.child("listings/$spaceId/photos/$imageId.$fileExtension"),
        rawBytes = rawBytes,
        onProgress = onProgress
    )

    /**
     * Per-subdivision (room/desk) images with automatic compression stored in
     * `listings/{spaceId}/subdivisions/{subdivisionId}/{imageId}.jpg`.
     */
    suspend fun uploadSubdivisionImageBytes(
        spaceId: String,
        subdivisionId: String,
        imageId: String,
        rawBytes: ByteArray,
        fileExtension: String = "jpg",
        onProgress: (Float) -> Unit = {}
    ): String? = uploadCompressedImageBytesAndGetUrl(
        ref = storage?.reference?.child("listings/$spaceId/subdivisions/$subdivisionId/$imageId.$fileExtension"),
        rawBytes = rawBytes,
        onProgress = onProgress
    )

    /**
     * Client-side bucket purge for all images/files stored under `listings/{spaceId}/`.
     */
    suspend fun purgeListingStorage(spaceId: String): Boolean {
        val rootRef = storage?.reference?.child("listings/$spaceId") ?: return false
        return try {
            val listResult = rootRef.listAll().await()
            for (fileRef in listResult.items) {
                try { fileRef.delete().await() } catch (e: Exception) { Log.w(TAG, "Failed deleting ${fileRef.path}: ${e.message}") }
            }
            for (prefixRef in listResult.prefixes) {
                val subList = prefixRef.listAll().await()
                for (fileRef in subList.items) {
                    try { fileRef.delete().await() } catch (e: Exception) { Log.w(TAG, "Failed deleting ${fileRef.path}: ${e.message}") }
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "purgeListingStorage failed for spaceId $spaceId: ${e.message}")
            false
        }
    }

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

    private suspend fun uploadCompressedImageBytesAndGetUrl(
        ref: StorageReference?,
        rawBytes: ByteArray,
        onProgress: (Float) -> Unit
    ): String? {
        if (ref == null) {
            Log.w(TAG, "Storage unavailable; skipping upload")
            return null
        }
        return try {
            val bytes = withContext(Dispatchers.IO) {
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)

                val maxDim = 1600
                var sampleSize = 1
                val w = boundsOptions.outWidth
                val h = boundsOptions.outHeight
                if (w > 0 && h > 0) {
                    while ((w / sampleSize) > maxDim || (h / sampleSize) > maxDim) {
                        sampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                var decodedBitmap: Bitmap? = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)

                if (decodedBitmap == null) {
                    throw IllegalStateException("Failed to decode image from raw bytes (length=${rawBytes.size})")
                }

                val currentMax = Math.max(decodedBitmap.width, decodedBitmap.height)
                if (currentMax > maxDim) {
                    val scale = maxDim.toFloat() / currentMax.toFloat()
                    val targetW = (decodedBitmap.width * scale).toInt()
                    val targetH = (decodedBitmap.height * scale).toInt()
                    val scaled = Bitmap.createScaledBitmap(decodedBitmap, targetW, targetH, true)
                    if (scaled != decodedBitmap) {
                        decodedBitmap.recycle()
                        decodedBitmap = scaled
                    }
                }

                ByteArrayOutputStream().use { outputStream ->
                    decodedBitmap.compress(Bitmap.CompressFormat.JPEG, 82, outputStream)
                    decodedBitmap.recycle()
                    outputStream.toByteArray()
                }
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
            lastUploadError = e.message ?: e.toString()
            Log.e(TAG, "Compressed image upload to ${ref.path} failed: ${e.message}", e)
            null
        }
    }
}
