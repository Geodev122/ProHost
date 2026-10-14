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
    internal val firestoreService: FirestoreService = FirestoreService.getInstance(),
    internal val functionsClient: FirebaseFunctionsClient = FirebaseFunctionsClient()
) {

    companion object {
        internal const val TAG = "ProHostRepository"

        // Must stay in sync with firestore.rules' user_profiles update rule's own
        // protected-fields list — every one of these is exclusively server-maintained
        // (assignInitialRole/grantAdminRole/setAccountSuspended/pinAuth/
        // emailVerification functions). Used by registerMember to filter its
        // write down to a safe subset — see that function's own doc comment.
        internal val PROTECTED_UPDATE_FIELDS = setOf(
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

    internal val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** True for the hermetic test repository (no Firestore; see FirestoreService.localOnly). */
    val isLocalOnly: Boolean get() = firestoreService.localOnly

    internal val _isCloudConnected = MutableStateFlow(false)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    internal val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    internal val _syncStatusMessage = MutableStateFlow<String?>("Synced with Lebanese Cloud Network")
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    internal val _fcmAlerts = MutableStateFlow<List<FCMAlert>>(emptyList())
    val fcmAlerts: StateFlow<List<FCMAlert>> = _fcmAlerts.asStateFlow()

    // Ids of alerts that exist in Firestore (only those can be marked read server-side).
    private var storedAlertIds: Set<String> = emptySet()

    /** A push that just arrived, shown at once; the Firestore snapshot replaces it (same id). */
    fun addFCMAlert(alert: FCMAlert) {
        if (_fcmAlerts.value.any { it.id == alert.id }) return
        _fcmAlerts.value = listOf(alert) + _fcmAlerts.value
    }

    internal fun onNotificationsSynced(stored: List<FCMAlert>) {
        storedAlertIds = stored.map { it.id }.toSet()
        val now = System.currentTimeMillis()
        // Keep a just-received push the snapshot hasn't caught up with yet (a few seconds).
        val pending = _fcmAlerts.value.filter { it.id !in storedAlertIds && now - it.timestamp < 60_000 }
        _fcmAlerts.value = (pending + stored).filter { it.expireAt > now }.sortedByDescending { it.timestamp }
    }

    fun markAlertAsRead(alertId: String) = markAlertsRead(listOf(alertId))

    fun markAllAlertsRead() = markAlertsRead(_fcmAlerts.value.filter { !it.isRead }.map { it.id })

    private fun markAlertsRead(ids: List<String>) {
        if (ids.isEmpty()) return
        _fcmAlerts.value = _fcmAlerts.value.map { if (it.id in ids) it.copy(isRead = true) else it }
        val uid = _currentUser.value?.id ?: return
        val stored = ids.filter { it in storedAlertIds }
        if (stored.isNotEmpty()) coroutineScope.launch { firestoreService.markNotificationsRead(uid, stored) }
    }

    internal val _pricingState = MutableStateFlow(AdminPricingState())
    val pricingState: StateFlow<AdminPricingState> = _pricingState.asStateFlow()

    internal val _spaces = MutableStateFlow<List<SpaceListing>>(emptyList())
    val spaces: StateFlow<List<SpaceListing>> = _spaces.asStateFlow()

    internal val _users = MutableStateFlow<List<AppUser>>(emptyList())
    val users: StateFlow<List<AppUser>> = _users.asStateFlow()

    internal val _auditLogs = MutableStateFlow<List<AuditSecurityLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditSecurityLog>> = _auditLogs.asStateFlow()

    // Starts signed out. This previously defaulted to a fully-populated Super Admin
    // AppUser, meaning every fresh install of the app opened directly into the Admin
    // console with zero authentication — no login screen ever shown, no credential
    // ever checked. That was, by a wide margin, the most severe bug in the app.
    internal val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    internal val _bookingRequests = MutableStateFlow<List<RentalBookingRequest>>(emptyList())
    val bookingRequests: StateFlow<List<RentalBookingRequest>> = _bookingRequests.asStateFlow()

    // Flips true the moment the first real Firestore snapshot for bookings arrives
    // (see onBookingsUpdated below) — distinct from bookingRequests simply being
    // empty, which is indistinguishable from "still loading" without this. Screens
    // reading practitionerBookings/ownerIncomingRequests (MyBookingsScreen,
    // OwnerIncomingRequestsView, OwnerRentingProgressScreen) used to render their
    // "No bookings yet" empty state instantly on open, even for an account with real
    // bookings, for however long the first snapshot took to arrive.
    internal val _hasLoadedBookingsOnce = MutableStateFlow(false)
    val hasLoadedBookingsOnce: StateFlow<Boolean> = _hasLoadedBookingsOnce.asStateFlow()

    // Same reasoning as hasLoadedBookingsOnce above, for Discovery: isLoading was
    // hardcoded false in DiscoveryViewModel, so the "no listings match your
    // filters" empty state rendered instantly on open, before the first real
    // workspace_listings snapshot ever arrived — indistinguishable from an
    // account that genuinely has no matches.
    internal val _hasLoadedSpacesOnce = MutableStateFlow(false)
    val hasLoadedSpacesOnce: StateFlow<Boolean> = _hasLoadedSpacesOnce.asStateFlow()

    // Set when the listings listener fails (rules denial, network, missing index);
    // cleared by the next successful snapshot. Lets Explore show an error instead
    // of spinning forever on !hasLoadedSpacesOnce.
    internal val _spacesLoadError = MutableStateFlow<String?>(null)
    val spacesLoadError: StateFlow<String?> = _spacesLoadError.asStateFlow()

    internal val _spaceArchitectureSchema = MutableStateFlow<SpaceArchitectureSchema>(createDefaultSchema())
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
    internal var publicListingsLimit = FirestoreService.PUBLIC_LISTINGS_PAGE
    internal val _hasMoreSpaces = MutableStateFlow(false)
    /** True when Explore's loaded page is full, so more active listings may exist. */
    val hasMoreSpaces: StateFlow<Boolean> = _hasMoreSpaces.asStateFlow()
    internal val _reviewQueue = MutableStateFlow<List<SpaceListing>>(emptyList())
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
                onNotificationsUpdated = { onNotificationsSynced(it) },
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
    internal fun addLocalAuditLogEntry(
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
    internal suspend fun persistPricingState(fields: Map<String, Any>): Boolean {
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
    internal fun SpaceListing.keepingServerOwnedFields(current: SpaceListing?): SpaceListing =
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
    /**
     * Pro Host "Change price": the server (changeSlotPrice) sets the slot's price on the listing and
     * applies [decisions] to the bookings using it — rules never let a client change a booking total.
     * The live listing/booking listeners pick the result up.
     */
    suspend fun changeSlotPrice(
        spaceId: String,
        ref: com.example.ui.util.PriceChange.SlotRef,
        newPrice: Double,
        decisions: Map<String, PriceChangeMode>
    ): Result<com.example.data.auth.SlotPriceChangeResult> = functionsClient.changeSlotPrice(
        spaceId = spaceId,
        scopeId = ref.scopeId,
        kind = ref.kind.name,
        key = ref.key,
        newPrice = newPrice,
        decisions = decisions.mapValues { it.value.name }
    )

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

    /** A listing by id, fetched when it isn't in the live lists (Explore loads 100 at a time). */
    suspend fun fetchListing(spaceId: String): SpaceListing? = firestoreService.fetchListing(spaceId)

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
    internal suspend fun saveUpdatedSpace(updated: SpaceListing): Boolean {
        val current = _spaces.value.find { it.id == updated.id }
        val safeUpdate = updated.keepingServerOwnedFields(current)
        val success = firestoreService.saveWorkspace(safeUpdate)
        if (success) {
            _spaces.value = _spaces.value.map { if (it.id == safeUpdate.id) safeUpdate else it }
        }
        return success
    }

    // --- BookingsRepository (moved; these delegates keep every call site unchanged) ---
    private val bookingsRepo by lazy { BookingsRepository(this) }
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
    ): Pair<RentalBookingRequest, Boolean> = bookingsRepo.createBookingRequest(space = space, formula = formula, practitioner = practitioner, startDate = startDate, durationMonths = durationMonths, notes = notes, selectedDays = selectedDays, selectedCalendarDates = selectedCalendarDates, selectedStartHour = selectedStartHour, selectedEndHour = selectedEndHour, selectedShift = selectedShift, calculatedTotalUsd = calculatedTotalUsd, subdivisionId = subdivisionId, subdivisionName = subdivisionName, replacesBookingId = replacesBookingId, attendeeCount = attendeeCount, selectedAttendeePackageId = selectedAttendeePackageId, attendeePackageName = attendeePackageName, attendeePackagePriceUsd = attendeePackagePriceUsd)

    fun findAcceptConflict(requestId: String): RentalBookingRequest? = bookingsRepo.findAcceptConflict(requestId = requestId)

    suspend fun acceptBookingRequest(requestId: String, agreementUrl: String? = null): Boolean = bookingsRepo.acceptBookingRequest(requestId = requestId, agreementUrl = agreementUrl)

    suspend fun rejectBookingRequest(requestId: String, note: String? = null): Boolean = bookingsRepo.rejectBookingRequest(requestId = requestId, note = note)

    suspend fun cancelBookingRequest(requestId: String): Boolean = bookingsRepo.cancelBookingRequest(requestId = requestId)

    suspend fun cancelAcceptedBooking(
        requestId: String,
        reasonCode: CancellationReasonCode,
        note: String?,
        cancelledByUid: String,
        cancelledByRole: String
    ): Boolean = bookingsRepo.cancelAcceptedBooking(requestId = requestId, reasonCode = reasonCode, note = note, cancelledByUid = cancelledByUid, cancelledByRole = cancelledByRole)

    suspend fun acknowledgePayment(requestId: String, asHost: Boolean): Boolean = bookingsRepo.acknowledgePayment(requestId = requestId, asHost = asHost)

    suspend fun addBlackoutSlot(spaceId: String, slot: BlackoutSlot): Boolean = bookingsRepo.addBlackoutSlot(spaceId = spaceId, slot = slot)

    suspend fun removeBlackoutSlot(spaceId: String, slotId: String): Boolean = bookingsRepo.removeBlackoutSlot(spaceId = spaceId, slotId = slotId)

    suspend fun addSubdivision(spaceId: String, subdivision: Subdivision): Boolean = bookingsRepo.addSubdivision(spaceId = spaceId, subdivision = subdivision)

    suspend fun removeSubdivision(spaceId: String, subdivisionId: String): Boolean = bookingsRepo.removeSubdivision(spaceId = spaceId, subdivisionId = subdivisionId)

    fun incrementSpaceViewCount(spaceId: String) = bookingsRepo.incrementSpaceViewCount(spaceId = spaceId)

    fun incrementSpaceInquiryCount(spaceId: String) = bookingsRepo.incrementSpaceInquiryCount(spaceId = spaceId)



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









    // setCurrentUser() and switchRole() were removed here — both let any caller (or,
    // for switchRole specifically, any already-logged-in user) instantly become ADMIN
    // with no server check at all. A role change now only ever happens through the
    // login()/registerMember() paths above, which require a role already verified via
    // a Firebase Auth custom claim, through the grantAdminRole Cloud Function for an
    // explicit Admin grant, or through grantEntitlement() promoting a SPECIALIST to
    // PRO_HOST the moment their Google Play subscription activates.

    // --- ProfilesRepository (moved; these delegates keep every call site unchanged) ---
    private val profilesRepo by lazy { ProfilesRepository(this) }
    suspend fun registerMember(
        uid: String,
        verifiedRole: UserRole,
        details: RegistrationDetails
    ): AppUser = profilesRepo.registerMember(uid = uid, verifiedRole = verifiedRole, details = details)

    suspend fun getUserProfile(uid: String): AppUser? = profilesRepo.getUserProfile(uid = uid)

    suspend fun login(uid: String, email: String, verifiedRole: UserRole): AppUser = profilesRepo.login(uid = uid, email = email, verifiedRole = verifiedRole)

    suspend fun logout(clearRemotePushToken: Boolean = true) = profilesRepo.logout(clearRemotePushToken = clearRemotePushToken)

    fun discardIncompleteSession() = profilesRepo.discardIncompleteSession()

    suspend fun updateCurrentUserProfile(
        name: String,
        specialty: String,
        phone: String,
        country: String,
        governorate: String,
        city: String,
        profilePictureUrl: String? = null
    ): Boolean = profilesRepo.updateCurrentUserProfile(name = name, specialty = specialty, phone = phone, country = country, governorate = governorate, city = city, profilePictureUrl = profilePictureUrl)

    suspend fun updatePhoneAfterKycLink(e164Phone: String): Boolean = profilesRepo.updatePhoneAfterKycLink(e164Phone = e164Phone)

    suspend fun toggleSavedSpace(spaceId: String): Boolean = profilesRepo.toggleSavedSpace(spaceId = spaceId)

    suspend fun registerFcmToken(uid: String, token: String): Boolean = profilesRepo.registerFcmToken(uid = uid, token = token)

    suspend fun updateAddress(country: String, city: String): Boolean = profilesRepo.updateAddress(country = country, city = city)

    suspend fun updateProfilePicture(profilePictureUrl: String): Boolean = profilesRepo.updateProfilePicture(profilePictureUrl = profilePictureUrl)

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
