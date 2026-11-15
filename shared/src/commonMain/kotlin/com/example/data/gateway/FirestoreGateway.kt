package com.example.data.gateway

import com.example.data.model.AdminPricingState
import com.example.data.model.AppUser
import com.example.data.model.AuditSecurityLog
import com.example.data.model.BookingRequest
import com.example.data.model.BookingRequestStatus
import com.example.data.model.FCMAlert
import com.example.data.model.HashtagUsageEntry
import com.example.data.model.LegalDocumentVersion
import com.example.data.model.SpaceArchitectureSchema
import com.example.data.model.SpaceListing

/** A live listener the caller removes when it leaves (Firestore's ListenerRegistration on Android). */
fun interface ListenerHandle { fun remove() }

/**
 * The app's Firestore access, platform-neutral. Android: `FirestoreService` (Firebase Android SDK);
 * iOS: a Swift implementation. Payloads are the models' `toFirestoreMap` / `fromFirestoreMap`, so
 * every platform writes byte-identical documents. Admin console reads stay out of this interface.
 */
interface FirestoreGateway {
    /** Tests only: no Firestore; writes succeed locally. */
    val localOnly: Boolean

    fun setPublicListingsLimit(limit: Int)
    fun listenOccupancy(spaceId: String, onUpdate: (List<BookingRequest>) -> Unit): ListenerHandle?
    fun attachLiveListeners(
        currentUid: String?,
        isAdminCaller: Boolean,
        onWorkspacesUpdated: (List<SpaceListing>) -> Unit,
        onUsersUpdated: (List<AppUser>) -> Unit,
        onBookingsUpdated: (List<BookingRequest>) -> Unit,
        onSchemaUpdated: (SpaceArchitectureSchema) -> Unit = {},
        onAuditLogsUpdated: (List<AuditSecurityLog>) -> Unit = {},
        onWorkspacesError: (Exception) -> Unit = {},
        publicListingsLimit: Int = PUBLIC_LISTINGS_PAGE,
        onPublicListingsPage: (hasMore: Boolean) -> Unit = {},
        onReviewQueueUpdated: (List<SpaceListing>) -> Unit = {},
        onNotificationsUpdated: (List<FCMAlert>) -> Unit = {}
    )
    fun clearListeners()

    suspend fun deleteWorkspace(spaceId: String): Boolean
    suspend fun saveWorkspace(space: SpaceListing): Boolean
    suspend fun updateWorkspaceListingFields(spaceId: String, fields: Map<String, Any?>): Boolean
    suspend fun incrementSpaceCounter(spaceId: String, field: String): Boolean
    suspend fun recordHashtagUsage(tags: List<String>, governorate: String): Boolean
    suspend fun markNotificationsRead(uid: String, ids: List<String>): Boolean
    suspend fun fetchListing(spaceId: String): SpaceListing?
    suspend fun getTopHashtags(limit: Long = 30): List<String>
    suspend fun getHashtagAnalytics(limit: Long = 50): List<HashtagUsageEntry>
    suspend fun saveUserProfile(user: AppUser): Boolean
    suspend fun updateUserProfileFields(uid: String, fields: Map<String, Any?>): Boolean
    suspend fun setSavedSpace(uid: String, spaceId: String, saved: Boolean): Boolean
    suspend fun saveFcmToken(uid: String, token: String): Boolean
    suspend fun deleteUserProfile(userId: String): Boolean
    suspend fun getUserProfile(userId: String): Map<String, Any>?
    suspend fun getPricingState(): AdminPricingState?
    suspend fun saveBookingRequest(booking: BookingRequest): Boolean
    suspend fun deleteBookingRequest(bookingId: String): Boolean
    suspend fun updateBookingStatus(
        requestId: String,
        status: BookingRequestStatus,
        rejectionReason: String? = null,
        extraFields: Map<String, Any?> = emptyMap()
    ): Boolean
    suspend fun updateBookingFields(requestId: String, fields: Map<String, Any?>): Boolean
    suspend fun saveSchema(schema: SpaceArchitectureSchema): Boolean
    suspend fun getLatestLegalDocumentVersion(docId: String): LegalDocumentVersion?
    suspend fun publishLegalDocumentVersion(docId: String, version: LegalDocumentVersion): Boolean

    companion object {
        /** Explore loads active listings a page at a time (see setPublicListingsLimit). */
        const val PUBLIC_LISTINGS_PAGE = 100
    }
}
