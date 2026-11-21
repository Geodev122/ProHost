package com.example.data.gateway

import com.example.data.firestore.FirestoreSchema
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
import com.example.util.nowMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** One document as the platform read it: Firestore Timestamps as epoch millis, numbers as Long/Double, booleans as Boolean. */
data class DocSnapshot(val id: String, val data: Map<String, Any?>)

/** One `where` clause: [op] is "==" or ">". */
data class QueryFilter(val field: String, val op: String, val value: Any?)

/** Server-side field operations inside written maps (Android `FieldValue.increment` / `arrayUnion` / `arrayRemove`). */
sealed class FieldOp {
    data class Increment(val by: Long) : FieldOp()
    data class ArrayUnion(val values: List<Any?>) : FieldOp()
    data class ArrayRemove(val values: List<Any?>) : FieldOp()
}

/**
 * Firestore on the platform's SDK as plain callbacks, so a Swift class can implement it (iOS: `FirestoreBridgeImpl.swift`).
 * Paths are slash-separated (`user_profiles/{uid}/notifications`). `limit` 0 = no limit. Errors are a message string.
 */
interface FirestoreBridge {
    fun listenQuery(
        collectionPath: String, filters: List<QueryFilter>, orderByField: String?, descending: Boolean, limit: Int,
        onSnapshot: (List<DocSnapshot>) -> Unit, onError: (String) -> Unit
    ): ListenerHandle
    fun listenDocument(docPath: String, onSnapshot: (DocSnapshot?) -> Unit, onError: (String) -> Unit): ListenerHandle
    fun queryOnce(
        collectionPath: String, filters: List<QueryFilter>, orderByField: String?, descending: Boolean, limit: Int,
        completion: (List<DocSnapshot>?, String?) -> Unit
    )
    fun getDocument(docPath: String, completion: (DocSnapshot?, String?) -> Unit)
    fun setDocument(docPath: String, data: Map<String, Any?>, merge: Boolean, completion: (String?) -> Unit)
    fun updateDocument(docPath: String, data: Map<String, Any?>, completion: (String?) -> Unit)
    fun deleteDocument(docPath: String, completion: (String?) -> Unit)
    /** One batch that applies [data] as an update to every document in [docPaths]. */
    fun batchUpdate(docPaths: List<String>, data: Map<String, Any?>, completion: (String?) -> Unit)
}

class FirestoreBridgeException(message: String) : Exception(message)

/**
 * [FirestoreGateway] over a [FirestoreBridge], with exactly the queries, merges and written fields of the Android
 * `FirestoreService` (`BridgedFirestoreGatewayTest` pins them). Used by iOS.
 */
class BridgedFirestoreGateway(private val bridge: FirestoreBridge) : FirestoreGateway {
    override val localOnly: Boolean = false

    private val c = FirestoreSchema.Collections
    private val notifications = "notifications"
    private val activeListeners = mutableListOf<ListenerHandle>()
    private var publicListingsRegistration: ListenerHandle? = null
    private var reattachPublicListings: ((Int) -> Unit)? = null

    // ---- suspend helpers over the callbacks ----
    private suspend fun write(op: ((String?) -> Unit) -> Unit) {
        val error = suspendCancellableCoroutine<String?> { cont -> op { cont.resume(it) } }
        if (error != null) throw FirestoreBridgeException(error)
    }

    private suspend fun get(path: String): DocSnapshot? {
        val (doc, error) = suspendCancellableCoroutine<Pair<DocSnapshot?, String?>> { cont ->
            bridge.getDocument(path) { d, e -> cont.resume(d to e) }
        }
        if (error != null) throw FirestoreBridgeException(error)
        return doc
    }

    private suspend fun query(path: String, orderBy: String?, descending: Boolean, limit: Int): List<DocSnapshot> {
        val (docs, error) = suspendCancellableCoroutine<Pair<List<DocSnapshot>?, String?>> { cont ->
            bridge.queryOnce(path, emptyList(), orderBy, descending, limit) { d, e -> cont.resume(d to e) }
        }
        if (error != null) throw FirestoreBridgeException(error)
        return docs.orEmpty()
    }

