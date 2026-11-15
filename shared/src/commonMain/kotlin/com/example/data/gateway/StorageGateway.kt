package com.example.data.gateway

import com.example.util.PickedFile

/**
 * The app's Cloud Storage uploads, platform-neutral. Android: `FirebaseStorageService`; iOS: a Swift
 * implementation. Paths, compression and the returned value (download URL, or a private `gs://`
 * reference for verification documents) are the platform implementation's job and must match Android.
 * Admin uploads (legal documents) stay out of this interface.
 */
interface StorageGateway {
    suspend fun uploadProfilePicture(uid: String, file: PickedFile, onProgress: (Float) -> Unit = {}): String?
    suspend fun uploadListingVerificationDocument(
        spaceId: String,
        file: PickedFile,
        fileExtension: String,
        onProgress: (Float) -> Unit = {}
    ): String?
    suspend fun uploadBookingAgreement(
        bookingId: String,
        file: PickedFile,
        fileExtension: String,
        onProgress: (Float) -> Unit = {}
    ): String?
    suspend fun uploadListingImageBytes(
        spaceId: String,
        imageId: String,
        rawBytes: ByteArray,
        fileExtension: String = "jpg",
        onProgress: (Float) -> Unit = {}
    ): String?
    suspend fun uploadSubdivisionImageBytes(
        spaceId: String,
        subdivisionId: String,
        imageId: String,
        rawBytes: ByteArray,
        fileExtension: String = "jpg",
        onProgress: (Float) -> Unit = {}
    ): String?
    suspend fun purgeListingStorage(spaceId: String): Boolean
}
