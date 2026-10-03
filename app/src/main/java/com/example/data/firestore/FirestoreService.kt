package com.example.data.firestore

import android.util.Log
import com.example.data.model.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * FirestoreService
 *
 * The single Firestore access layer for ProHost: collection/document CRUD, real-time
 * listeners, and initial-data seeding, all against the canonical collection names in
 * [FirestoreSchema]. This absorbs what used to be split across this class and the
 * now-removed `FirestoreDataConnectBridge` (which duplicated most of this against the
 * same collections via a parallel callback-style API).
 */
class FirestoreService(
    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.w("FirestoreService", "FirebaseFirestore instance unavailable: ${e.message}")
        null
    },
    // Tests only: no Firestore at all, and writes report success so repository flows run
    // against in-memory state instead of production (see localOnly()).
    val localOnly: Boolean = false
) {
    companion object {
        private const val TAG = "FirestoreService"

        /** Hermetic instance for unit tests: never touches Firebase; writes succeed locally. */
        fun localOnly(): FirestoreService = FirestoreService(firestore = null, localOnly = true)

        @Volatile
        private var INSTANCE: FirestoreService? = null

        fun getInstance(): FirestoreService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirestoreService().also { INSTANCE = it }
            }
        }
    }

    private val activeListeners = mutableListOf<ListenerRegistration>()

    /**
     * Attaches real-time snapshot listeners for the collections that need live cross-device
     * sync (workspaces, users, bookings, schema, plans, audit logs). Returns nothing; call
     * [clearListeners] to detach everything this has registered.
     *
     * INVARIANT this whole function depends on for its ADMIN branches: firestore.rules'
     * `isAdmin()`/`role()` must stay pure custom-claim checks with zero `resource.data`/
     * `get()` dependency (see the matching comment there). The moment that stops being true —
     * e.g. a future change makes an admin-revocation take effect instantly the way
     * `isSuspended()`/`liveRole()` already do there — every unfiltered admin listener below
     * (workspace_listings, user_profiles, booking_requests,
     * audit_security_logs) will start failing PERMISSION_DENIED, and must be re-architected
     * into role-scoped queries in that same change, not discovered after the fact.
     *
     * [currentUid]/[isAdminCaller] scope the three collections whose firestore.rules read
     * rule is content-conditional (workspace_listings, user_profiles, booking_requests) — an unconstrained `.collection().addSnapshotListener()` with no
     * `where()` filter can never satisfy those rules for a non-admin caller: Firestore only
     * allows a LIST query when the query's own filters provably guarantee every possible
     * result satisfies the rule, and "no filter at all" proves nothing except for the
     * content-independent `isAdmin()` branch. The old unconstrained listeners here simply
     * failed outright (PERMISSION_DENIED, silently logged and dropped) for every
     * non-admin — this is the fix for that. ADMIN keeps the exact same unconstrained
     * listeners as before (isAdmin() is trivially provable, independent of any document's
     * content). A non-admin gets real, rule-satisfying queries instead: the public
     * Discovery set plus their own docs for workspace_listings/booking_requests (merged
     * client-side, since a single query can't match two different OR-branches), their own
     * single document for user_profiles.
     * [currentUid] null (signed out) attaches none of these — nothing to show.
     *
     * The old `if (list.isNotEmpty()) callback(list)` gating on every one of these is also
     * gone: a genuinely empty result (no listings match, no bookings exist) now reaches the
     * caller like any other real snapshot, instead of being silently dropped.
     */
    fun attachLiveListeners(
        currentUid: String?,
        isAdminCaller: Boolean,
        onWorkspacesUpdated: (List<SpaceListing>) -> Unit,
        onUsersUpdated: (List<AppUser>) -> Unit,
        onBookingsUpdated: (List<RentalBookingRequest>) -> Unit,
        onSchemaUpdated: (SpaceArchitectureSchema) -> Unit = {},
        onAuditLogsUpdated: (List<AuditSecurityLog>) -> Unit = {},
        onPackagePlansUpdated: (PackagePlanCatalog) -> Unit = {},
        onWorkspacesError: (Exception) -> Unit = {}
    ) {
        val db = firestore ?: return

        try {
            // --- workspace_listings ---
            if (isAdminCaller) {
                val spaceListener = db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
                    .limit(500)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Workspaces sync note: ${error.message}")
                            onWorkspacesError(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val spaces = snapshot.documents.mapNotNull { doc ->
                                // Skip ownerless ghost docs (see favoritesSync.ts history).
                                doc.data?.takeIf { it["ownerId"] != null }
                                    ?.let { data -> SpaceListing.fromFirestoreMap(doc.id, data) }
                            }
                            onWorkspacesUpdated(spaces)
                        }
                    }
                activeListeners.add(spaceListener)
            } else if (currentUid != null) {
                val publicById = mutableMapOf<String, SpaceListing>()
                val ownById = mutableMapOf<String, SpaceListing>()
                // ownById is published last (see below) so a host's own copy always wins
                // over the public one for the same id — the only case they'd ever
                // disagree is a status/field the owner just changed, where the owner's
                // own read is the freshest.
                fun publishSpaces() {
                    val merged = LinkedHashMap<String, SpaceListing>()
                    publicById.values.forEach { merged[it.id] = it }
                    ownById.values.forEach { merged[it.id] = it }
                    onWorkspacesUpdated(merged.values.toList())
                }
                val publicListener = db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
                    .whereEqualTo("status", "ACTIVE")
                    .whereEqualTo("isOwnerSuspended", false)
                    .whereEqualTo("isOwnerPackageLapsed", false)
                    .limit(200)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Public workspaces sync note: ${error.message}")
                            onWorkspacesError(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            publicById.clear()
                            snapshot.documents.forEach { doc ->
                                doc.data?.let { SpaceListing.fromFirestoreMap(doc.id, it) }?.let { publicById[it.id] = it }
                            }
                            publishSpaces()
                        }
                    }
                activeListeners.add(publicListener)
                val ownListener = db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
                    .whereEqualTo("ownerId", currentUid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Own workspaces sync note: ${error.message}")
                            onWorkspacesError(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            ownById.clear()
                            snapshot.documents.forEach { doc ->
                                doc.data?.let { SpaceListing.fromFirestoreMap(doc.id, it) }?.let { ownById[it.id] = it }
                            }
                            publishSpaces()
                        }
                    }
                activeListeners.add(ownListener)
            }

            // --- user_profiles ---
            if (isAdminCaller) {
                val userListener = db.collection(FirestoreSchema.Collections.USER_PROFILES)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Users sync note: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val users = snapshot.documents.mapNotNull { doc ->
                                doc.data?.let { data -> AppUser.fromFirestoreMap(doc.id, data) }
                            }
                            onUsersUpdated(users)
                        }
                    }
                activeListeners.add(userListener)
            } else if (currentUid != null) {
                // Non-admin read rule only ever allows the caller's own document — a
                // collection-wide listener can't be scoped any other way here, so this
                // is a single-document listener, not a query.
                val ownProfileListener = db.collection(FirestoreSchema.Collections.USER_PROFILES)
                    .document(currentUid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Own profile sync note: ${error.message}")
                            return@addSnapshotListener
                        }
                        val data = snapshot?.data
                        onUsersUpdated(if (data != null) listOf(AppUser.fromFirestoreMap(snapshot.id, data)) else emptyList())
                    }
                activeListeners.add(ownProfileListener)
            }


            // --- booking_requests --- the single collection ("booking_requests") that both
            // reads and writes must agree on. See ProHostRepository for the write side.
            if (isAdminCaller) {
                val bookingListener = db.collection(FirestoreSchema.Collections.BOOKING_REQUESTS)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Bookings sync note: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val bookings = snapshot.documents.mapNotNull { doc ->
                                doc.data?.let { data -> BookingRequest.fromFirestoreMap(doc.id, data) }
                            }
                            onBookingsUpdated(bookings)
                        }
                    }
                activeListeners.add(bookingListener)
            } else if (currentUid != null) {
                val asOwner = mutableMapOf<String, RentalBookingRequest>()
                val asPractitioner = mutableMapOf<String, RentalBookingRequest>()
                fun publishBookings() {
                    val merged = LinkedHashMap<String, RentalBookingRequest>()
                    asOwner.values.forEach { merged[it.id] = it }
                    asPractitioner.values.forEach { merged[it.id] = it }
                    onBookingsUpdated(merged.values.toList())
                }
                val ownerBookingsListener = db.collection(FirestoreSchema.Collections.BOOKING_REQUESTS)
                    .whereEqualTo("ownerId", currentUid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Owner bookings sync note: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            asOwner.clear()
                            snapshot.documents.forEach { doc ->
                                doc.data?.let { BookingRequest.fromFirestoreMap(doc.id, it) }?.let { asOwner[it.id] = it }
                            }
                            publishBookings()
                        }
                    }
                activeListeners.add(ownerBookingsListener)
                val practitionerBookingsListener = db.collection(FirestoreSchema.Collections.BOOKING_REQUESTS)
                    .whereEqualTo("practitionerId", currentUid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Practitioner bookings sync note: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            asPractitioner.clear()
                            snapshot.documents.forEach { doc ->
                                doc.data?.let { BookingRequest.fromFirestoreMap(doc.id, it) }?.let { asPractitioner[it.id] = it }
                            }
                            publishBookings()
                        }
                    }
                activeListeners.add(practitionerBookingsListener)
            }

            // Single-document taxonomy: space types/subcategories/amenities/equipment/
            // rental strategies. Public read (firestore.rules), admin-only write — no
            // document exists until an admin makes their first edit, so a missing
            // snapshot here just means the caller keeps its local default schema.
            val schemaListener = db.collection(FirestoreSchema.Collections.SCHEMA_ARCHITECTURE)
                .document("main")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Schema sync note: ${error.message}")
                        return@addSnapshotListener
                    }
                    val data = snapshot?.data
                    if (data != null) {
                        onSchemaUpdated(SpaceArchitectureSchema.fromFirestoreMap(data))
                    }
                }
            activeListeners.add(schemaListener)

            // Admin-managed, purchasable Pro Host packages (package_plans/main). Public
            // read (firestore.rules) — every client, not just admins, needs the live
            // catalog to render purchase/renewal screens and resolve a host's own
            // package name/limit. Unlike the schema listener above, a missing document
            // here is reported as a real EMPTY catalog (not silently skipped) — an
            // empty/"no packages yet" state must be distinguishable from "still waiting
            // on Firestore," since the caller must never treat a placeholder as a real,
            // purchasable package (see ProHostRepository's own doc comment on this).
            val packagePlansListener = db.collection(FirestoreSchema.Collections.PACKAGE_PLANS)
                .document(PackagePlanCatalog.DOCUMENT_ID)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Package plans sync note: ${error.message}")
                        return@addSnapshotListener
                    }
                    val data = snapshot?.data
                    if (data == null || data.isEmpty()) {
                        db.collection(FirestoreSchema.Collections.PACKAGE_PLANS)
                            .document(PackagePlanCatalog.DOCUMENT_ID)
                            .set(PackagePlanCatalog.DEFAULT_CATALOG.toFirestoreMap(), SetOptions.merge())
                            .addOnFailureListener { e -> Log.e(TAG, "Failed to seed package plans catalog: ${e.message}") }
                        onPackagePlansUpdated(PackagePlanCatalog.DEFAULT_CATALOG)
                    } else {
                        onPackagePlansUpdated(PackagePlanCatalog.fromFirestoreMap(data))
                    }
                }
            activeListeners.add(packagePlansListener)

            // Admin-only read (firestore.rules) — only ever attached for an admin caller
            // now, rather than unconditionally attempting it and eating a guaranteed
            // PERMISSION_DENIED for every non-admin sign-in. Before this listener
            // existed at all, Admin's "System Audit Logs" panel only ever showed entries
            // added locally on the SAME device via addLocalAuditLogEntry — real
            // server-written entries (other admins' actions, Cloud-Function-only events
            // like role grants or account suspensions) never reached it at all. This is
            // what makes that panel actually show everything.
            if (isAdminCaller) {
                val auditLogListener = db.collection(FirestoreSchema.Collections.AUDIT_SECURITY_LOGS)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(500)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Audit logs sync note: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val logs = snapshot.documents.mapNotNull { doc ->
                                doc.data?.let { data -> AuditSecurityLog.fromFirestoreMap(doc.id, data) }
                            }
                            onAuditLogsUpdated(logs)
                        }
                    }
                activeListeners.add(auditLogListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Live listeners attachment warning: ${e.message}")
        }
    }

    fun clearListeners() {
        activeListeners.forEach { runCatching { it.remove() } }
        activeListeners.clear()
    }

    // ==========================================
    // WORKSPACE LISTINGS (schema.gql SpaceListing)
    // ==========================================

    /**
     * Persists the FULL workspace document (equipment, rental formulas, rules, schedule,
     * subdivisions included) — previously this wrote only a partial field subset, silently
     * dropping nested data on every save.
     */
    suspend fun deleteWorkspace(spaceId: String): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS).document(spaceId).delete().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting workspace: ${e.message}", e)
            false
        }
    }

    suspend fun saveWorkspace(space: SpaceListing): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
                .document(space.id)
                .set(space.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving workspace: ${e.message}", e)
            false
        }
    }

    /**
     * A narrow merge write of only [fields] on a workspace_listings document — used
     * for the host's own lifecycle actions (Pause/Resume, publishing a Draft) that
     * change just `status`, without re-sending (and risking overwriting) the rest
     * of the listing the way saveWorkspace()'s full-document write would.
     */
    suspend fun updateWorkspaceListingFields(spaceId: String, fields: Map<String, Any?>): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
                .document(spaceId)
                .set(fields + ("updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating workspace listing fields: ${e.message}", e)
            false
        }
    }

    /**
     * Atomically increments a single numeric field on a workspace_listings document
     * (used for the real "Views"/"Inquiries" engagement counters). Deliberately uses
     * Firestore's server-side FieldValue.increment rather than the read-modify-write
     * "copy local state, saveWorkspace() the whole doc" pattern used elsewhere in this
     * file — that pattern has a lost-update race under concurrent writes from
     * different users, which is exactly this scenario (many different Specialists
     * viewing/inquiring on the same listing around the same time).
     */
    suspend fun incrementSpaceCounter(spaceId: String, field: String): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
                .document(spaceId)
                .update(field, FieldValue.increment(1))
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing $field for workspace $spaceId: ${e.message}", e)
            false
        }
    }

    /**
     * One doc per hashtag (id = lowercased tag), created if absent and incremented if
     * present — SetOptions.merge() lets "count" increment atomically on either path
     * without a separate existence check. Fire-and-forget from the caller's
     * perspective (a failure here shouldn't block a listing publish), so this simply
     * returns whether it succeeded rather than throwing.
     */
    suspend fun recordHashtagUsage(tags: List<String>, governorate: String): Boolean {
        val db = firestore ?: return localOnly
        return try {
            tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }.forEach { tag ->
                db.collection(FirestoreSchema.Collections.HASHTAG_USAGE)
                    .document(tag)
                    .set(
                        mapOf(
                            "tag" to tag,
                            "count" to FieldValue.increment(1),
                            "lastUsedAtMillis" to System.currentTimeMillis(),
                            "governorate" to governorate
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error recording hashtag usage: ${e.message}", e)
            false
        }
    }

    /** Top-used hashtags overall, for CreateListingDialog's autosuggest — a fixed
     *  top-N snapshot fetched once when the dialog opens, filtered client-side by
     *  prefix as the host types, not a per-keystroke query. */
    suspend fun getTopHashtags(limit: Long = 30): List<String> {
        val db = firestore ?: return emptyList()
        return try {
            db.collection(FirestoreSchema.Collections.HASHTAG_USAGE)
                .orderBy("count", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
                .documents
                .mapNotNull { it.getString("tag") }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching top hashtags: ${e.message}", e)
            emptyList()
        }
    }

    /** Full analytics rows (tag, count, most-recent governorate/timestamp) for the
     *  Admin Console's hashtag usage view — spec 1.4's "display analytics of
     *  hashtags used" requirement. governorate here is whichever listing most
     *  recently used the tag, not a full per-governorate breakdown — a reasonable
     *  first cut given this is a single-country deployment today. */
    suspend fun getHashtagAnalytics(limit: Long = 50): List<HashtagUsageEntry> {
        val db = firestore ?: return emptyList()
        return try {
            db.collection(FirestoreSchema.Collections.HASHTAG_USAGE)
                .orderBy("count", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val tag = doc.getString("tag") ?: return@mapNotNull null
                    HashtagUsageEntry(
                        tag = tag,
                        count = (doc.getLong("count") ?: 0L).toInt(),
                        governorate = doc.getString("governorate") ?: "",
                        lastUsedAtMillis = doc.getLong("lastUsedAtMillis") ?: 0L
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching hashtag analytics: ${e.message}", e)
            emptyList()
        }
    }

    fun observeWorkspaces(): Flow<List<Map<String, Any>>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration: ListenerRegistration = db.collection(FirestoreSchema.Collections.WORKSPACE_LISTINGS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing workspaces: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.data }
                    trySend(list)
                }
            }

        awaitClose { registration.remove() }
    }

    // ==========================================
    // USER PROFILES (schema.gql AppUser)
    // ==========================================

    /**
     * Targeted merge write of only the given [fields] on [uid]'s profile — never the
     * full [AppUser] object. Use this for any client-initiated profile edit (self or
     * Admin-on-behalf-of-another-user): role/isVerified/createdAtMillis/
     * lastSignInAtMillis/isSuspended/ownerPackageTier/ownerPackageExpiryMillis are
     * exclusively server-maintained (assignInitialRole/grantAdminRole/
     * setAccountSuspended/playBillingRtdn/grantPackageToUser — see firestore.rules' user_profiles
     * update rule) and must never appear in [fields]. Echoing a full AppUser's
     * toFirestoreMap() back unfiltered risks writing a locally-cached, possibly-stale
     * value for one of those fields that no longer matches the real server-stored one —
     * Firestore then rejects the *entire* write as an attempted protected-field
     * change, even though the caller only meant to edit their name.
     */
    suspend fun saveUserProfile(user: AppUser): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.USER_PROFILES)
                .document(user.id)
                .set(user.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile: ${e.message}", e)
            false
        }
    }

    suspend fun updateUserProfileFields(uid: String, fields: Map<String, Any?>): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.USER_PROFILES)
                .document(uid)
                .set(fields + ("updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user profile fields: ${e.message}", e)
            false
        }
    }

    /**
     * Persists this device's current FCM registration token onto the signed-in user's
     * own profile doc — the only way a server-side Cloud Function can ever reach this
     * device with a real push (see functions/src/notifications/ ts files). A merge write, so
     * it never touches any other field; not a protected field in firestore.rules since
     * only the owning user ever writes their own token.
     */
    suspend fun saveFcmToken(uid: String, token: String): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.USER_PROFILES)
                .document(uid)
                .set(mapOf("fcmToken" to token, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving FCM token: ${e.message}", e)
            false
        }
    }

    suspend fun deleteUserProfile(userId: String): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.USER_PROFILES).document(userId).delete().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user profile: ${e.message}", e)
            false
        }
    }

    suspend fun getUserProfile(userId: String): Map<String, Any>? {
        return try {
            val db = firestore ?: return null
            val doc = db.collection(FirestoreSchema.Collections.USER_PROFILES).document(userId).get().await()
            doc.data
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user profile: ${e.message}", e)
            null
        }
    }

    // ==========================================
    // ADMIN PRICING STATE
    // ==========================================
    // Holds admin governance settings (AdminPricingState). The only
    // way to WRITE it is the updatePricing Cloud Function (ProHostRepository.
    // persistPricingState) — firestore.rules denies every direct client write to
    // system_metadata, so a save*State function here would only ever fail.

    suspend fun getPricingState(): AdminPricingState? {
        return try {
            val db = firestore ?: return null
            val doc = db.collection(AdminPricingState.COLLECTION_PATH)
                .document(AdminPricingState.DOCUMENT_ID)
                .get()
                .await()
            doc.data?.let { AdminPricingState.fromFirestoreMap(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching pricing state: ${e.message}", e)
            null
        }
    }


    // ==========================================
    // BOOKING REQUESTS (schema.gql BookingRequest)
    // ==========================================

    suspend fun saveBookingRequest(booking: BookingRequest): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.BOOKING_REQUESTS)
                .document(booking.id)
                .set(booking.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving booking request: ${e.message}", e)
            false
        }
    }

    suspend fun deleteBookingRequest(bookingId: String): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.BOOKING_REQUESTS).document(bookingId).delete().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting booking request: ${e.message}", e)
            false
        }
    }

    suspend fun updateBookingStatus(
        requestId: String,
        status: BookingRequestStatus,
        rejectionReason: String? = null,
        extraFields: Map<String, Any?> = emptyMap()
    ): Boolean {
        return try {
            val db = firestore ?: return localOnly
            val updates = mutableMapOf<String, Any?>(
                "status" to status.name,
                "reviewedAt" to System.currentTimeMillis()
            )
            if (rejectionReason != null) {
                updates["rejectionReason"] = rejectionReason
            }
            updates.putAll(extraFields)

            db.collection(FirestoreSchema.Collections.BOOKING_REQUESTS)
                .document(requestId)
                .set(updates, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating booking status: ${e.message}", e)
            false
        }
    }

    /**
     * A narrow merge-write for fields that aren't part of the status lifecycle
     * (updateBookingStatus above always stamps status/reviewedAt, which isn't
     * appropriate for something like a payment acknowledgment — see
     * BookingRequest.paymentAcknowledgedByHost/paymentAcknowledgedBySpecialist).
     */
    suspend fun updateBookingFields(requestId: String, fields: Map<String, Any?>): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.BOOKING_REQUESTS)
                .document(requestId)
                .set(fields, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating booking fields: ${e.message}", e)
            false
        }
    }

    // ==========================================
    // SPACE ARCHITECTURE SCHEMA (taxonomy)
    // ==========================================

    suspend fun saveSchema(schema: SpaceArchitectureSchema): Boolean {
        val db = firestore ?: return localOnly
        db.collection(FirestoreSchema.Collections.SCHEMA_ARCHITECTURE)
            .document("main")
            .set(schema.toFirestoreMap(), SetOptions.merge())
            .await()
        return true
    }

    // ==========================================
    // ADMIN-MANAGED PACKAGE PLANS
    // ==========================================

    suspend fun savePackagePlans(catalog: PackagePlanCatalog): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.PACKAGE_PLANS)
                .document(PackagePlanCatalog.DOCUMENT_ID)
                .set(catalog.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving package plans: ${e.message}", e)
            false
        }
    }

    /**
     * A merge-write of the full `packages` map (savePackagePlans above) can only
     * ADD/overwrite keys — Firestore's merge deep-merges nested map fields, so a
     * key simply absent from the write payload is never removed server-side. Real
     * deletion needs an explicit FieldValue.delete() at the specific nested path.
     * Requires the doc to already exist (update(), not set-with-merge) — true for
     * any package that ever went through addPackagePlan/updatePackagePlan/
     * togglePackagePlan; a still-only-local seeded default has nothing to delete
     * server-side yet, and this simply fails harmlessly in that edge case.
     */
    suspend fun deletePackagePlan(planId: String): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.PACKAGE_PLANS)
                .document(PackagePlanCatalog.DOCUMENT_ID)
                .update("packages.$planId", com.google.firebase.firestore.FieldValue.delete())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting package plan: ${e.message}", e)
            false
        }
    }

    /** One-shot read of the currently-published version of legal document [docId], or
     * null if nothing has ever been uploaded for it (or the read fails). */
    suspend fun getLatestLegalDocumentVersion(docId: String): LegalDocumentVersion? {
        return try {
            val db = firestore ?: return null
            val snap = db.collection(LegalDocumentVersion.COLLECTION_PATH).document(docId).get().await()
            if (!snap.exists()) return null
            snap.data?.let { LegalDocumentVersion.fromFirestoreMap(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading legal document version for $docId: ${e.message}", e)
            null
        }
    }

    /**
     * Publishes [version] as legal document [docId]'s new current version — writes the
     * parent "current pointer" doc (legal_documents/{docId}, merge) and a permanent
     * history entry (legal_documents/{docId}/versions/{version}, create-only — see
     * storage.rules' matching immutability for the underlying HTML file). Both writes
     * use the same already-resolved [version] number (the caller reads the current
     * version once, increments, then calls this — see ProHostRepository.
     * uploadLegalDocumentVersion) rather than a Firestore-side atomic increment, since
     * the version number also has to name the Storage object and the subcollection
     * doc id consistently across both writes.
     */
    suspend fun publishLegalDocumentVersion(docId: String, version: LegalDocumentVersion): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(LegalDocumentVersion.COLLECTION_PATH).document(docId)
                .set(version.toFirestoreMap(), SetOptions.merge())
                .await()
            db.collection(LegalDocumentVersion.COLLECTION_PATH).document(docId)
                .collection("versions").document(version.version.toString())
                .set(version.toFirestoreMap())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing legal document version for $docId: ${e.message}", e)
            false
        }
    }

    // ==========================================
    // AUDIT LOGS
    // ==========================================

    suspend fun recordAuditLog(log: AuditSecurityLog): Boolean {
        return try {
            val db = firestore ?: return localOnly
            db.collection(FirestoreSchema.Collections.AUDIT_SECURITY_LOGS)
                .document(log.id)
                .set(log.toFirestoreMap(), SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error recording audit log: ${e.message}", e)
            false
        }
    }

}