    private suspend fun ok(block: suspend () -> Unit): Boolean = try {
        block(); true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    // ---- live listeners ----
    override fun setPublicListingsLimit(limit: Int) { reattachPublicListings?.invoke(limit) }

    override fun listenOccupancy(spaceId: String, onUpdate: (List<BookingRequest>) -> Unit): ListenerHandle =
        bridge.listenQuery("booking_occupancy", listOf(QueryFilter("spaceId", "==", spaceId)), null, false, 0,
            { docs -> onUpdate(docs.map { BookingRequest.fromFirestoreMap(it.id, it.data) }) }, {})

    override fun attachLiveListeners(
        currentUid: String?,
        isAdminCaller: Boolean,
        onWorkspacesUpdated: (List<SpaceListing>) -> Unit,
        onUsersUpdated: (List<AppUser>) -> Unit,
        onBookingsUpdated: (List<BookingRequest>) -> Unit,
        onSchemaUpdated: (SpaceArchitectureSchema) -> Unit,
        onAuditLogsUpdated: (List<AuditSecurityLog>) -> Unit,
        onWorkspacesError: (Exception) -> Unit,
        publicListingsLimit: Int,
        onPublicListingsPage: (hasMore: Boolean) -> Unit,
        onReviewQueueUpdated: (List<SpaceListing>) -> Unit,
        onNotificationsUpdated: (List<FCMAlert>) -> Unit
    ) {
        if (currentUid != null) {
            val publicById = mutableMapOf<String, SpaceListing>()
            val ownById = mutableMapOf<String, SpaceListing>()
            fun publishSpaces() {
                val merged = LinkedHashMap<String, SpaceListing>()
                publicById.values.forEach { merged[it.id] = it }
                ownById.values.forEach { merged[it.id] = it }
                onWorkspacesUpdated(merged.values.toList())
            }
            fun attachPublic(limit: Int) {
                publicListingsRegistration?.remove()
                publicListingsRegistration = bridge.listenQuery(
                    c.WORKSPACE_LISTINGS,
                    listOf(
                        QueryFilter("status", "==", "ACTIVE"),
                        QueryFilter("isOwnerSuspended", "==", false),
                        QueryFilter("isOwnerPackageLapsed", "==", false)
                    ),
                    null, false, limit,
                    { docs ->
                        publicById.clear()
                        docs.forEach { d -> SpaceListing.fromFirestoreMap(d.id, d.data).let { publicById[it.id] = it } }
                        onPublicListingsPage(docs.size >= limit)
                        publishSpaces()
                    },
                    { msg -> onWorkspacesError(FirestoreBridgeException(msg)) }
                )
            }
            attachPublic(publicListingsLimit)
            reattachPublicListings = ::attachPublic
            activeListeners += bridge.listenQuery(
                c.WORKSPACE_LISTINGS, listOf(QueryFilter("ownerId", "==", currentUid)), null, false, 0,
                { docs ->
                    ownById.clear()
                    docs.forEach { d -> SpaceListing.fromFirestoreMap(d.id, d.data).let { ownById[it.id] = it } }
                    publishSpaces()
                },
                { msg -> onWorkspacesError(FirestoreBridgeException(msg)) }
            )

            activeListeners += bridge.listenDocument("${c.USER_PROFILES}/$currentUid", { doc ->
                onUsersUpdated(if (doc != null) listOf(AppUser.fromFirestoreMap(doc.id, doc.data)) else emptyList())
            }, {})

            activeListeners += bridge.listenQuery(
                "${c.USER_PROFILES}/$currentUid/$notifications", listOf(QueryFilter("expireAt", ">", nowMillis())),
                "expireAt", true, 100,
                { docs -> onNotificationsUpdated(docs.map { FCMAlert.fromFirestoreMap(it.id, it.data) }.sortedByDescending { it.timestamp }) },
                {}
            )

            val asOwner = mutableMapOf<String, BookingRequest>()
            val asPractitioner = mutableMapOf<String, BookingRequest>()
            fun publishBookings() {
                val merged = LinkedHashMap<String, BookingRequest>()
                asOwner.values.forEach { merged[it.id] = it }
                asPractitioner.values.forEach { merged[it.id] = it }
                onBookingsUpdated(merged.values.toList())
            }
            activeListeners += bridge.listenQuery(
                c.BOOKING_REQUESTS, listOf(QueryFilter("ownerId", "==", currentUid)), null, false, 0,
                { docs ->
                    asOwner.clear()
                    docs.forEach { d ->
                        BookingRequest.fromFirestoreMap(d.id, d.data).takeIf { !it.rejectedBySystem }?.let { asOwner[it.id] = it }
                    }
                    publishBookings()
                },
                {}
            )
            activeListeners += bridge.listenQuery(
                c.BOOKING_REQUESTS, listOf(QueryFilter("practitionerId", "==", currentUid)), null, false, 0,
                { docs ->
                    asPractitioner.clear()
                    docs.forEach { d -> BookingRequest.fromFirestoreMap(d.id, d.data).let { asPractitioner[it.id] = it } }
                    publishBookings()
                },
                {}
            )
        }

        activeListeners += bridge.listenDocument("${c.SCHEMA_ARCHITECTURE}/main", { doc ->
            if (doc != null) onSchemaUpdated(SpaceArchitectureSchema.fromFirestoreMap(doc.data))
        }, {})

        if (isAdminCaller) {
            activeListeners += bridge.listenQuery(
                c.AUDIT_SECURITY_LOGS, emptyList(), "timestamp", true, 500,
                { docs -> onAuditLogsUpdated(docs.map { AuditSecurityLog.fromFirestoreMap(it.id, it.data) }) }, {}
            )
            activeListeners += bridge.listenQuery(
                c.WORKSPACE_LISTINGS,
                listOf(QueryFilter("isVerified", "==", false), QueryFilter("verificationRequestedAt", ">", 0)),
                null, false, 50,
                { docs ->
                    onReviewQueueUpdated(docs.filter { it.data["ownerId"] != null }.map { SpaceListing.fromFirestoreMap(it.id, it.data) })
                },
                {}
            )
        }
    }

    override fun clearListeners() {
        activeListeners.forEach { runCatching { it.remove() } }
        activeListeners.clear()
        publicListingsRegistration?.let { runCatching { it.remove() } }
        publicListingsRegistration = null
        reattachPublicListings = null
    }

    // ---- workspace listings ----
    override suspend fun deleteWorkspace(spaceId: String): Boolean = ok {
        write { bridge.deleteDocument("${c.WORKSPACE_LISTINGS}/$spaceId", it) }
    }

    override suspend fun saveWorkspace(space: SpaceListing): Boolean = ok {
        write { bridge.setDocument("${c.WORKSPACE_LISTINGS}/${space.id}", space.toFirestoreMap(), true, it) }
    }

    override suspend fun updateWorkspaceListingFields(spaceId: String, fields: Map<String, Any?>): Boolean = ok {
        write { bridge.setDocument("${c.WORKSPACE_LISTINGS}/$spaceId", fields + ("updatedAt" to nowMillis()), true, it) }
    }

    override suspend fun incrementSpaceCounter(spaceId: String, field: String): Boolean = ok {
        write { bridge.updateDocument("${c.WORKSPACE_LISTINGS}/$spaceId", mapOf(field to FieldOp.Increment(1)), it) }
    }

    override suspend fun recordHashtagUsage(tags: List<String>, governorate: String): Boolean = ok {
        tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }.forEach { tag ->
            write {
                bridge.setDocument(
                    "${c.HASHTAG_USAGE}/$tag",
                    mapOf("tag" to tag, "count" to FieldOp.Increment(1), "lastUsedAtMillis" to nowMillis(), "governorate" to governorate),
                    true, it
                )
            }
        }
    }

