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
     * Uploads the document a Pro Host chose to EARN the Listing Verified badge —
     * either a signed re-rental authorization or proof of self-ownership (see
     * SpaceListing.verificationDocUrl's doc comment). A separate, optional upload
     * from uploadOwnershipProofDocument above, which is required-but-unchecked at
     * publish time; this one is optional-but-checked by
     * FirebaseFunctionsClient.requestListingVerification.
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
     * Uploads the signed leasing agreement a Pro Host attaches when finalizing
     * acceptance of a booking request, to `booking_agreements/{bookingId}/agreement.{ext}`
     * — see BookingRequest.agreementUrl's doc comment: this is the record that host
     * and specialist reached a real agreement (payment itself happens outside the
     * app entirely), kept on file with no admin review. Readable by either party to
     * the booking or an Admin, writable only by the host who owns the space — see
     * storage.rules.
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

    /** Per-subdivision (room/desk) images — deliberately a flat filename under the
     *  same listings/{spaceId}/{fileName} path uploadListingImage uses, not a nested
     *  listings/{spaceId}/subdivisions/{subId}/{imageId} path: storage.rules'
     *  {fileName} wildcard matches exactly one path segment, so a nested path would
     *  fall through to the default-deny catch-all and silently fail every upload.
     *  This flat "sub-{subdivisionId}-{imageId}" naming matches the existing rule
     *  with zero rules changes needed. */
    suspend fun uploadSubdivisionImage(
        spaceId: String,
        subdivisionId: String,
        imageId: String,
        fileUri: Uri,
        fileExtension: String,
        onProgress: (Float) -> Unit = {}
    ): String? = uploadAndGetUrl(
        ref = storage?.reference?.child("listings/$spaceId/sub-$subdivisionId-$imageId.$fileExtension"),
        fileUri = fileUri,
        onProgress = onProgress
    )

    /**
     * Uploads a new immutable version of an admin-managed legal document (Privacy
     * Policy / Terms of Use / Revocation Policy — text/html — or the re-rental
     * authorization template — application/pdf — see LegalDocumentVersion) to
     * `legal_documents/{docId}/v{version}.{ext}`. Every version is its own permanent
     * object — never overwritten or deleted, matching storage.rules' create-only
     * rule for this path — so re-uploading never destroys the previous version's
     * history. Explicitly sets contentType rather than trusting the file picker's
     * own MIME detection (which can come back generic for some share-sheet
     * sources), since storage.rules' create rule checks it exactly.
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
}
