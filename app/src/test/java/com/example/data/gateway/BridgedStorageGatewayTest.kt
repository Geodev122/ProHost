package com.example.data.gateway

import com.example.util.FileAccess
import com.example.util.PickedFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The shared (iOS) storage gateway must use the Android `FirebaseStorageService` paths, content types, compression rule and returns. */
class BridgedStorageGatewayTest {
    private class Rec : StorageBridge {
        val uploads = mutableListOf<Triple<String, String?, String>>() // path, content type, kind
        var compressArgs: Pair<Int, Int>? = null
        var compressible = true
        var deleted: String? = null
        override fun uploadBytes(path: String, bytes: ByteArray, contentType: String?, onProgress: (Float) -> Unit, completion: (UploadResult) -> Unit) {
            uploads += Triple(path, contentType, "bytes"); completion(UploadResult("https://dl/$path", "gs://b/$path", null))
        }
        override fun uploadPickedFile(path: String, file: PickedFile, contentType: String?, onProgress: (Float) -> Unit, completion: (UploadResult) -> Unit) {
            uploads += Triple(path, contentType, "file"); completion(UploadResult("https://dl/$path", "gs://b/$path", null))
        }
        override fun compressJpeg(bytes: ByteArray, maxDim: Int, quality: Int): ByteArray? {
            compressArgs = maxDim to quality; return if (compressible) byteArrayOf(1) else null
        }
        override fun deletePrefix(path: String, completion: (Boolean) -> Unit) { deleted = path; completion(true) }
    }

    @After fun clear() { FileAccess.current = null }

    @Test fun pathsTypesAndReturnsMatchAndroid() = runBlocking {
        FileAccess.current = object : FileAccess {
            override fun extension(file: PickedFile, fallback: String) = fallback
            override suspend fun readBytes(file: PickedFile) = byteArrayOf(9)
            override suspend fun sizeBytes(file: PickedFile): Long? = 1
        }
        val r = Rec()
        val g = BridgedStorageGateway(r, now = { 1234L })
        assertEquals("https://dl/profile_pictures/u1/photo.jpg", g.uploadProfilePicture("u1", PickedFile("f")))
        assertEquals(1600 to 82, r.compressArgs)
        assertEquals("gs://b/listing_verification_docs/s1/verification_proof.pdf", g.uploadListingVerificationDocument("s1", PickedFile("f"), "pdf"))
        assertEquals("https://dl/booking_agreements/b1/agreement-1234.jpg", g.uploadBookingAgreement("b1", PickedFile("f"), "jpg"))
        assertEquals("https://dl/listings/s1/photos/i1.jpg", g.uploadListingImageBytes("s1", "i1", byteArrayOf(1)))
        assertEquals("https://dl/listings/s1/subdivisions/d1/i2.jpg", g.uploadSubdivisionImageBytes("s1", "d1", "i2", byteArrayOf(1)))
        assertEquals(
            listOf(
                Triple("profile_pictures/u1/photo.jpg", "image/jpeg", "bytes"),
                Triple("listing_verification_docs/s1/verification_proof.pdf", "application/pdf", "file"),
                Triple("booking_agreements/b1/agreement-1234.jpg", "image/jpeg", "file"),
                Triple("listings/s1/photos/i1.jpg", "image/jpeg", "bytes"),
                Triple("listings/s1/subdivisions/d1/i2.jpg", "image/jpeg", "bytes")
            ),
            r.uploads
        )
        assertEquals(true, g.purgeListingStorage("s1")); assertEquals("listings/s1", r.deleted)
    }

    @Test fun notAnImageUploadsNothing() = runBlocking {
        val r = Rec().apply { compressible = false }
        assertNull(BridgedStorageGateway(r).uploadListingImageBytes("s", "i", byteArrayOf(0)))
        assertEquals(emptyList<Triple<String, String?, String>>(), r.uploads)
    }
}
