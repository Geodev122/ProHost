package com.example.data.gateway

import com.example.util.resumeOnce
import com.example.util.FileAccess
import com.example.util.PickedFile
import com.example.util.nowMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** One finished upload: the download URL and the private `gs://` reference, or [error]. */
data class UploadResult(val downloadUrl: String?, val gsUri: String?, val error: String?)

/**
 * Cloud Storage and image compression on the platform's SDK (iOS: `IosStorageBridge` over `FirebaseStorageBridge.swift`).
 * [compressJpeg] applies Android's rule: longest side at most [maxDim] px, JPEG at [quality] (0-100); null when the bytes are no image.
 */
interface StorageBridge {
    fun uploadBytes(path: String, bytes: ByteArray, contentType: String?, onProgress: (Float) -> Unit, completion: (UploadResult) -> Unit)
    fun uploadPickedFile(path: String, file: PickedFile, contentType: String?, onProgress: (Float) -> Unit, completion: (UploadResult) -> Unit)
    fun compressJpeg(bytes: ByteArray, maxDim: Int, quality: Int): ByteArray?
    fun deletePrefix(path: String, completion: (Boolean) -> Unit)
}

/**
 * [StorageGateway] over a [StorageBridge], with the paths, content types, compression rule and return values of the Android
 * `FirebaseStorageService` (`BridgedStorageGatewayTest`). Used by iOS.
 */
class BridgedStorageGateway(private val bridge: StorageBridge, private val now: () -> Long = { nowMillis() }) : StorageGateway {

    private suspend fun upload(call: ((UploadResult) -> Unit) -> Unit): UploadResult? = try {
        suspendCancellableCoroutine<UploadResult> { cont -> call { cont.resumeOnce(it) } }.takeIf { it.error == null }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun compressed(path: String, rawBytes: ByteArray, onProgress: (Float) -> Unit): String? {
        val jpeg = bridge.compressJpeg(rawBytes, MAX_IMAGE_DIM, JPEG_QUALITY) ?: return null
        return upload { bridge.uploadBytes(path, jpeg, "image/jpeg", onProgress, it) }?.downloadUrl
    }

    override suspend fun uploadProfilePicture(uid: String, file: PickedFile, onProgress: (Float) -> Unit): String? {
        val bytes = FileAccess.require().readBytes(file) ?: return null
        return compressed("profile_pictures/$uid/photo.jpg", bytes, onProgress)
    }

    // Returns the private gs:// reference, never a download URL (the listing is readable by every signed-in user).
    override suspend fun uploadListingVerificationDocument(
        spaceId: String, file: PickedFile, fileExtension: String, onProgress: (Float) -> Unit
    ): String? = upload {
        bridge.uploadPickedFile("listing_verification_docs/$spaceId/verification_proof.$fileExtension", file, documentContentType(fileExtension), onProgress, it)
    }?.gsUri

    // A fresh name per upload: signed leases are create-only, so a retry must not overwrite the first upload.
    override suspend fun uploadBookingAgreement(
        bookingId: String, file: PickedFile, fileExtension: String, onProgress: (Float) -> Unit
    ): String? = upload {
        bridge.uploadPickedFile("booking_agreements/$bookingId/agreement-${now()}.$fileExtension", file, documentContentType(fileExtension), onProgress, it)
    }?.downloadUrl

    override suspend fun uploadListingImageBytes(
        spaceId: String, imageId: String, rawBytes: ByteArray, fileExtension: String, onProgress: (Float) -> Unit
    ): String? = compressed("listings/$spaceId/photos/$imageId.$fileExtension", rawBytes, onProgress)

    override suspend fun uploadSubdivisionImageBytes(
        spaceId: String, subdivisionId: String, imageId: String, rawBytes: ByteArray, fileExtension: String, onProgress: (Float) -> Unit
    ): String? = compressed("listings/$spaceId/subdivisions/$subdivisionId/$imageId.$fileExtension", rawBytes, onProgress)

    override suspend fun purgeListingStorage(spaceId: String): Boolean =
        suspendCancellableCoroutine { cont -> bridge.deletePrefix("listings/$spaceId") { cont.resumeOnce(it) } }

    companion object {
        /** Android's compression rule (`FirebaseStorageService.uploadCompressedImageBytesAndGetUrl`). */
        const val MAX_IMAGE_DIM = 1600
        const val JPEG_QUALITY = 82

        fun documentContentType(fileExtension: String): String? = when (fileExtension.lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            else -> null
        }
    }
}
