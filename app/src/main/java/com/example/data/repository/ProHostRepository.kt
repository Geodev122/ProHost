package com.example.data.repository

import android.util.Log
import com.example.data.auth.toUserMessage
import com.example.data.auth.FirebaseFunctionsClient
import com.example.data.auth.RegistrationDetails
import com.example.data.demo.DemoDataGenerator
import com.example.data.firestore.FirestoreSchema
import com.example.data.firestore.FirestoreService
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ProHostRepository(
    private val firestoreService: FirestoreService = FirestoreService.getInstance(),
    private val functionsClient: FirebaseFunctionsClient = FirebaseFunctionsClient()
) {

    companion object {
        private const val TAG = "ProHostRepository"

        // Must stay in sync with firestore.rules' user_profiles update rule's own
        // protected-fields list — every one of these is exclusively server-maintained
        // (assignInitialRole/grantAdminRole/setAccountSuspended/pinAuth/
        // emailVerification functions). Used by registerMember to filter its
        // write down to a safe subset — see that function's own doc comment.
        private val PROTECTED_UPDATE_FIELDS = setOf(
            "role", "isVerified", "createdAtMillis", "lastSignInAtMillis", "isSuspended",
            "ownerPackageId", "ownerPackageExpiryMillis", "activeListingCount",
            "tosAcceptedAtMillis", "consentVersion",
            "emailVerified", "emailVerifiedAt", "displayCode"
        )

        @Volatile
        private var instance: ProHostRepository? = null

        fun getInstance(): ProHostRepository {
            return instance ?: synchronized(this) {
                val existing = instance
                if (existing != null) {
                    existing
                } else {
                    val newInstance = ProHostRepository()
                    instance = newInstance
                    newInstance
                }
            }
        }
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** True for the hermetic test repository (no Firestore; see FirestoreService.localOnly). */
    val isLocalOnly: Boolean get() = firestoreService.localOnly

    private val _isCloudConnected = MutableStateFlow(false)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>("Synced with Lebanese Cloud Network")
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    private val _fcmAlerts = MutableStateFlow<List<FCMAlert>>(emptyList())
    val fcmAlerts: StateFlow<List<FCMAlert>> = _fcmAlerts.asStateFlow()

    fun addFCMAlert(alert: FCMAlert) {
        _fcmAlerts.value = listOf(alert) + _fcmAlerts.value
    }

    fun markAlertAsRead(alertId: String) {
        _fcmAlerts.value = _fcmAlerts.value.map {
            if (it.id == alertId) it.copy(isRead = true) else it
        }
    }

    private val _pricingState = MutableStateFlow(AdminPricingState())
    val pricingState: StateFlow<AdminPricingState> = _pricingState.asStateFlow()

    private val _spaces = MutableStateFlow<List<SpaceListing>>(emptyList())
    val spaces: StateFlow<List<SpaceListing>> = _spaces.asStateFlow()

    private val _users = MutableStateFlow<List<AppUser>>(emptyList())
    val users: StateFlow<List<AppUser>> = _users.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditSecurityLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditSecurityLog>> = _auditLogs.asStateFlow()

    // Starts signed out. This previously defaulted to a fully-populated Super Admin
    // AppUser, meaning every fresh install of the app opened directly into the Admin
    // console with zero authentication — no login screen ever shown, no credential
    // ever checked. That was, by a wide margin, the most severe bug in the app.
    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    private val _bookingRequests = MutableStateFlow<List<RentalBookingRequest>>(emptyList())
    val bookingRequests: StateFlow<List<RentalBookingRequest>> = _bookingRequests.asStateFlow()

    // Flips true the moment the first real Firestore snapshot for bookings arrives
    // (see onBookingsUpdated below) — distinct from bookingRequests simply being
    // empty, which is indistinguishable from "still loading" without this. Screens
    // reading practitionerBookings/ownerIncomingRequests (MyBookingsScreen,
    // OwnerIncomingRequestsView, OwnerRentingProgressScreen) used to render their
    // "No bookings yet" empty state instantly on open, even for an account with real
    // bookings, for however long the first snapshot took to arrive.
    private val _hasLoadedBookingsOnce = MutableStateFlow(false)
    val hasLoadedBookingsOnce: StateFlow<Boolean> = _hasLoadedBookingsOnce.asStateFlow()

    // Same reasoning as hasLoadedBookingsOnce above, for Discovery: isLoading was
    // hardcoded false in DiscoveryViewModel, so the "no listings match your
    // filters" empty state rendered instantly on open, before the first real
    // workspace_listings snapshot ever arrived — indistinguishable from an
    // account that genuinely has no matches.
    private val _hasLoadedSpacesOnce = MutableStateFlow(false)
    val hasLoadedSpacesOnce: StateFlow<Boolean> = _hasLoadedSpacesOnce.asStateFlow()

    // Set when the listings listener fails (rules denial, network, missing index);
    // cleared by the next successful snapshot. Lets Explore show an error instead
    // of spinning forever on !hasLoadedSpacesOnce.
    private val _spacesLoadError = MutableStateFlow<String?>(null)
    val spacesLoadError: StateFlow<String?> = _spacesLoadError.asStateFlow()

    private val _spaceArchitectureSchema = MutableStateFlow<SpaceArchitectureSchema>(createDefaultSchema())
    val spaceArchitectureSchema: StateFlow<SpaceArchitectureSchema> = _spaceArchitectureSchema.asStateFlow()

    init {
        // Watches _currentUser and (re)attaches the live listeners whenever the
        // signed-in user's identity or admin status changes — including the very
        // first attach, since this collector fires immediately with _currentUser's
        // initial null value ("not signed in yet," correctly attaching only the
        // public, role-independent listeners at that point). Before this,
        // startRealtimeSync() only ever ran once, in this init block, before any
        // user was ever signed in — so the four role-conditional collections
        // (workspace_listings, user_profiles, booking_requests
        // — see FirestoreService.attachLiveListeners's own doc comment) were
        // always scoped to "signed out" and never re-scoped on sign-in, sign-out,
        // account switch, or an Admin promotion/demotion mid-session.
        coroutineScope.launch {
            var lastScopeKey: Pair<String?, Boolean>? = null
            _currentUser.collect { user ->
                val scopeKey = user?.id to (user?.role == UserRole.ADMIN)
                if (scopeKey != lastScopeKey) {
                    lastScopeKey = scopeKey
                    startRealtimeSync()
                }
            }
        }
    }

    // Explore's paged live list (see FirestoreService.setPublicListingsLimit).
    private var publicListingsLimit = FirestoreService.PUBLIC_LISTINGS_PAGE
    private val _hasMoreSpaces = MutableStateFlow(false)
    /** True when Explore's loaded page is full, so more active listings may exist. */
    val hasMoreSpaces: StateFlow<Boolean> = _hasMoreSpaces.asStateFlow()
    private val _reviewQueue = MutableStateFlow<List<SpaceListing>>(emptyList())
    /** Admin only: listings waiting for verification review (small live queue). */
    val reviewQueue: StateFlow<List<SpaceListing>> = _reviewQueue.asStateFlow()

    /** Explore: grow the live listing page by one page. No-op when everything is loaded. */
    fun loadMoreSpaces() {
        if (!_hasMoreSpaces.value) return
        publicListingsLimit += FirestoreService.PUBLIC_LISTINGS_PAGE
        firestoreService.setPublicListingsLimit(publicListingsLimit)
    }

    fun startRealtimeSync() {
        try {
            // Safe to call again (e.g. on manual retry, or a role/uid rescope) —
            // detach any previous listeners first so they don't stack up and fire
            // duplicate/stale-scoped updates.
            firestoreService.clearListeners()

            val scopeUser = _currentUser.value

            // Attach the single set of real-time listeners. Bookings are read from and
            // written to the same collection (FirestoreSchema.Collections.BOOKING_REQUESTS) —
            // there used to be a second, separate "prospace_bookings" collection that writes
            // went to while this listener read from "booking_requests", so a booking from one
            // device never reached another device's listener. That split is now gone.
            firestoreService.attachLiveListeners(
                currentUid = scopeUser?.id,
                isAdminCaller = scopeUser?.role == UserRole.ADMIN,
                onWorkspacesUpdated = { updatedSpaces ->
                    _spaces.value = updatedSpaces
                    _hasLoadedSpacesOnce.value = true
                    _spacesLoadError.value = null
                    _isCloudConnected.value = true
                    _isOfflineMode.value = false
                    _syncStatusMessage.value = "Real-time Cloud Sync Active"
                },
                onUsersUpdated = { updatedUsers ->
                    _users.value = updatedUsers
                    // Keep the signed-in user's own session in sync with server-side role/
                    // tier/suspension changes in real time — an Admin action on ANOTHER
                    // device (grantAdminRole, revokeProHostRole, setAccountSuspended, a
                    // verification toggle) used to only ever reach _users, never
                    // _currentUser, so the affected user's own UI kept showing their old
                    // role/status until their next full sign-in. All of these fields are
                    // server-authoritative (Cloud-Function-only writes), so overwriting the
                    // local currentUser with the fresh synced copy is always safe.
                    val signedInId = _currentUser.value?.id
                    if (signedInId != null) {
                        updatedUsers.find { it.id == signedInId }?.let { fresh ->
                            _currentUser.value = fresh
                        }
                    }
                    _isCloudConnected.value = true
                },
                onBookingsUpdated = { updatedBookings ->
                    _bookingRequests.value = updatedBookings
                    _hasLoadedBookingsOnce.value = true
                    _isCloudConnected.value = true
                },
                onSchemaUpdated = { updatedSchema ->
                    _spaceArchitectureSchema.value = updatedSchema
                    _isCloudConnected.value = true
                },
                onAuditLogsUpdated = { updatedLogs ->
                    _auditLogs.value = updatedLogs
                },
                onWorkspacesError = { error ->
                    if (_spaces.value.isEmpty()) {
                        _spacesLoadError.value = error.toUserMessage("We couldn't load workspaces. Please try again.")
                    }
                },
                publicListingsLimit = publicListingsLimit,
                onPublicListingsPage = { _hasMoreSpaces.value = it },
                onReviewQueueUpdated = { _reviewQueue.value = it }
            )

            // Pricing: read-only from the client's side. If an admin has already
            // configured pricing on this project, use it; otherwise keep the local
            // AdminPricingState() defaults, which match the fallback defaults
            // functions/src/lib/pricing.ts uses server-side when the doc doesn't
            // exist yet — no client write needed. Writing system_metadata/pricing at all is now
            // exclusively the updatePricing Cloud Function's job (Phase 7 rules deny
            // every client write to it, admin or not).
            coroutineScope.launch {
                val remotePricing = firestoreService.getPricingState()
                if (remotePricing != null) {
                    _pricingState.value = remotePricing
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Firestore init fallback: ${e.message}")
            _isOfflineMode.value = true
            _syncStatusMessage.value = "Offline Cache Active"
        }
    }

    /** Test hook: Robolectric tests have no Firestore, so they inject listings directly. */
    @androidx.annotation.VisibleForTesting
    internal fun replaceSpacesForTest(spaces: List<SpaceListing>) {
        _spaces.value = spaces
    }

    // --- Security & Audit Logging ---
    // actorEmail is kept as a parameter only for the local optimistic display copy
    // below — Firestore rules deny every direct client write to audit_security_logs,
    // and the recordClientAuditLog Cloud Function this now calls always uses the
    // caller's own verified token email server-side, ignoring whatever's passed
    // here. That closes the exact gap the original audit flagged: actorEmail used to
    // be a plain client-supplied (spoofable) value in the persisted record.
    fun addAuditLog(
        actionType: String,
        details: String,
        severity: String = "INFO",
        actorEmail: String = _currentUser.value?.email ?: "system@prohost.app"
    ) {
        addLocalAuditLogEntry(actionType, details, severity, actorEmail)
        coroutineScope.launch { functionsClient.recordAuditLog(actionType, details, severity) }
    }

    /**
     * Updates only the local, in-memory audit log list (no server write). Used by
     * actions that already have their own dedicated Cloud Function writing the real
     * audit entry (pricing, credential review, listing verification/subscription
     * overrides) — calling [addAuditLog] there too would double-write.
     */
    private fun addLocalAuditLogEntry(
        actionType: String,
        details: String,
        severity: String = "INFO",
        actorEmail: String = _currentUser.value?.email ?: "system@prohost.app"
    ) {
        val log = AuditSecurityLog(
            id = "LOG-" + UUID.randomUUID().toString().take(6).uppercase(),
            timestamp = System.currentTimeMillis(),
            actionType = actionType,
            details = details,
            actorEmail = actorEmail,
            severity = severity
        )
        _auditLogs.value = listOf(log) + _auditLogs.value
    }

    // --- Admin Governance (system_metadata/pricing) ---
    // Firestore rules deny every client write to system_metadata, so this goes
    // through the updatePricing Cloud Function, which also writes the audit entry.
    /** Returns whether the server actually accepted the change. */
    private suspend fun persistPricingState(fields: Map<String, Any>): Boolean {
        return functionsClient.updatePricing(fields).isSuccess
    }

    /** Current published version of legal document [docId] ("privacy_policy" /
     * "terms_of_use" / "revocation_policy"), or null if never uploaded. Thin
     * delegate — the Admin Console upload card reads this to show "currently v3"
     * and to resolve the next version number before uploading; LegalDocumentDialog
     * reads it to render the live HTML (or a placeholder when null). */
    suspend fun getLatestLegalDocumentVersion(docId: String): LegalDocumentVersion? =
        firestoreService.getLatestLegalDocumentVersion(docId)

    /** Records [version] as legal document [docId]'s new current version (both the
     * fast "current" pointer and a permanent, never-overwritten history entry — see
     * FirestoreService.publishLegalDocumentVersion) and audit-logs the change. The
     * caller (AdminViewModel.uploadLegalDocument) already uploaded the HTML to
     * Storage and resolved [version]'s number before calling this. */
    suspend fun publishLegalDocumentVersion(docId: String, version: LegalDocumentVersion): Boolean {
        val success = firestoreService.publishLegalDocumentVersion(docId, version)
        if (success) {
            addAuditLog(
                actionType = "LEGAL_DOCUMENT_PUBLISHED",
                details = "Admin published $docId v${version.version}" +
                    (version.fileName?.let { " (\"$it\")" } ?: ""),
                severity = "SECURE"
            )
        }
        return success
    }

    suspend fun updateGovernanceTag(tag: String): Boolean {
        val success = persistPricingState(mapOf("governanceTag" to tag))
        if (success) {
            _pricingState.value = _pricingState.value.copy(governanceTag = tag)
            addLocalAuditLogEntry(
                actionType = "ADMIN_GOVERNANCE_TAG_UPDATED",
                details = "Admin governance control tag updated to: $tag",
                severity = "WARN"
            )
        }
        return success
    }

    // --- Space Listing Management ---
    /**
     * Every field firestore.rules protects on a listing update (or the server alone
     * maintains), taken from the stored listing so a client write can never change
     * them. isOwnerPackageLapsed/isOwnerSuspended matter most: the server flips them
     * when a plan lapses or an account is suspended, and a wizard-built listing carries
     * the defaults, so echoing those back used to get the whole save denied.
     */
    private fun SpaceListing.keepingServerOwnedFields(current: SpaceListing?): SpaceListing =
        if (current == null) this else copy(
            ownerId = current.ownerId,
            isVerified = current.isVerified,
            isActiveSubscription = current.isActiveSubscription,
            isOwnerSuspended = current.isOwnerSuspended,
            isOwnerPackageLapsed = current.isOwnerPackageLapsed,
            ownerProfilePictureUrl = current.ownerProfilePictureUrl
        )

    /**
     * This only ever mutated the in-memory _spaces list — it never wrote to
     * Firestore at all, so a freshly "published" listing lived purely in RAM and
     * was wiped the moment attachLiveListeners' workspace_listings snapshot next
     * replaced _spaces wholesale (see startRealtimeSync). That also silently broke
     * every follow-up edit: the schedule/blackout/formula saves all look the space
     * up in _spaces first and bail out when it isn't there. Now persisted first,
     * and local state only commits once the write is confirmed.
     */
    suspend fun addSpaceListing(listing: SpaceListing): Boolean {
        // Publishing a Draft reuses the same listingId (see CreateListingDialog's
        // buildListing), so this write may really be an update of an existing
        // document rather than a brand-new one — same protected-field re-copy as
        // updateSpaceListing, otherwise a freshly-constructed subscriptionExpiryMillis
        // (computed from System.currentTimeMillis() at build time, not preserved
        // across the wizard's steps) would diff against the stored value and get
        // the whole write denied by firestore.rules' protected-fields check.
        val current = _spaces.value.find { it.id == listing.id }
        val safeListing = listing.keepingServerOwnedFields(current)
        val success = firestoreService.saveWorkspace(safeListing)
        if (success) {
            // Upserts rather than always prepending — see the comment above on why
            // this id may already be in local state from saveListingDraft;
            // prepending unconditionally would leave two entries for the same
            // document instead of one updated one.
            _spaces.value = if (current != null) {
                _spaces.value.map { if (it.id == safeListing.id) safeListing else it }
            } else {
                listOf(safeListing) + _spaces.value
            }
            addAuditLog(
                actionType = "LISTING_CREATED",
                details = "New space created: ${safeListing.title} (${safeListing.district}) by ${safeListing.ownerName}",
                severity = "INFO"
            )
            // Recorded on publish, not on every draft save — a draft mid-typing
            // shouldn't inflate usage counts for tags the host might still change.
            if (safeListing.complementarySpecialties.isNotEmpty()) {
                coroutineScope.launch {
                    firestoreService.recordHashtagUsage(safeListing.complementarySpecialties, safeListing.governorate.name)
                }
            }
        }
        return success
    }

    /** Fetched once per call site (CreateListingDialog opening), not cached
     *  reactively — hashtag popularity changes slowly enough that a snapshot from
     *  whenever the wizard was opened is accurate enough for autosuggest. */
    suspend fun fetchTopHashtags(): List<String> = firestoreService.getTopHashtags()

    /** Full analytics rows for the Admin Console's hashtag usage view. */
    suspend fun fetchHashtagAnalytics(): List<HashtagUsageEntry> = firestoreService.getHashtagAnalytics()

    /**
     * Saves (or re-saves) a listing as a Draft — never gated by the host's active-
     * listing quota (see ProHostViewModel.createNewSpaceListing's quota check, which
     * this deliberately bypasses) since a Draft doesn't consume a slot until it's
     * actually published. Upserts on listingId so repeatedly saving the same draft
     * updates one document/local entry instead of creating duplicates. Preserves the
     * same protected fields as addSpaceListing/updateSpaceListing — see their doc
     * comments — since a re-save is really an update of the existing draft document.
     */
    suspend fun saveListingDraft(listing: SpaceListing): Boolean {
        val current = _spaces.value.find { it.id == listing.id }
        val draft = listing.copy(status = ListingStatus.DRAFT).keepingServerOwnedFields(current)
        val success = firestoreService.saveWorkspace(draft)
        if (success) {
            _spaces.value = if (current != null) {
                _spaces.value.map { if (it.id == draft.id) draft else it }
            } else {
                listOf(draft) + _spaces.value
            }
            addAuditLog(
                actionType = "LISTING_DRAFT_SAVED",
                details = "Draft saved: ${draft.title} (${draft.district}) by ${draft.ownerName}",
                severity = "INFO"
            )
        }
        return success
    }

    /**
     * Generic listing edit (title, address, base rate, etc.) from the Admin
     * Console's Edit Listing dialog. Never actually reached Firestore before —
     * local-only mutation, a real gap independent of Phase 7's rules. Now
     * persists via firestoreService.saveWorkspace, but — same pattern as
     * updateUser() — always preserves the current stored isVerified/
     * isActiveSubscription/subscriptionExpiryMillis/ownerId regardless of what's
     * passed in, since Firestore rules deny any client write that changes them.
     * Use setListingVerification / setListingSubscriptionActive for those.
     */
    /** Returns whether the write actually succeeded, so the caller can show a real result. */
    suspend fun updateSpaceListing(updated: SpaceListing): Boolean {
        val current = _spaces.value.find { it.id == updated.id }
        val safeUpdate = updated.keepingServerOwnedFields(current)
        val success = firestoreService.saveWorkspace(safeUpdate)
        if (success) {
            _spaces.value = _spaces.value.map { if (it.id == safeUpdate.id) safeUpdate else it }
            addAuditLog(
                actionType = "LISTING_UPDATED",
                details = "Admin updated workspace listing #${safeUpdate.id} (${safeUpdate.title})",
                severity = "INFO"
            )
        }
        return success
    }

    /**
     * This used to only mutate local state — the confirmation dialog claimed a
     * "permanent" removal, but the Firestore document was never touched, so the
     * listing simply reappeared the next time the live listener fired. Firestore
     * rules already permit an admin (or the owning user) to delete this document
     * directly (firestore.rules workspace_listings: allow delete), so no Cloud
     * Function is needed for the delete itself. A separate trigger
     * (functions/src/listings/listingDeleteCleanup.ts,
     * onWorkspaceListingDeletedCleanup) reacts to the deletion server-side to
     * scrub this listing's id out of every other user's favorites and cancel
     * any still-open booking against it — a raw client write here isn't
     * authorized to touch another user's profile directly.
     */
    suspend fun deleteSpaceListing(spaceId: String): Boolean {
        val target = _spaces.value.find { it.id == spaceId }
        val success = firestoreService.deleteWorkspace(spaceId)
        if (success) {
            _spaces.value = _spaces.value.filterNot { it.id == spaceId }
            com.example.data.storage.FirebaseStorageService.getInstance().purgeListingStorage(spaceId)
            addAuditLog(
                actionType = "LISTING_DELETED",
                details = "Admin permanently deleted workspace listing #${spaceId} (${target?.title ?: "Unknown"})",
                severity = "WARN"
            )
        }
        return success
    }

    /**
     * Generic profile edit (name, phone, specialty, country/governorate/city, etc.)
     * from the Admin Console's edit-user dialog. role/isVerified are always preserved
     * from the current stored value here, regardless of what's passed in: Firestore
     * rules deny any client write that changes those fields, so silently keeping them
     * unchanged avoids a write that would otherwise be denied outright (and this
     * method's caller showing a false "updated successfully" toast). Use
     * grantAdminRole for a role change instead.
     */
    /** Returns whether the write actually succeeded, so the caller can show a real result. */
    suspend fun updateUser(updated: AppUser): Boolean {
        val current = _users.value.find { it.id == updated.id }
        val safeUpdate = if (current != null) {
            updated.copy(
                role = current.role,
                isVerified = current.isVerified,
                isSuspended = current.isSuspended,
                ownerPackageId = current.ownerPackageId,
                ownerPackageExpiryMillis = current.ownerPackageExpiryMillis
            )
        } else {
            updated
        }
        // A targeted write of only these fields — role/isVerified/isSuspended/
        // ownerPackageId/etc. are Cloud-Function/Admin-SDK-only per firestore.rules'
        // user_profiles protected-fields list, and are never sent here at all (not
        // even echoed back unchanged) — echoing back the *locally cached* value used
        // to be this function's protection, but that cache can itself be stale
        // relative to the real server-stored value, which gets the entire write
        // rejected instead of just silently keeping the stale field as-is.
        val success = firestoreService.updateUserProfileFields(
            safeUpdate.id,
            mapOf(
                "fullName" to safeUpdate.fullName,
                "email" to safeUpdate.email,
                "specialty" to safeUpdate.specialty,
                "phone" to safeUpdate.phone,
                "country" to safeUpdate.country,
                "governorate" to safeUpdate.governorate,
                "city" to safeUpdate.city,
                "profilePictureUrl" to safeUpdate.profilePictureUrl
            )
        )
        if (success) {
            _users.value = _users.value.map { if (it.id == safeUpdate.id) safeUpdate else it }
            if (_currentUser.value?.id == safeUpdate.id) {
                _currentUser.value = safeUpdate
            }
            addAuditLog(
                actionType = "USER_UPDATED",
                details = "Admin updated user profile for ${safeUpdate.fullName} (${safeUpdate.email})",
                severity = "INFO"
            )
        }
        return success
    }

    /**
     * This used to only mutate local state — the confirmation dialog claimed a
     * "permanent" removal, but the Firestore document was never touched, so the
     * profile simply reappeared the next time the live listener fired. Firestore
     * rules already permit an admin to delete this document directly
     * (firestore.rules user_profiles: allow delete: if isAdmin()). Note this does
     * NOT revoke the user's Firebase Auth account or custom claim — only a Cloud
     * Function with the Admin SDK could do that; this removes their platform
     * profile record, which is what the confirmation dialog actually describes.
     */
    suspend fun deleteUser(userId: String): Boolean {
        val target = _users.value.find { it.id == userId }
        val success = firestoreService.deleteUserProfile(userId)
        if (success) {
            _users.value = _users.value.filterNot { it.id == userId }
            addAuditLog(
                actionType = "USER_DELETED",
                details = "Admin removed user profile #${userId} (${target?.fullName ?: "Unknown"})",
                severity = "WARN"
            )
        }
        return success
    }

    // --- Dynamic Space Architecture Schema Management ---
    // All four of these used to be 100% local — they never called Firestore at all,
    // despite AdminConsoleScreen's copy claiming edits went to a "database registry"/
    // "cloud" and the toasts implying a real save. Now they persist via
    // firestoreService.saveSchema (schema_architecture/main, admin-write-gated —
    // see firestore.rules), and only update local state once that write is confirmed.

    suspend fun addSchemaItem(item: SchemaItem): Boolean {
        val current = _spaceArchitectureSchema.value
        val updated = when (item.category) {
            SchemaCategory.SPACE_TYPE -> current.copy(spaceTypes = current.spaceTypes + item)
            SchemaCategory.DIVISION_TYPE -> current.copy(divisionTypes = current.divisionTypes + item)
            SchemaCategory.FACILITY -> current.copy(facilities = current.facilities + item)
            SchemaCategory.AMENITY -> current.copy(amenities = current.amenities + item)
            SchemaCategory.RENTAL_STRATEGY -> current.copy(rentalStrategies = current.rentalStrategies + item)
            else -> current
        }
        val success = firestoreService.saveSchema(updated)
        if (success) {
            _spaceArchitectureSchema.value = updated
            addAuditLog(
                actionType = "SCHEMA_ITEM_ADDED",
                details = "Admin added schema node '${item.name}' under category '${item.category}'",
                severity = "SECURE"
            )
        }
        return success
    }

    /** Edits maxSubdivisions on an EXISTING SchemaItem — previously only settable
     * once, at creation, via addSchemaItem. Only meaningful for category == "SPACE_TYPE"
     * (a no-op for any other category, same as the field's own doc comment on
     * SchemaItem). Prices are never stored in the app: Google Play is the only billing authority. */
    suspend fun updateSchemaItemMaxSubdivisions(itemId: String, category: String, maxSubdivisions: Int?): Boolean {
        val current = _spaceArchitectureSchema.value
        fun updateList(items: List<SchemaItem>) =
            items.map { if (it.id == itemId) it.copy(maxSubdivisions = maxSubdivisions) else it }
        val updated = when (category) {
            "SPACE_TYPE" -> current.copy(spaceTypes = updateList(current.spaceTypes))
            else -> return false
        }
        val success = firestoreService.saveSchema(updated)
        if (success) {
            _spaceArchitectureSchema.value = updated
            addAuditLog(
                actionType = "SCHEMA_ITEM_PRICING_UPDATED",
                details = "Admin updated max-subdivisions for schema item #$itemId",
                severity = "SECURE"
            )
        }
        return success
    }

    suspend fun toggleSchemaItem(itemId: String): Boolean {
        val current = _spaceArchitectureSchema.value
        fun List<SchemaItem>.tog() = map { if (it.id == itemId) it.copy(isEnabled = !it.isEnabled) else it }
        val updated = current.copy(
            spaceTypes = current.spaceTypes.tog(),
            divisionTypes = current.divisionTypes.tog(),
            facilities = current.facilities.tog(),
            amenities = current.amenities.tog(),
            rentalStrategies = current.rentalStrategies.tog()
        )
        val success = firestoreService.saveSchema(updated)
        if (success) {
            _spaceArchitectureSchema.value = updated
            addAuditLog(
                actionType = "SCHEMA_ITEM_TOGGLED",
                details = "Admin toggled schema item #$itemId active status",
                severity = "INFO"
            )
        }
        return success
    }

    suspend fun deleteSchemaItem(itemId: String): Boolean {
        val current = _spaceArchitectureSchema.value
        val updated = current.copy(
            spaceTypes = current.spaceTypes.filterNot { it.id == itemId },
            divisionTypes = current.divisionTypes.filterNot { it.id == itemId },
            facilities = current.facilities.filterNot { it.id == itemId },
            amenities = current.amenities.filterNot { it.id == itemId },
            rentalStrategies = current.rentalStrategies.filterNot { it.id == itemId }
        )
        val success = firestoreService.saveSchema(updated)
        if (success) {
            _spaceArchitectureSchema.value = updated
            addAuditLog(
                actionType = "SCHEMA_ITEM_DELETED",
                details = "Admin removed custom schema item #$itemId",
                severity = "WARN"
            )
        }
        return success
    }

    /**
     * Appends built-in default schema items that the live schema lacks (matched by id and
     * by name), leaving every existing item untouched. Defaults added after the live
     * schema_architecture/main doc was created never reach it otherwise, because that doc
     * replaces the built-in defaults wholesale. Returns the number added, or null on a
     * failed write.
     */
    suspend fun addMissingDefaultSchemaItems(): Int? {
        val current = _spaceArchitectureSchema.value
        val defaults = createDefaultSchema()
        fun merge(existing: List<SchemaItem>, builtIn: List<SchemaItem>): List<SchemaItem> {
            val ids = existing.map { it.id }.toSet()
            val names = existing.map { it.name.trim().lowercase() }.toSet()
            return existing + builtIn.filter { it.id !in ids && it.name.trim().lowercase() !in names }
        }
        val updated = current.copy(
            spaceTypes = merge(current.spaceTypes, defaults.spaceTypes),
            divisionTypes = merge(current.divisionTypes, defaults.divisionTypes),
            facilities = merge(current.facilities, defaults.facilities),
            amenities = merge(current.amenities, defaults.amenities),
            rentalStrategies = merge(current.rentalStrategies, defaults.rentalStrategies),
            attendeePackages = current.attendeePackages + defaults.attendeePackages.filter { d ->
                current.attendeePackages.none { it.id == d.id || it.name.trim().equals(d.name.trim(), ignoreCase = true) }
            }
        )
        fun count(s: SpaceArchitectureSchema) =
            s.spaceTypes.size + s.divisionTypes.size + s.facilities.size + s.amenities.size + s.rentalStrategies.size +
                s.attendeePackages.size
        val added = count(updated) - count(current)
        if (added == 0) return 0
        if (!firestoreService.saveSchema(updated)) return null
        _spaceArchitectureSchema.value = updated
        addAuditLog(
            actionType = "SCHEMA_DEFAULTS_MERGED",
            details = "Admin added $added missing built-in schema item(s); existing items unchanged",
            severity = "SECURE"
        )
        return added
    }

    suspend fun resetSchemaToDefaults(): Boolean {
        val defaults = createDefaultSchema()
        val success = firestoreService.saveSchema(defaults)
        if (success) {
            _spaceArchitectureSchema.value = defaults
            addAuditLog(
                actionType = "SCHEMA_RESET_DEFAULTS",
                details = "Admin restored factory baseline schema definitions for Lebanese workspaces",
                severity = "SECURE"
            )
        }
        return success
    }

    suspend fun seedDemoContent(): Boolean {
        val demoUsers = DemoDataGenerator.generateDemoUsers()
        val demoListings = DemoDataGenerator.generateDemoListings()
        val demoRequests = DemoDataGenerator.generateDemoBookings()

        var userCount = 0
        var spaceCount = 0
        var reqCount = 0

        demoUsers.forEach { u ->
            if (firestoreService.saveUserProfile(u)) userCount++
        }

        demoListings.forEach { s ->
            if (firestoreService.saveWorkspace(s)) spaceCount++
        }

        demoRequests.forEach { r ->
            if (firestoreService.saveBookingRequest(r)) reqCount++
        }

        val updatedUserMap = _users.value.associateBy { it.id }.toMutableMap()
        demoUsers.forEach { updatedUserMap[it.id] = it }
        _users.value = updatedUserMap.values.toList()

        val updatedSpaceMap = _spaces.value.associateBy { it.id }.toMutableMap()
        demoListings.forEach { updatedSpaceMap[it.id] = it }
        _spaces.value = updatedSpaceMap.values.toList()

        val updatedBookingMap = _bookingRequests.value.associateBy { it.id }.toMutableMap()
        demoRequests.forEach { updatedBookingMap[it.id] = it }
        _bookingRequests.value = updatedBookingMap.values.toList()

        addAuditLog(
            actionType = "DEMO_CONTENT_SEEDED",
            details = "Admin generated $spaceCount demo listings, $reqCount fake requests, and $userCount demo users.",
            severity = "INFO"
        )
        return true
    }

    suspend fun purgeDemoContent(): Int {
        var purgedCount = 0

        val demoSpaces = _spaces.value.filter {
            it.isDemo || it.id.startsWith("demo-") || it.id.startsWith("DEMO-")
        }
        demoSpaces.forEach { space ->
            if (firestoreService.deleteWorkspace(space.id)) purgedCount++
        }
        _spaces.value = _spaces.value.filterNot {
            it.isDemo || it.id.startsWith("demo-") || it.id.startsWith("DEMO-")
        }

        val demoBookings = _bookingRequests.value.filter {
            it.isDemo || it.id.startsWith("demo-") || it.id.startsWith("DEMO-")
        }
        demoBookings.forEach { booking ->
            if (firestoreService.deleteBookingRequest(booking.id)) purgedCount++
        }
        _bookingRequests.value = _bookingRequests.value.filterNot {
            it.isDemo || it.id.startsWith("demo-") || it.id.startsWith("DEMO-")
        }

        val demoUsers = _users.value.filter {
            it.isDemo || it.id.startsWith("demo-") || it.id.startsWith("DEMO-") || it.email.startsWith("demo.")
        }
        demoUsers.forEach { user ->
            if (firestoreService.deleteUserProfile(user.id)) purgedCount++
        }
        _users.value = _users.value.filterNot {
            it.isDemo || it.id.startsWith("demo-") || it.id.startsWith("DEMO-") || it.email.startsWith("demo.")
        }

        addAuditLog(
            actionType = "DEMO_CONTENT_PURGED",
            details = "Admin purged $purgedCount demo listings, fake requests, and demo users from system before production.",
            severity = "SECURE"
        )
        return purgedCount
    }

    suspend fun updateSchemaItem(item: SchemaItem): Boolean {
        val current = _spaceArchitectureSchema.value
        fun List<SchemaItem>.replace() = map { if (it.id == item.id) item else it }
        val updated = when (item.category) {
            SchemaCategory.SPACE_TYPE -> current.copy(spaceTypes = current.spaceTypes.replace())
            SchemaCategory.DIVISION_TYPE -> current.copy(divisionTypes = current.divisionTypes.replace())
            SchemaCategory.FACILITY -> current.copy(facilities = current.facilities.replace())
            SchemaCategory.AMENITY -> current.copy(amenities = current.amenities.replace())
            SchemaCategory.RENTAL_STRATEGY -> current.copy(rentalStrategies = current.rentalStrategies.replace())
            else -> return false
        }
        val success = firestoreService.saveSchema(updated)
        if (success) {
            _spaceArchitectureSchema.value = updated
            addAuditLog("SCHEMA_ITEM_UPDATED", "Schema item ${item.id} updated: ${item.name}", "INFO")
        }
        return success
    }

    suspend fun addUserSuggestedSchemaItem(item: SchemaItem): Boolean {
        val current = _spaceArchitectureSchema.value
        val allNames = current.allItems.map { it.name.lowercase() }
        if (item.name.lowercase() in allNames) return true // deduplicate silently
        return addSchemaItem(item)
    }

    suspend fun addAttendeePackage(pkg: AttendeePackage): Boolean {
        val current = _spaceArchitectureSchema.value
        val updated = current.copy(attendeePackages = current.attendeePackages + pkg)
        val success = firestoreService.saveSchema(updated)
        if (success) _spaceArchitectureSchema.value = updated
        return success
    }

    suspend fun updateAttendeePackage(pkg: AttendeePackage): Boolean {
        val current = _spaceArchitectureSchema.value
        val updated = current.copy(attendeePackages = current.attendeePackages.map { if (it.id == pkg.id) pkg else it })
        val success = firestoreService.saveSchema(updated)
        if (success) _spaceArchitectureSchema.value = updated
        return success
    }

    suspend fun deleteAttendeePackage(pkgId: String): Boolean {
        val current = _spaceArchitectureSchema.value
        val updated = current.copy(attendeePackages = current.attendeePackages.filter { it.id != pkgId })
        val success = firestoreService.saveSchema(updated)
        if (success) _spaceArchitectureSchema.value = updated
        return success
    }

    suspend fun toggleAttendeePackage(pkgId: String): Boolean {
        val current = _spaceArchitectureSchema.value
        val updated = current.copy(attendeePackages = current.attendeePackages.map {
            if (it.id == pkgId) it.copy(isEnabled = !it.isEnabled) else it
        })
        val success = firestoreService.saveSchema(updated)
        if (success) _spaceArchitectureSchema.value = updated
        return success
    }

    /**
     * Firestore rules deny every client write to workspace_listings.isVerified — only
     * setListingVerification's Admin SDK write can change it. This used to only ever
     * mutate local state (never actually reached Firestore, regardless of rules); now
     * it awaits the real server change before updating local state, so the two can't
     * drift if the call fails.
     */
    /** Returns whether the change actually succeeded, so the caller can show a real result. */
    suspend fun toggleListingVerification(spaceId: String): Boolean {
        val target = _spaces.value.find { it.id == spaceId } ?: return false
        val nextStatus = !target.isVerified
        val result = functionsClient.setListingVerification(spaceId, nextStatus)
        if (result.isSuccess) {
            _spaces.value = _spaces.value.map {
                if (it.id == spaceId) it.copy(isVerified = nextStatus) else it
            }
            addLocalAuditLogEntry(
                actionType = "VERIFICATION_OVERRIDE",
                details = "Workspace #${spaceId} verified status changed to $nextStatus by Super Admin",
                severity = "SECURE"
            )
        }
        return result.isSuccess
    }

    /**
     * Saves whichever verification document the host just uploaded — a normal
     * client write (verificationDocUrl/verificationDocType aren't rules-protected,
     * only isVerified itself is) — then immediately calls the Cloud Function that
     * actually earns the badge. Two steps because the function needs the document
     * URL already on the Firestore doc before it can check for it.
     */
    suspend fun requestOwnListingVerification(
        spaceId: String,
        docUrl: String,
        docType: ListingVerificationDocType
    ): Boolean {
        val target = _spaces.value.find { it.id == spaceId } ?: return false
        val withDoc = target.copy(verificationDocUrl = docUrl, verificationDocType = docType)
        if (!saveUpdatedSpace(withDoc)) return false

        // Doesn't flip isVerified locally: the badge is granted only when an Admin reviews
        // the document and calls setListingVerification; the live listener picks that up.
        return functionsClient.requestListingVerification(spaceId).isSuccess
    }

    /**
     * The host's own lifecycle control for a listing — Pause/Resume an Active
     * listing, or publish a Draft (Draft -> Active). Not Admin-only: `status`
     * isn't in firestore.rules' workspace_listings protected-fields list, so
     * a narrow merge write is enough; listingCountTracker.ts's update trigger
     * keeps activeListingCount in sync with the transition server-side.
     */
    suspend fun setListingStatus(spaceId: String, status: ListingStatus): Boolean {
        val success = firestoreService.updateWorkspaceListingFields(spaceId, mapOf("status" to status.name))
        if (success) {
            _spaces.value = _spaces.value.map {
                if (it.id == spaceId) it.copy(status = status) else it
            }
        }
        return success
    }

    /** Same pattern as [toggleListingVerification] — see its doc comment. */
    suspend fun toggleListingActive(spaceId: String): Boolean {
        val target = _spaces.value.find { it.id == spaceId } ?: return false
        val nextStatus = !target.isActiveSubscription
        val result = functionsClient.setListingSubscriptionActive(spaceId, nextStatus)
        if (result.isSuccess) {
            _spaces.value = _spaces.value.map {
                if (it.id == spaceId) it.copy(isActiveSubscription = nextStatus) else it
            }
            addLocalAuditLogEntry(
                actionType = "SUBSCRIPTION_STATUS_TOGGLE",
                details = "Listing #${spaceId} subscription active status set to $nextStatus by Super Admin",
                severity = "WARN"
            )
        }
        return result.isSuccess
    }

    // --- Smart Booking & In-App Rental Request Engine ---
    /**
     * Awaits the real Firestore write instead of firing it off in the
     * background — the caller (ProHostViewModel.submitBookingRequest) used to
     * show "Rental Request Sent!" the instant this returned, whatever the
     * actual sync outcome, since the write itself ran fire-and-forget via
     * syncNewBookingToFirestore. A specialist on a bad connection saw
     * confirmed success for a request that never reached Firestore — and
     * therefore never reached the host — with nothing telling them to retry.
     * The request is still added to local state optimistically (so it shows
     * up immediately in "My Bookings" even mid-sync), but [synced] in the
     * returned pair tells the caller whether that actually landed, so it can
     * show a truthful toast instead of an unconditional one.
     */
    suspend fun createBookingRequest(
        space: SpaceListing,
        formula: RentalFormula,
        practitioner: AppUser,
        startDate: String,
        durationMonths: Int,
        notes: String,
        selectedDays: List<String> = emptyList(),
        selectedCalendarDates: List<String> = emptyList(),
        selectedStartHour: String = "",
        selectedEndHour: String = "",
        selectedShift: String = "",
        calculatedTotalUsd: Double = 0.0,
        subdivisionId: String? = null,
        subdivisionName: String? = null,
        replacesBookingId: String? = null,
        attendeeCount: Int = 0,
        selectedAttendeePackageId: String? = null,
        attendeePackageName: String? = null,
        attendeePackagePriceUsd: Double = 0.0
    ): Pair<RentalBookingRequest, Boolean> {
        if (practitioner.id == space.ownerId) {
            throw IllegalArgumentException("A host cannot book their own listing.")
        }
        // Guard against duplicate submissions: reject if a PENDING request from this
        // practitioner for this space already exists in the local cache (M7).
        val hasPending = _bookingRequests.value.any { existing ->
            existing.spaceId == space.id &&
            existing.practitionerId == practitioner.id &&
            existing.status == BookingRequestStatus.PENDING &&
            existing.id != replacesBookingId
        }
        if (hasPending) {
            throw IllegalStateException("You already have a pending booking request for this space.")
        }
        // Was "REQ-LB-" + (1000..9999).random() — only ~9,000 distinct values,
        // no collision check, and saveBookingRequest below does a
        // .document(requestId).set(..., merge=true) — a collision wouldn't even
        // fail loudly, it would silently merge two unrelated bookings' fields
        // into one Firestore document. A UUID-derived id makes a collision
        // practically impossible (16^10 space) without losing the readable
        // "REQ-XXXXXXXXXX" shape the toasts/audit log already display.
        val requestId = "REQ-" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        val totalUsd = if (calculatedTotalUsd > 0) calculatedTotalUsd else (formula.rateUsd * durationMonths)

        val daysChosen = if (selectedDays.isNotEmpty()) selectedDays else formula.daysOfWeek
        val startH = if (selectedStartHour.isNotBlank()) selectedStartHour else formula.startHour
        val endH = if (selectedEndHour.isNotBlank()) selectedEndHour else formula.endHour
        val shiftDesc = if (selectedShift.isNotBlank()) " [$selectedShift]" else ""

        val rangeString = "${daysChosen.joinToString(", ")} $startH - $endH$shiftDesc (Starting $startDate, $durationMonths Mon" +
            "th${if (durationMonths > 1) "s" else ""})"

        val request = RentalBookingRequest(
            id = requestId,
            spaceId = space.id,
            spaceTitle = space.title,
            spaceDistrict = space.district,
            governorate = space.governorate,
            ownerId = space.ownerId,
            ownerName = space.ownerName,
            ownerPhone = space.ownerPhone,
            practitionerId = practitioner.id,
            practitionerName = practitioner.fullName,
            practitionerEmail = practitioner.email,
            practitionerPhone = practitioner.phone,
            practitionerSpecialty = practitioner.specialty,
            formula = formula,
            startDate = startDate,
            selectedDays = daysChosen,
            selectedCalendarDates = selectedCalendarDates,
            selectedStartHour = startH,
            selectedEndHour = endH,
            selectedShift = selectedShift,
            selectedDateTimeRange = rangeString,
            durationMonths = durationMonths,
            totalAmountUsd = totalUsd,
            clinicalNotes = notes,
            status = BookingRequestStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            subdivisionId = subdivisionId,
            subdivisionName = subdivisionName,
            replacesBookingId = replacesBookingId,
            attendeeCount = attendeeCount,
            selectedAttendeePackageId = selectedAttendeePackageId,
            attendeePackageName = attendeePackageName,
            attendeePackagePriceUsd = attendeePackagePriceUsd
        )

        _bookingRequests.value = listOf(request) + _bookingRequests.value
        val synced = firestoreService.saveBookingRequest(request)
        if (synced) {
            _isOfflineMode.value = false
            _syncStatusMessage.value = "Booking Synced with Firebase Cloud"
        } else {
            _isOfflineMode.value = true
            _syncStatusMessage.value = "Offline: Booking Stored in Local Cache"
        }

        addAuditLog(
            actionType = if (replacesBookingId != null) "RENTAL_REQUEST_EDIT_SUBMITTED" else "RENTAL_REQUEST_SUBMITTED",
            details = if (replacesBookingId != null) {
                "Edit request $requestId sent by ${practitioner.fullName} for '${space.title}', proposing to replace " +
                    "accepted booking #$replacesBookingId. New slot: $rangeString. Awaiting owner approval." +
                    (if (!synced) " [NOT YET SYNCED TO CLOUD]" else "")
            } else {
                "Request $requestId sent by ${practitioner.fullName} for '${space.title}' (${formula.type.displayName}" +
                    ", $${totalUsd.toInt()} USD). Selected Slot: $rangeString. Awaiting owner WhatsApp/In-app approval." +
                    (if (!synced) " [NOT YET SYNCED TO CLOUD]" else "")
            },
            severity = if (synced) "INFO" else "WARN",
            actorEmail = practitioner.email
        )

        return request to synced
    }

    /**
     * The already-ACCEPTED booking that [requestId] would collide with if accepted
     * now, or null when it's clear. Checked by the ViewModel before the agreement
     * upload (so a host isn't asked to upload a lease for a booking that can't be
     * accepted) and again inside [acceptBookingRequest] as the real guard.
     */
    fun findAcceptConflict(requestId: String): RentalBookingRequest? {
        val request = _bookingRequests.value.find { it.id == requestId } ?: return null
        return com.example.ui.util.SpaceCalculationUtils.findAcceptConflict(request, _bookingRequests.value)
    }

    /**
     * Owner accepting a booking means they've reached and evidenced a real agreement
     * with the specialist — [agreementUrl] is the signed lease they just uploaded to
     * Storage (see OwnerRentalRequestsScreen's Accept flow), kept on file as the
     * record of that, exactly like [SpaceListing.ownershipProofUrl]: self-attested,
     * never reviewed. There is no in-app payment settlement to track anymore — both
     * sides handle payment outside the app entirely (the old isExternalPaymentSettled
     * flag, and the "Pay Whish" flow that set it, are gone).
     *
     * If [request.replacesBookingId] is set, this acceptance is really an edit
     * superseding a previously accepted booking (see MyBookingsScreen's "Edit
     * Booking" action) — the superseded booking is released (marked CANCELLED) in
     * the same operation, so exactly one of the two is ever ACCEPTED and
     * availability — always derived live from ACCEPTED bookings + the space's
     * schedule, never a separately stored count — recalculates immediately.
     *
     * Never double-books: refuses (returns false) when [findAcceptConflict] finds an
     * ACCEPTED booking already holding the same room/space, day and hours.
     */
    suspend fun acceptBookingRequest(requestId: String, agreementUrl: String? = null): Boolean {
        val request = _bookingRequests.value.find { it.id == requestId } ?: return false
        // Never double-book: two ACCEPTED bookings can't overlap on the same room/space,
        // day and hours (SpaceCalculationUtils.findAcceptConflict — the same rule the
        // specialist-facing screens hide locked slots with).
        if (findAcceptConflict(requestId) != null) return false
        val now = System.currentTimeMillis()

        val extraFields = if (agreementUrl != null) mapOf("agreementUrl" to agreementUrl) else emptyMap()
        val success = firestoreService.updateBookingStatus(
            requestId,
            BookingRequestStatus.ACCEPTED,
            extraFields = extraFields
        )
        if (!success) return false

        _bookingRequests.value = _bookingRequests.value.map {
            if (it.id == requestId) {
                it.copy(status = BookingRequestStatus.ACCEPTED, reviewedAt = now, agreementUrl = agreementUrl ?: it.agreementUrl)
            } else it
        }

        // Add member to resident list if not present
        val memberString = "${request.practitionerName} (${request.practitionerSpecialty})"
        _spaces.value = _spaces.value.map { space ->
            if (space.id == request.spaceId && !space.residentPractitioners.contains(memberString)) {
                space.copy(residentPractitioners = space.residentPractitioners + memberString)
            } else space
        }

        // Release the booking this edit replaces, if any — see the doc comment above.
        request.replacesBookingId?.let { oldId ->
            val oldRequest = _bookingRequests.value.find { it.id == oldId }
            if (oldRequest != null && oldRequest.status == BookingRequestStatus.ACCEPTED) {
                val editCode = _bookingRequests.value.find { it.id == requestId }?.publicCode ?: publicCode("", requestId)
                val supersededReason = "Superseded by an accepted edit ($editCode)"
                val cancelledOld = firestoreService.updateBookingStatus(oldId, BookingRequestStatus.CANCELLED, rejectionReason = supersededReason)
                if (cancelledOld) {
                    _bookingRequests.value = _bookingRequests.value.map {
                        if (it.id == oldId) it.copy(status = BookingRequestStatus.CANCELLED, rejectionReason = supersededReason) else it
                    }
                }
            }
        }

        addAuditLog(
            actionType = "RENTAL_REQUEST_ACCEPTED",
            details = "Owner ${request.ownerName} accepted $requestId by ${request.practitionerName}. Formula '${request.formula.scheduleDescription}" +
                "' (${request.selectedDateTimeRange}) is now locked and marked unavailable for public display." +
                (request.replacesBookingId?.let { " Replaces booking #$it, now released." } ?: ""),
            severity = "SECURE",
            actorEmail = request.ownerName
        )

        return true
    }

    /**
     * Used to fire syncBookingStatusToFirestore (a detached coroutine.launch,
     * never awaited) and unconditionally return true — the same
     * fire-and-forget shape submitBookingRequest had before it was fixed
     * (see that function's own doc comment). A host declining a request saw
     * "Declined" toast success, and the local list flipped to REJECTED,
     * whether or not the Firestore write actually landed; a flaky connection
     * left the request still PENDING server-side while the host's own UI
     * insisted it was handled. Now suspend and await the real write, mirroring
     * acceptBookingRequest's own shape, and only report/apply success when it
     * genuinely succeeded.
     */
    suspend fun rejectBookingRequest(requestId: String, note: String? = null): Boolean {
        val request = _bookingRequests.value.find { it.id == requestId } ?: return false
        val now = System.currentTimeMillis()
        val reason = note ?: "Declined by space owner"

        val success = firestoreService.updateBookingStatus(requestId, BookingRequestStatus.REJECTED, reason)
        if (!success) return false

        _bookingRequests.value = _bookingRequests.value.map {
            if (it.id == requestId) {
                it.copy(status = BookingRequestStatus.REJECTED, reviewedAt = now, rejectionReason = reason)
            } else it
        }

        addAuditLog(
            actionType = "RENTAL_REQUEST_DECLINED",
            details = "Request $requestId declined by owner ${request.ownerName} (Reason: $reason). Hours remain available to the public.",
            severity = "WARN",
            actorEmail = request.ownerName
        )

        return true
    }

    /** See rejectBookingRequest's doc comment — same fire-and-forget bug, same fix. */
    suspend fun cancelBookingRequest(requestId: String): Boolean {
        val request = _bookingRequests.value.find { it.id == requestId } ?: return false

        val success = firestoreService.updateBookingStatus(requestId, BookingRequestStatus.CANCELLED)
        if (!success) return false

        _bookingRequests.value = _bookingRequests.value.map {
            if (it.id == requestId) it.copy(status = BookingRequestStatus.CANCELLED) else it
        }

        addAuditLog(
            actionType = "RENTAL_REQUEST_CANCELLED",
            details = "Request $requestId for '${request.spaceTitle}' cancelled by practitioner ${request.practitionerName} before host review.",
            severity = "INFO",
            actorEmail = request.practitionerEmail
        )

        return true
    }

    /**
     * Early termination of an already-ACCEPTED booking — the gap flagged as a genuine
     * open item: previously the only cancellation path was for a not-yet-accepted
     * PENDING request (cancelBookingRequest above). Deliberately simple per the
     * current terms-of-use cancellation workflow: a reason code plus an optional
     * note, no refund/penalty logic (there's nothing to refund — rent settlement
     * never happens in-app). [cancelledByUid]/[cancelledByRole] identify who ended
     * it (only the booking's own practitioner, its owner, or an Admin may call this
     * — enforced by the caller checking against the loaded [BookingRequest] before
     * invoking it, same pattern as reject/accept). A real cross-device push notifies
     * whichever side didn't initiate the cancellation (onBookingRequestStatusChanged,
     * functions/src/notifications/bookingNotifications.ts).
     */
    suspend fun cancelAcceptedBooking(
        requestId: String,
        reasonCode: CancellationReasonCode,
        note: String?,
        cancelledByUid: String,
        cancelledByRole: String
    ): Boolean {
        val request = _bookingRequests.value.find { it.id == requestId } ?: return false
        if (request.status != BookingRequestStatus.ACCEPTED) return false

        val success = firestoreService.updateBookingStatus(
            requestId,
            BookingRequestStatus.CANCELLED,
            extraFields = mapOf(
                "cancellationReasonCode" to reasonCode.name,
                "cancellationNote" to note,
                "cancelledByRole" to cancelledByRole
            )
        )
        if (!success) return false

        _bookingRequests.value = _bookingRequests.value.map {
            if (it.id == requestId) {
                it.copy(
                    status = BookingRequestStatus.CANCELLED,
                    cancellationReasonCode = reasonCode.name,
                    cancellationNote = note,
                    cancelledByRole = cancelledByRole
                )
            } else it
        }

        addAuditLog(
            actionType = "ACCEPTED_BOOKING_CANCELLED",
            details = "Accepted booking $requestId for '${request.spaceTitle}' (${request.practitionerName} / ${request.ownerName}" +
                ") terminated early by $cancelledByRole. Reason: ${reasonCode.displayName}${if (!note.isNullOrBlank()) " — \"$note\"" else ""}" +
                ".",
            severity = "WARN",
            actorEmail = if (cancelledByUid == request.practitionerId) request.practitionerEmail else request.ownerName
        )

        return true
    }

    /**
     * Mutual, independent "Mark as Paid" acknowledgment — record-keeping only, since
     * rent settlement happens entirely outside the app. Each side can only ever set
     * their own flag (the caller decides which); this never touches the other side's.
     */
    suspend fun acknowledgePayment(requestId: String, asHost: Boolean): Boolean {
        val field = if (asHost) "paymentAcknowledgedByHost" else "paymentAcknowledgedBySpecialist"
        val success = firestoreService.updateBookingFields(requestId, mapOf(field to true))
        if (success) {
            _bookingRequests.value = _bookingRequests.value.map {
                if (it.id == requestId) {
                    if (asHost) it.copy(paymentAcknowledgedByHost = true) else it.copy(paymentAcknowledgedBySpecialist = true)
                } else it
            }
        }
        return success
    }

    // --- Schedule & Blackout Slots Management ---
    // All five of these used to only mutate the in-memory _spaces StateFlow — never
    // calling Firestore at all. That's worse than just "no error handling": since
    // attachLiveListeners' workspace_listings snapshot listener periodically overwrites
    // _spaces wholesale, a schedule/formula change that never reached Firestore could
    // be silently reverted on the SAME device the next time any unrelated write
    // triggered a snapshot, on top of never syncing to any other device at all. Now
    // persisted via the same firestoreService.saveWorkspace path updateSpaceListing
    // already uses, only committing local state once the write is confirmed.

    /**
     * Same guard updateSpaceListing applies: always write back the CURRENTLY STORED
     * ownerId/isVerified/isActiveSubscription/subscriptionExpiryMillis rather than
     * whatever the local copy holds. firestore.rules rejects any workspace_listings
     * update whose affectedKeys() touches those, and fromFirestoreMap regenerates a
     * fresh subscriptionExpiryMillis (now + 30 days) whenever the field is missing
     * from the document — so echoing the local value back could change that key and
     * get the whole write denied, permanently, for that listing. Schedule, blackout
     * and formula saves all funnel through here and never had this protection.
     */
    private suspend fun saveUpdatedSpace(updated: SpaceListing): Boolean {
        val current = _spaces.value.find { it.id == updated.id }
        val safeUpdate = updated.keepingServerOwnedFields(current)
        val success = firestoreService.saveWorkspace(safeUpdate)
        if (success) {
            _spaces.value = _spaces.value.map { if (it.id == safeUpdate.id) safeUpdate else it }
        }
        return success
    }

    suspend fun addBlackoutSlot(spaceId: String, slot: BlackoutSlot): Boolean {
        val space = _spaces.value.find { it.id == spaceId } ?: return false
        val updated = space.copy(schedule = space.schedule.copy(blackoutSlots = space.schedule.blackoutSlots + slot))
        val success = saveUpdatedSpace(updated)
        if (success) {
            addAuditLog(
                actionType = "SCHEDULE_BLACKOUT_ADDED",
                details = "Owner added non-operating blackout slot (${slot.dayOfWeek} ${slot.startTime}-${slot.endTime}) to space $spaceId",
                severity = "INFO"
            )
        }
        return success
    }

    suspend fun removeBlackoutSlot(spaceId: String, slotId: String): Boolean {
        val space = _spaces.value.find { it.id == spaceId } ?: return false
        val updated = space.copy(schedule = space.schedule.copy(blackoutSlots = space.schedule.blackoutSlots.filter { it.id != slotId }))
        return saveUpdatedSpace(updated)
    }

    /**
     * Rooms/desks were previously only editable at listing-creation time
     * (CreateListingDialog's Step 2) — a host who published first and only later
     * realized they needed another room, or wanted to remove one, had no in-app way
     * to do it. These three funnel through the same saveUpdatedSpace protected-field
     * guard as the blackout/formula functions above; subdivisions live nested inside
     * the same workspace_listings document, so no firestore.rules change is needed.
     */
    suspend fun addSubdivision(spaceId: String, subdivision: Subdivision): Boolean {
        val space = _spaces.value.find { it.id == spaceId } ?: return false
        val updated = space.copy(subdivisions = space.subdivisions + subdivision)
        val success = saveUpdatedSpace(updated)
        if (success) {
            addAuditLog(
                actionType = "SUBDIVISION_ADDED",
                details = "Added room/desk '${subdivision.name}' (${subdivision.type.displayName}) to space $spaceId",
                severity = "INFO"
            )
        }
        return success
    }

    suspend fun removeSubdivision(spaceId: String, subdivisionId: String): Boolean {
        val space = _spaces.value.find { it.id == spaceId } ?: return false
        val updated = space.copy(subdivisions = space.subdivisions.filter { it.id != subdivisionId })
        val success = saveUpdatedSpace(updated)
        if (success) {
            addAuditLog(
                actionType = "SUBDIVISION_REMOVED",
                details = "Removed room/desk #$subdivisionId from space $spaceId",
                severity = "INFO"
            )
        }
        return success
    }

    // Best-effort, fire-and-forget engagement counters (real "Views"/"Inquiries" data,
    // replacing the old fixed 850/14 placeholders). Not offline-queued — this is a
    // low-stakes analytics counter, not a transaction, so silently no-op-ing while
    // offline is an acceptable tradeoff. The live workspace_listings snapshot
    // listener picks up the new value and refreshes _spaces automatically, so no
    // manual local-state patch is needed here. Mirrors addAuditLog's coroutineScope
    // fire-and-forget pattern rather than being a suspend fun.
    fun incrementSpaceViewCount(spaceId: String) {
        coroutineScope.launch { firestoreService.incrementSpaceCounter(spaceId, "avatarEngagementViews") }
    }

    fun incrementSpaceInquiryCount(spaceId: String) {
        coroutineScope.launch { firestoreService.incrementSpaceCounter(spaceId, "avatarInquiryClicks") }
    }

    // --- User Authentication & Member Registration ---
    /**
     * Registers a new member. [uid] must be the real Firebase Auth UID (so this user's
     * `id` lines up with the `user_profiles/{uid}` document the role-claim Cloud Functions
     * write to) and [verifiedRole] must already have been confirmed server-side — see
     * [com.example.data.auth.completeVerifiedRegistration]. [isVerified] reflects that
     * [uid]'s Firebase Auth account already completed phone-number SMS verification
     * before this is ever called (see LoginAuthScreen's OTP flow) — there is no admin
     * accreditation step anymore.
     *
     * Suspend, and its Firestore write is awaited and checked, because of a real bug this
     * fixes: assignInitialRole.ts's Admin-SDK write always lands (and creates the
     * user_profiles/{uid} document) before this function's own write does, so by the time
     * this write reaches Firestore it's evaluated as an UPDATE, not a create — and
     * AppUser.toFirestoreMap() unconditionally includes ownerPackageId/
     * ownerPackageExpiryMillis, both on the update rule's protected-fields list (see
     * PROTECTED_UPDATE_FIELDS below), so the whole write used to be silently rejected
     * with permission-denied. The caller (completeVerifiedRegistration) is already a
     * suspend fun with exactly one call site, so making this suspend too costs nothing.
     */
    suspend fun registerMember(
        uid: String,
        verifiedRole: UserRole,
        details: RegistrationDetails
    ): AppUser {
        val cleanEmail = details.email.trim().lowercase()
        val newUser = AppUser(
            id = uid,
            email = cleanEmail,
            fullName = details.fullName.trim(),
            role = verifiedRole,
            specialty = details.specialty.trim(),
            phone = details.phone.trim(),
            profilePictureUrl = details.profilePictureUrl,
            country = details.country.trim(),
            governorate = details.governorate.trim(),
            city = details.city.trim(),
            isVerified = true
        )

        // A targeted merge of only the fields this registration actually owns — never the
        // fields firestore.rules' user_profiles update rule protects (role/isVerified/
        // ownerPackageId/etc. — assignInitialRole.ts already correctly set/defaulted all
        // of those). Filtering toFirestoreMap() by this list, rather than hand-listing the
        // "safe" fields, means a future field added to toFirestoreMap() is safe-by-default
        // unless it's also added here. Keep this in sync with firestore.rules' own list.
        val safeFields = newUser.toFirestoreMap().filterKeys { it !in PROTECTED_UPDATE_FIELDS }
        val saved = firestoreService.updateUserProfileFields(uid, safeFields)
        if (!saved) {
            throw IllegalStateException("We couldn't save your profile. Please check your connection and try again.")
        }

        _users.value = _users.value.filterNot { it.id == uid } + newUser
        _currentUser.value = newUser

        addAuditLog(
            actionType = "MEMBER_REGISTRATION",
            details = "New member registered: ${newUser.fullName} (${newUser.role.name}) • ${newUser.specialty} • ${newUser.city}" +
                ", ${newUser.governorate}, ${newUser.country}",
            severity = "SECURE",
            actorEmail = newUser.email
        )
        return newUser
    }

    /**
     * Signs a user in locally once their identity AND role have already been verified
     * server-side (real Firebase Auth sign-in, then the role read from their ID token's
     * custom claim — see [com.example.data.auth.completeVerifiedLogin]). [uid] must be
     * the Firebase Auth UID; [verifiedRole] must come from the token claim, never from
     * UI state. There is no code path here that grants a role from caller-supplied input.
     * [email] may be blank (a phone-auth FirebaseUser has no email of its own) — a blank
     * value never overwrites a previously-stored real email.
     *
     * This used to only ever check [_users]' in-memory cache for an existing profile —
     * but that cache is populated by [startRealtimeSync]'s listeners, which only
     * (re)attach in reaction to [_currentUser] changing, and [_currentUser] is set at
     * the very end of THIS function. On the very first sign-in of a fresh app process
     * (a returning user on a new device, or right after a reinstall/clear-data), the
     * cache is therefore always empty at the moment this runs — every returning user's
     * very first login of a session fabricated a brand-new, blank-phone profile, which
     * routed them straight to the registration form despite a real, complete profile
     * already sitting in Firestore. This is the confirmed root cause of a registered
     * user being redirected to registration instead of recognized on login. A real,
     * awaited direct document read (already rules-permitted — it's the same document
     * [FirestoreService]'s own per-user listener reads) closes the race outright: the
     * cache is still checked first as a fast path, but a cache miss now falls through
     * to Firestore itself rather than assuming "no profile exists yet."
     */

    /** Cache-first lookup used for idempotency checks (e.g. before re-writing a profile). */
    suspend fun getUserProfile(uid: String): AppUser? =
        _users.value.find { it.id == uid }
            ?: firestoreService.getUserProfile(uid)?.let { AppUser.fromFirestoreMap(uid, it) }

    suspend fun login(uid: String, email: String, verifiedRole: UserRole): AppUser {
        val cleanEmail = email.trim().lowercase()
        val existing = _users.value.find { it.id == uid }
            ?: firestoreService.getUserProfile(uid)?.let { AppUser.fromFirestoreMap(uid, it) }

        // No stored profile yet → a blank name, so isProfileComplete() routes the user to
        // the registration form instead of into the app with an invented identity.
        val user = existing?.copy(role = verifiedRole, email = cleanEmail.ifBlank { existing.email }) ?: AppUser(
            id = uid,
            email = cleanEmail,
            fullName = "",
            role = verifiedRole,
            specialty = "",
            phone = "",
            country = "",
            governorate = "",
            city = "",
            isVerified = true
        )

        _users.value = _users.value.filterNot { it.id == uid } + user
        _currentUser.value = user
        // assignInitialRole.ts already runs (Admin SDK, bypassing rules) before this
        // is ever called — see completeVerifiedLogin — and keeps role/isVerified/
        // createdAtMillis/lastSignInAtMillis correctly in sync server-side on every
        // sign-in. This client-side write exists only to fix up email, and only ever
        // as a targeted field: writing this function's other, locally-fabricated
        // defaults (specialty="", phone="", country="Lebanon", ...) for a user whose
        // real profile just hasn't synced to this device yet would clobber their real
        // stored values, and echoing role/isVerified back risks disagreeing with the
        // real server-stored value and getting the whole write rejected.
        if (cleanEmail.isNotBlank()) {
            coroutineScope.launch { firestoreService.updateUserProfileFields(uid, mapOf("email" to cleanEmail)) }
        }

        addAuditLog(
            actionType = "USER_LOGIN_SUCCESS",
            details = "Role: ${user.role.name} • Name: ${user.fullName} (${user.email})",
            severity = if (user.role == UserRole.ADMIN) "SECURE" else "INFO",
            actorEmail = user.email
        )
        return user
    }

    /**
     * @param clearRemotePushToken Best-effort clears this device's fcmToken off the
     * signed-out user's own profile doc before it's nulled locally, so a push meant
     * for them can't keep reaching this device once someone else signs in on it —
     * see ProHostViewModel.logout()'s comment for why this must run before
     * FirebaseAuth.signOut() invalidates the write's auth context. Pass false from
     * account-deletion (ProHostViewModel.deleteAccount()): the profile doc there has
     * already been deleted server-side, and a merge write after that would just
     * resurrect a stub user_profiles/{uid} doc with nothing in it but this field.
     */
    suspend fun logout(clearRemotePushToken: Boolean = true) {
        val loggedOutUser = _currentUser.value
        val previous = loggedOutUser?.email ?: "Unknown"
        if (clearRemotePushToken && loggedOutUser != null) {
            try {
                // Only clear the stored token when it is THIS device's — otherwise signing
                // out here would silence push on the user's other, still-signed-in phone.
                val deviceToken = runCatching {
                    @Suppress("DEPRECATION")
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                }.getOrNull()
                val storedToken = firestoreService.getUserProfile(loggedOutUser.id)?.get("fcmToken") as? String
                if (storedToken != null && (deviceToken == null || storedToken == deviceToken)) {
                    firestoreService.updateUserProfileFields(loggedOutUser.id, mapOf("fcmToken" to null))
                }
            } catch (e: Exception) {
                Log.w(TAG, "FCM token clear on logout failed: ${e.message}")
            }
        }
        // Also clear the in-app "Real-time Alerts Terminal" — this StateFlow is a
        // process-wide singleton with no per-uid scoping, so without this an alert
        // history from the account that just signed out stayed visible to whoever
        // signs in next in the same app process.
        _fcmAlerts.value = emptyList()
        _currentUser.value = null
        _hasLoadedBookingsOnce.value = false
        _hasLoadedSpacesOnce.value = false
        addAuditLog(
            actionType = "USER_LOGOUT",
            details = "Session closed for $previous",
            severity = "INFO",
            actorEmail = previous
        )
    }

    /**
     * Undoes completeVerifiedLogin's currentUser assignment for one specific
     * case: a phone number that's Firebase-Auth-verified but whose registration
     * was interrupted before the profile form was ever submitted (app killed
     * between OTP verification and completeVerifiedRegistration). completeVerifiedLogin
     * always treats a returning uid as a normal login and sets currentUser
     * unconditionally — the caller (ProHostViewModel's cold-start check /
     * AuthViewModel.finishPhoneVerification) detects the bare profile
     * afterward and calls this to put the app back in "signed out" state for
     * routing purposes, without touching the still-valid Firebase Auth
     * session itself (unlike [logout], this is not a real sign-out — the
     * caller is about to route into the registration form, which needs that
     * session to stay alive).
     */
    fun discardIncompleteSession() {
        _currentUser.value = null
    }

    // setCurrentUser() and switchRole() were removed here — both let any caller (or,
    // for switchRole specifically, any already-logged-in user) instantly become ADMIN
    // with no server check at all. A role change now only ever happens through the
    // login()/registerMember() paths above, which require a role already verified via
    // a Firebase Auth custom claim, through the grantAdminRole Cloud Function for an
    // explicit Admin grant, or through grantEntitlement() promoting a SPECIALIST to
    // PRO_HOST the moment their Google Play subscription activates.

    suspend fun updateCurrentUserProfile(
        name: String,
        specialty: String,
        phone: String,
        country: String,
        governorate: String,
        city: String,
        profilePictureUrl: String? = null
    ): Boolean {
        val current = _currentUser.value ?: return false
        val updated = current.copy(
            fullName = name,
            specialty = specialty,
            phone = phone,
            country = country,
            governorate = governorate,
            city = city,
            profilePictureUrl = profilePictureUrl ?: current.profilePictureUrl
        )
        // A targeted write of only these fields — never role/isVerified/isSuspended/
        // ownerPackageId/etc. Echoing those back from the locally-cached AppUser
        // (the old approach) could disagree with the real server-stored value (e.g.
        // right after a role grant the local cache hasn't refreshed yet) and get the
        // *entire* write rejected by firestore.rules' protected-fields check, even
        // though the caller only meant to change their name.
        val success = firestoreService.updateUserProfileFields(
            current.id,
            mapOf(
                "fullName" to updated.fullName,
                "specialty" to updated.specialty,
                "phone" to updated.phone,
                "country" to updated.country,
                "governorate" to updated.governorate,
                "city" to updated.city,
                "profilePictureUrl" to updated.profilePictureUrl
            )
        )
        if (success) {
            _currentUser.value = updated
            _users.value = _users.value.map { if (it.id == updated.id) updated else it }
        }
        return success
    }

    /**
     * Persists the phone number to Firestore right after a successful KYC phone
     * link (FirebaseAuthService.linkPhoneCredentialToCurrentUser only links the
     * credential at the Firebase Auth level — it never touches Firestore). Without
     * this write, a Google/email user who completes phone KYC keeps a blank
     * user_profiles.phone forever, which both re-triggers the "needs KYC" gate
     * (ProHostNavGraph) and re-sends them to the registration form on their next
     * sign-in (AuthViewModel.finishVerification's stranded-account check).
     */
    suspend fun updatePhoneAfterKycLink(e164Phone: String): Boolean {
        val current = _currentUser.value ?: return false
        val success = firestoreService.updateUserProfileFields(
            current.id,
            mapOf("phone" to e164Phone)
        )
        if (success) {
            val updated = current.copy(phone = e164Phone)
            _currentUser.value = updated
            _users.value = _users.value.map { if (it.id == updated.id) updated else it }
        }
        return success
    }

    /**
     * Records the purchase token so the RTDN handler can look up this user by token
     * as a fallback. Role promotion and expiry are written exclusively by the server
     * (billing/playBillingRtdn.ts grantSubscription) using Play's canonical expiryTimeMillis —
     * never set from the client to avoid clock skew and protected-field rule rejections.
     */
    /**
     * Toggles [spaceId] in the current user's personal saved/favorites list. Not a
     * protected field — any signed-in user may freely write their own savedSpaceIds,
     * so a plain merge write of the recomputed list is enough (no rules change needed).
     */
    /**
     * Was fully non-optimistic (waited for the Firestore round-trip before ever
     * flipping currentUser), so every heart-icon tap had a visible delay before
     * it reflected — and both call sites (DiscoveryScreen, SpaceDetailsScreen)
     * discarded the returned Boolean entirely, so a write failure did nothing:
     * not even a revert of the local state that, in this old code, hadn't
     * changed yet anyway. Now flips currentUser immediately (this field is a
     * per-user preference list, not security/money-sensitive, so an optimistic
     * update carries no real risk) and reverts it if the write genuinely fails —
     * a real, visible signal instead of a silent no-op.
     */
    suspend fun toggleSavedSpace(spaceId: String): Boolean {
        val current = _currentUser.value ?: return false
        val updatedIds = if (current.savedSpaceIds.contains(spaceId)) {
            current.savedSpaceIds - spaceId
        } else {
            current.savedSpaceIds + spaceId
        }
        val updated = current.copy(savedSpaceIds = updatedIds)
        _currentUser.value = updated
        _users.value = _users.value.map { if (it.id == updated.id) updated else it }

        val success = firestoreService.updateUserProfileFields(
            current.id,
            mapOf("savedSpaceIds" to updatedIds)
        )
        // Revert only if nothing has changed currentUser since this call made
        // its own optimistic write — comparing against [updated] (not
        // overwriting with [current] unconditionally) so a rapid second toggle
        // that already landed isn't clobbered by this call's late failure.
        if (!success && _currentUser.value == updated) {
            _currentUser.value = current
            _users.value = _users.value.map { if (it.id == current.id) current else it }
        }
        return success
    }

    /** Registers this device's FCM token against [uid]'s profile — see FirestoreService.saveFcmToken. */
    suspend fun registerFcmToken(uid: String, token: String): Boolean = firestoreService.saveFcmToken(uid, token)

    /** Sets only the signed-in user's photo (RequirementsSheet). A targeted, rules-safe write. */
    /** Address-only profile update (RequirementsSheet's hosting step). */
    suspend fun updateAddress(country: String, city: String): Boolean {
        val current = _currentUser.value ?: return false
        val success = firestoreService.updateUserProfileFields(current.id, mapOf("country" to country, "city" to city))
        if (success) _currentUser.value = current.copy(country = country, city = city)
        return success
    }

    suspend fun updateProfilePicture(profilePictureUrl: String): Boolean {
        val current = _currentUser.value ?: return false
        val success = firestoreService.updateUserProfileFields(current.id, mapOf("profilePictureUrl" to profilePictureUrl))
        if (success) _currentUser.value = current.copy(profilePictureUrl = profilePictureUrl)
        return success
    }

    // --- CSV exports (formatting lives in RepositoryCsv.kt) ---
    fun exportAuditLogsToCsv(startDateMillis: Long? = null, endDateMillis: Long? = null): String =
        RepositoryCsv.auditLogs(_auditLogs.value, startDateMillis, endDateMillis)
    fun exportOwnerRegistrationsToCsv(): String = RepositoryCsv.ownerRegistrations(_users.value, _spaces.value)
    fun exportListingsToCsv(): String = RepositoryCsv.listings(_spaces.value)
    fun exportWorkspacesCsv(): String = exportListingsToCsv()
    fun exportBookingsCsv(): String = RepositoryCsv.bookings(_bookingRequests.value)
    fun exportUsersCsv(): String = exportOwnerRegistrationsToCsv()

    /**
     * Server-side verification + entitlement restore for a Google Play purchase whose
     * RTDN delivery may have been dropped. Delegates to verifyAndRestorePurchase Cloud
     * Function (functions/src/billing/verifyAndRestorePurchase.ts).
     *
     * Returns the verified expiry timestamp in milliseconds on success.
     */
    suspend fun verifyAndRestorePlayPurchase(purchaseToken: String, productId: String, fromCheckout: Boolean = false): Result<Long> =
        functionsClient.verifyAndRestorePurchase(purchaseToken, productId, fromCheckout)
}