    override suspend fun markNotificationsRead(uid: String, ids: List<String>): Boolean {
        if (ids.isEmpty()) return true
        return ok {
            val now = nowMillis()
            ids.chunked(400).forEach { chunk ->
                write { bridge.batchUpdate(chunk.map { "${c.USER_PROFILES}/$uid/$notifications/$it" }, mapOf("read" to true, "readAt" to now), it) }
            }
        }
    }

    override suspend fun fetchListing(spaceId: String): SpaceListing? {
        if (spaceId.isBlank()) return null
        return try {
            get("${c.WORKSPACE_LISTINGS}/$spaceId")?.takeIf { it.data["ownerId"] != null }?.let { SpaceListing.fromFirestoreMap(it.id, it.data) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getTopHashtags(limit: Long): List<String> = try {
        query(c.HASHTAG_USAGE, "count", true, limit.toInt()).mapNotNull { it.data["tag"] as? String }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun getHashtagAnalytics(limit: Long): List<HashtagUsageEntry> = try {
        query(c.HASHTAG_USAGE, "count", true, limit.toInt()).mapNotNull { d ->
            val tag = d.data["tag"] as? String ?: return@mapNotNull null
            HashtagUsageEntry(
                tag = tag,
                count = ((d.data["count"] as? Number)?.toLong() ?: 0L).toInt(),
                governorate = d.data["governorate"] as? String ?: "",
                lastUsedAtMillis = (d.data["lastUsedAtMillis"] as? Number)?.toLong() ?: 0L
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    // ---- user profiles ----
    override suspend fun saveUserProfile(user: AppUser): Boolean = ok {
        write { bridge.setDocument("${c.USER_PROFILES}/${user.id}", user.toFirestoreMap(), true, it) }
    }

    override suspend fun updateUserProfileFields(uid: String, fields: Map<String, Any?>): Boolean = ok {
        write { bridge.setDocument("${c.USER_PROFILES}/$uid", fields + ("updatedAt" to nowMillis()), true, it) }
    }

    override suspend fun updateUserProfileDocument(uid: String, fields: Map<String, Any?>) {
        write { bridge.updateDocument("${c.USER_PROFILES}/$uid", fields, it) }
    }

    override suspend fun listBlockedPeople(uid: String): List<Pair<String, String>> =
        query("${c.USER_PROFILES}/$uid/blocks", null, false, 0).map { it.id to ((it.data["displayName"] as? String).orEmpty()) }

    override suspend fun setSavedSpace(uid: String, spaceId: String, saved: Boolean): Boolean = ok {
        val op: FieldOp = if (saved) FieldOp.ArrayUnion(listOf(spaceId)) else FieldOp.ArrayRemove(listOf(spaceId))
        write { bridge.updateDocument("${c.USER_PROFILES}/$uid", mapOf("savedSpaceIds" to op, "updatedAt" to nowMillis()), it) }
    }

    override suspend fun saveFcmToken(uid: String, token: String): Boolean = ok {
        write { bridge.setDocument("${c.USER_PROFILES}/$uid", mapOf("fcmToken" to token, "updatedAt" to nowMillis()), true, it) }
    }

    override suspend fun deleteUserProfile(userId: String): Boolean = ok {
        write { bridge.deleteDocument("${c.USER_PROFILES}/$userId", it) }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun getUserProfile(userId: String): Map<String, Any>? = try {
        get("${c.USER_PROFILES}/$userId")?.data as Map<String, Any>?
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    override suspend fun getPricingState(): AdminPricingState? = try {
        get("${AdminPricingState.COLLECTION_PATH}/${AdminPricingState.DOCUMENT_ID}")?.let { AdminPricingState.fromFirestoreMap(it.data) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    // ---- bookings ----
    override suspend fun saveBookingRequest(booking: BookingRequest): Boolean = ok {
        write { bridge.setDocument("${c.BOOKING_REQUESTS}/${booking.id}", booking.toFirestoreMap(), true, it) }
    }

    override suspend fun deleteBookingRequest(bookingId: String): Boolean = ok {
        write { bridge.deleteDocument("${c.BOOKING_REQUESTS}/$bookingId", it) }
    }

    override suspend fun updateBookingStatus(
        requestId: String, status: BookingRequestStatus, rejectionReason: String?, extraFields: Map<String, Any?>
    ): Boolean = ok {
        val updates = mutableMapOf<String, Any?>("status" to status.name, "reviewedAt" to nowMillis())
        if (rejectionReason != null) updates["rejectionReason"] = rejectionReason
        updates.putAll(extraFields)
        write { bridge.setDocument("${c.BOOKING_REQUESTS}/$requestId", updates, true, it) }
    }

    override suspend fun updateBookingFields(requestId: String, fields: Map<String, Any?>): Boolean = ok {
        write { bridge.setDocument("${c.BOOKING_REQUESTS}/$requestId", fields, true, it) }
    }

    // ---- schema and legal documents ----
    // Like Android, a failed schema save throws instead of returning false.
    override suspend fun saveSchema(schema: SpaceArchitectureSchema): Boolean {
        write { bridge.setDocument("${c.SCHEMA_ARCHITECTURE}/main", schema.toFirestoreMap(), true, it) }
        return true
    }

    override suspend fun getLatestLegalDocumentVersion(docId: String): LegalDocumentVersion? = try {
        get("${LegalDocumentVersion.COLLECTION_PATH}/$docId")?.let { LegalDocumentVersion.fromFirestoreMap(it.data) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    override suspend fun publishLegalDocumentVersion(docId: String, version: LegalDocumentVersion): Boolean = ok {
        write { bridge.setDocument("${LegalDocumentVersion.COLLECTION_PATH}/$docId", version.toFirestoreMap(), true, it) }
        write { bridge.setDocument("${LegalDocumentVersion.COLLECTION_PATH}/$docId/versions/${version.version}", version.toFirestoreMap(), false, it) }
    }
}
