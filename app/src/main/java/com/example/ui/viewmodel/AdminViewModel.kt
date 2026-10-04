package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.FirebaseFunctionsClient
import com.example.data.model.*
import com.example.data.repository.ProHostRepository
import com.example.ui.state.AdminUiEvent
import com.example.ui.state.AdminUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * ViewModel managing Admin console operations, user directory governance, listings CRUD,
 * dynamic space architecture schema modifications, and multi-format exports.
 */
class AdminViewModel(
    private val repository: ProHostRepository = ProHostRepository.getInstance()
) : ViewModel() {

    private val functionsClient = FirebaseFunctionsClient()

    /** Admin lists load a page at a time (see FirestoreService.ADMIN_PAGE). */
    val hasMoreRows: StateFlow<Boolean> = repository.hasMoreAdminRows
    fun loadMoreRows() = repository.loadMoreAdminRows()

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AdminUiEvent>()
    val events: SharedFlow<AdminUiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                repository.pricingState.collect { pricing ->
                    _uiState.update { it.copy(pricingState = pricing) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
        // One-shot fetch (not a live listener) — hashtag popularity changes slowly
        // enough that a snapshot from whenever the Admin Console tab was opened is
        // accurate enough for this analytics view. refreshHashtagAnalytics() below
        // can be called again to re-fetch on demand.
        refreshHashtagAnalytics()
        refreshLegalDocuments()
        viewModelScope.launch {
            try {
                repository.spaces.collect { spaces ->
                    _uiState.update { it.copy(allSpaces = spaces) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
        viewModelScope.launch {
            try {
                repository.users.collect { users ->
                    val now = System.currentTimeMillis()
                    _uiState.update {
                        it.copy(
                            allUsers = users,
                            activeSubscriberCount = users.count { u ->
                                u.ownerPackageId != null &&
                                u.ownerPackageExpiryMillis != null &&
                                u.ownerPackageExpiryMillis > now
                            }
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
        viewModelScope.launch {
            try {
                repository.bookingRequests.collect { bookings ->
                    _uiState.update { it.copy(allBookings = bookings) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
        viewModelScope.launch {
            try {
                repository.auditLogs.collect { logs ->
                    _uiState.update { it.copy(auditLogs = logs) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
        viewModelScope.launch {
            try {
                repository.spaceArchitectureSchema.collect { schema ->
                    _uiState.update { it.copy(schema = schema) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    // --- Navigation & Pricing ---
    fun setSelectedTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
        com.example.analytics.AnalyticsTracker.screen("admin_tab_$tabIndex", "AdminConsole")
    }

    private val _grantAccess = MutableStateFlow(GrantAccessUiState())
    val grantAccess: StateFlow<GrantAccessUiState> = _grantAccess.asStateFlow()

    fun lookupUserForGrant(email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return
        _grantAccess.value = GrantAccessUiState(isLookingUp = true)
        viewModelScope.launch {
            try {
                val result = functionsClient.lookupUserForGrant(trimmed)
                _grantAccess.value = result.fold(
                    onSuccess = { GrantAccessUiState(target = it) },
                    onFailure = { GrantAccessUiState(error = it.message ?: "Lookup failed") }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _grantAccess.value = GrantAccessUiState(error = e.message ?: "Lookup failed")
            }
        }
    }

    /**
     * Admin → Packages → Force Upgrade → ProHost (the only admin billing action).
     * [targetUid] must be the UID shown to the admin by [lookupUserForGrant].
     */
    fun forceUpgrade(targetUid: String) {
        val target = _grantAccess.value.target ?: return
        if (target.uid != targetUid || _grantAccess.value.isGranting) return
        _grantAccess.update { it.copy(isGranting = true, error = null, lastGrant = null) }
        viewModelScope.launch {
          try {
            val result = functionsClient.forceProHostUpgrade(targetUid)
            com.example.analytics.AnalyticsTracker.adminAction("force_upgrade", result.isSuccess)
            result.fold(
                onSuccess = {
                    // Re-read from the server so the card shows the persisted role.
                    val refreshed = functionsClient.lookupUserForGrant(target.email).getOrNull() ?: target
                    val grant = com.example.data.auth.GrantResult(
                        role = "PRO_HOST",
                        packageId = com.example.data.billing.PlayCatalog.ADMIN_FORCED_PLAN_ID,
                        expiryMillis = com.example.data.billing.PlayCatalog.LIFETIME_EXPIRY_MILLIS,
                        restoredListings = 0
                    )
                    _grantAccess.value = GrantAccessUiState(target = refreshed, lastGrant = grant)
                    _events.emit(AdminUiEvent.ShowToast("${refreshed.fullName.ifBlank { refreshed.email }} is now a Pro Host"))
                },
                onFailure = { e ->
                    _grantAccess.update { it.copy(isGranting = false, error = com.example.util.friendlyErrorMessage(e, "Upgrade failed")) }
                }
            )
          } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
          } catch (e: Exception) {
            _grantAccess.update { it.copy(isGranting = false, error = e.message ?: "Upgrade failed") }
          }
        }
    }

    /** Re-reads every Google Play subscription now (normally daily) and runs the one-time migration. */
    fun runBillingSync() {
        viewModelScope.launch {
            try {
                val result = functionsClient.runBillingSync()
                com.example.analytics.AnalyticsTracker.adminAction("billing_sync", result.isSuccess)
                _events.emit(
                    AdminUiEvent.ShowToast(
                        result.fold(
                            onSuccess = { r ->
                                "Billing sync: ${r["checked"]} checked, ${r["changed"]} changed, ${r["failed"]} failed" +
                                    (if (r["stoppedOnConfigError"] == true) " — Play API access denied" else "")
                            },
                            onFailure = { com.example.util.friendlyErrorMessage(it, "Billing sync failed") }
                        )
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.message ?: "Billing sync failed"))
            }
        }
    }

    private val _analytics = MutableStateFlow(AdminAnalyticsUiState())
    val analytics: StateFlow<AdminAnalyticsUiState> = _analytics.asStateFlow()
    private var analyticsJob: kotlinx.coroutines.Job? = null

    fun setAnalyticsRange(fromMillis: Long?, toMillis: Long?) {
        _analytics.update { it.copy(fromMillis = fromMillis, toMillis = toMillis) }
        loadAnalytics()
    }

    fun setAnalyticsCountry(country: String?) {
        _analytics.update { it.copy(country = country) }
        loadAnalytics()
    }

    fun loadAnalytics() {
        val filters = _analytics.value
        analyticsJob?.cancel()
        _analytics.update { it.copy(isLoading = true, error = null) }
        analyticsJob = viewModelScope.launch {
            try {
                val result = functionsClient.getAdminAnalytics(filters.fromMillis, filters.toMillis, filters.country)
                _analytics.update { state ->
                    result.fold(
                        onSuccess = { state.copy(isLoading = false, data = it) },
                        onFailure = { state.copy(isLoading = false, error = it.message ?: "Couldn't load analytics") }
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _analytics.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load analytics") }
            }
        }
    }

    fun backfillDisplayCodes() {
        viewModelScope.launch {
            try {
                val result = functionsClient.backfillDisplayCodes()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        result.fold(
                            onSuccess = { c ->
                                "Codes assigned — users: ${c["users"]}, listings: ${c["listings"]}, bookings: ${c["bookings"]}"
                            },
                            onFailure = { com.example.util.friendlyErrorMessage(it, "Code backfill failed") }
                        )
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "backfillDisplayCodes failed", e)
            }
        }
    }

    fun backfillProHostUpgradeDates() {
        viewModelScope.launch {
            try {
                val result = functionsClient.backfillProHostUpgradeDates()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        result.fold(
                            onSuccess = { (updated, missing) -> "Filled $updated upgrade date(s); $missing had no record" },
                            onFailure = { it.message ?: "Backfill failed" }
                        )
                    )
                )
                if (result.isSuccess) loadAnalytics()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.message ?: "Backfill failed"))
            }
        }
    }

    fun resetGrantAccess() {
        _grantAccess.value = GrantAccessUiState()
    }

    fun updateGovernanceTag(tag: String) {
        viewModelScope.launch {
            try {
                val success = repository.updateGovernanceTag(tag)
                com.example.analytics.AnalyticsTracker.adminAction("governance_tag", success)
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "Admin governance control tag updated" else "Failed to update governance tag"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    // --- User Filtering & Governance ---
    fun setUserSearchQuery(query: String) {
        _uiState.update { it.copy(userSearchQuery = query) }
    }

    fun setSelectedUserRoleFilter(role: UserRole?) {
        _uiState.update { it.copy(selectedUserRoleFilter = role) }
    }

    fun setUserRoleFilter(role: UserRole?) {
        setSelectedUserRoleFilter(role)
    }

    fun openEditUserDialog(user: AppUser) {
        _uiState.update { it.copy(editingUser = user, isEditUserDialogOpen = true) }
    }

    fun closeEditUserDialog() {
        _uiState.update { it.copy(editingUser = null, isEditUserDialogOpen = false) }
    }

    fun saveUser(user: AppUser) {
        viewModelScope.launch {
            try {
                val success = repository.updateUser(user)
                com.example.analytics.AnalyticsTracker.adminAction("user_edit", success)
                closeEditUserDialog()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "User profile updated successfully" else "Failed to update user profile"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun openDeleteUserDialog(user: AppUser) {
        _uiState.update { it.copy(deletingUser = user, isDeleteUserDialogOpen = true) }
    }

    fun closeDeleteUserDialog() {
        _uiState.update { it.copy(deletingUser = null, isDeleteUserDialogOpen = false) }
    }

    fun confirmDeleteUser(userId: String) {
        viewModelScope.launch {
            try {
                val success = repository.deleteUser(userId)
                com.example.analytics.AnalyticsTracker.adminAction("user_delete", success)
                closeDeleteUserDialog()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "User profile removed from platform" else "Failed to remove user — please try again"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    // --- Grant Admin ---
    // The only path that can grant the ADMIN role (grantAdminRole Cloud Function, which
    // only succeeds if the caller's own token already carries role == ADMIN). Until this
    // was added, there was no in-app way to promote a second admin at all — the only
    // account with the role got it from a one-off bootstrap script.
    fun openGrantAdminDialog(user: AppUser) {
        _uiState.update { it.copy(grantingAdminUser = user, isGrantAdminDialogOpen = true) }
    }

    fun closeGrantAdminDialog() {
        _uiState.update { it.copy(grantingAdminUser = null, isGrantAdminDialogOpen = false) }
    }

    fun confirmGrantAdmin(email: String) {
        viewModelScope.launch {
            try {
                val result = functionsClient.grantAdminRole(email)
                com.example.analytics.AnalyticsTracker.adminAction("grant_admin", result.isSuccess)
                closeGrantAdminDialog()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (result.isSuccess) "Admin role granted to $email" else "Failed to grant Admin role"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    // --- Suspend / Reactivate Account ---
    // The gap between "exists" and "deleted" Admin never had before — suspending
    // blocks sign-in (assignInitialRole.ts) and new listings/booking requests
    // (firestore.rules' isSuspended()) without erasing the account's data.
    fun openSuspendUserDialog(user: AppUser) {
        _uiState.update { it.copy(suspendingUser = user, isSuspendUserDialogOpen = true) }
    }

    fun closeSuspendUserDialog() {
        _uiState.update { it.copy(suspendingUser = null, isSuspendUserDialogOpen = false) }
    }

    fun confirmToggleSuspend() {
        val user = _uiState.value.suspendingUser ?: return
        val newSuspended = !user.isSuspended
        viewModelScope.launch {
            try {
                val result = functionsClient.setAccountSuspended(user.id, newSuspended)
                com.example.analytics.AnalyticsTracker.adminAction("suspend_toggle", result.isSuccess)
                closeSuspendUserDialog()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (result.isSuccess) {
                            if (newSuspended) "${user.fullName} suspended" else "${user.fullName} reactivated"
                        } else {
                            "Could not ${if (newSuspended) "suspend" else "reactivate"} ${user.fullName}"
                        }
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    // --- Revoke Pro Host Role ---
    // The downgrade path PRO_HOST never had — see revokeProHostRole.ts's doc comment.
    // Only offered for accounts that currently hold PRO_HOST (the Cloud Function itself
    // also enforces this, but there's no reason to show the action otherwise).
    fun openRevokeProHostDialog(user: AppUser) {
        _uiState.update { it.copy(revokingProHostUser = user, isRevokeProHostDialogOpen = true) }
    }

    fun closeRevokeProHostDialog() {
        _uiState.update { it.copy(revokingProHostUser = null, isRevokeProHostDialogOpen = false) }
    }

    fun confirmRevokeProHost() {
        val user = _uiState.value.revokingProHostUser ?: return
        viewModelScope.launch {
            try {
                val result = functionsClient.revokeProHostRole(user.id)
                com.example.analytics.AnalyticsTracker.adminAction("revoke_pro_host", result.isSuccess)
                closeRevokeProHostDialog()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (result.isSuccess) "${user.fullName} downgraded to Specialist; their listings were deactivated"
                        else "Could not revoke Pro Host role for ${user.fullName}"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    // --- Listings Filtering & Governance ---
    fun setListingSearchQuery(query: String) {
        _uiState.update { it.copy(listingSearchQuery = query) }
    }

    fun setSelectedListingTypeFilter(categoryId: String?) {
        _uiState.update { it.copy(selectedListingTypeFilter = categoryId) }
    }

    fun setListingTypeFilter(categoryId: String?) {
        setSelectedListingTypeFilter(categoryId)
    }

    fun setSelectedListingStatusFilter(status: String) {
        _uiState.update { it.copy(selectedListingStatusFilter = status) }
    }

    fun setListingStatusFilter(status: String) {
        setSelectedListingStatusFilter(status)
    }

    fun openEditListingDialog(space: SpaceListing) {
        _uiState.update { it.copy(editingListing = space, isEditListingDialogOpen = true) }
    }

    fun closeEditListingDialog() {
        _uiState.update { it.copy(editingListing = null, isEditListingDialogOpen = false) }
    }

    fun saveListing(listing: SpaceListing) {
        viewModelScope.launch {
            try {
                val success = repository.updateSpaceListing(listing)
                com.example.analytics.AnalyticsTracker.adminAction("listing_edit", success)
                closeEditListingDialog()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "Listing updated successfully" else "Failed to update listing"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun toggleListingVerification(spaceId: String, currentVerified: Boolean = false) {
        viewModelScope.launch {
            try {
                val success = repository.toggleListingVerification(spaceId)
                com.example.analytics.AnalyticsTracker.adminAction("listing_verify_toggle", success)
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "Verification status toggled for space" else "Failed to toggle verification status"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    /**
     * Used to manually mutate isActiveSubscription/subscriptionExpiryMillis and
     * call the generic (and, before Phase 7 extended, never-persisted)
     * updateSpaceListing — bypassing setListingSubscriptionActive entirely, and
     * fabricating a +30-day expiry that had nothing to do with any real
     * subscription term. Firestore rules now deny a direct write to either
     * field anyway, so this goes through the Cloud Function like
     * toggleListingVerification does.
     */
    fun toggleListingSubscription(spaceId: String, currentActive: Boolean = false) {
        viewModelScope.launch {
            try {
                val success = repository.toggleListingActive(spaceId)
                com.example.analytics.AnalyticsTracker.adminAction("listing_active_toggle", success)
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "Listing subscription active status toggled" else "Failed to toggle subscription status"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun openDeleteListingDialog(space: SpaceListing) {
        _uiState.update { it.copy(deletingListing = space, isDeleteListingDialogOpen = true) }
    }

    fun closeDeleteListingDialog() {
        _uiState.update { it.copy(deletingListing = null, isDeleteListingDialogOpen = false) }
    }

    fun confirmDeleteListing(spaceId: String) {
        viewModelScope.launch {
            try {
                val success = repository.deleteSpaceListing(spaceId)
                com.example.analytics.AnalyticsTracker.adminAction("listing_delete", success)
                closeDeleteListingDialog()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "Listing permanently removed from catalog" else "Failed to remove listing — please try again"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    // --- Dynamic Space Architecture Schema Management ---
    fun setSelectedSchemaCategoryFilter(category: String) {
        _uiState.update { it.copy(selectedSchemaCategoryFilter = category) }
    }

    fun toggleSchemaItemEnabled(itemId: String, category: String = "", currentEnabled: Boolean = false) {
        viewModelScope.launch {
            try {
                val success = repository.toggleSchemaItem(itemId)
                com.example.analytics.AnalyticsTracker.adminAction("schema_toggle", success)
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "Schema item status updated" else "Failed to update schema item — please try again"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun deleteSchemaItem(itemId: String, category: String = "") {
        viewModelScope.launch {
            try {
                val success = repository.deleteSchemaItem(itemId)
                com.example.analytics.AnalyticsTracker.adminAction("schema_delete", success)
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "Schema item deleted from database registry" else "Failed to delete schema item — please try again"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun openAddSchemaItemDialog(presetCategory: String? = null) {
        _uiState.update { it.copy(isAddSchemaItemDialogOpen = true, addSchemaItemPresetCategory = presetCategory) }
    }

    fun closeAddSchemaItemDialog() {
        _uiState.update { it.copy(isAddSchemaItemDialogOpen = false, addSchemaItemPresetCategory = null) }
    }

    fun refreshHashtagAnalytics() {
        viewModelScope.launch {
            try {
                val entries = repository.fetchHashtagAnalytics()
                _uiState.update { it.copy(hashtagAnalytics = entries) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun refreshLegalDocuments() {
        viewModelScope.launch {
            try {
                val docIds = LegalDocumentVersion.ADMIN_MANAGED_DOC_IDS + LegalDocumentVersion.RERENTAL_TEMPLATE_DOC_ID
                val versions = docIds.associateWith { docId ->
                    repository.getLatestLegalDocumentVersion(docId)
                }
                _uiState.update { it.copy(legalDocuments = versions) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    /**
     * Uploads [fileUri] (an .html file the admin picked) as the new current version
     * of legal document [docId] — one of LegalDocumentVersion.ADMIN_MANAGED_DOC_IDS.
     * Resolves the next version number from the currently-published one (read fresh
     * here rather than trusting uiState.legalDocuments, in case another admin session
     * published a version since this screen last refreshed), uploads to Storage at
     * legal_documents/{docId}/v{nextVersion}.html, then records it in Firestore —
     * see FirebaseStorageService.uploadLegalDocumentVersion / ProHostRepository.
     * publishLegalDocumentVersion for why each version is a permanent, never-
     * overwritten object rather than an in-place replace.
     */
    fun uploadLegalDocument(
        docId: String,
        fileUri: android.net.Uri,
        fileName: String?,
        adminEmail: String,
        contentType: String = "text/html",
        fileExtension: String = "html"
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isUploadingLegalDocument = docId) }
                val current = repository.getLatestLegalDocumentVersion(docId)
                val nextVersion = (current?.version ?: 0) + 1
                val storageService = com.example.data.storage.FirebaseStorageService.getInstance()
                val url = storageService.uploadLegalDocumentVersion(docId, nextVersion, fileUri, contentType, fileExtension)
                val success = if (url != null) {
                    repository.publishLegalDocumentVersion(
                        docId,
                        LegalDocumentVersion(
                            version = nextVersion,
                            url = url,
                            fileName = fileName,
                            uploadedAtMillis = System.currentTimeMillis(),
                            uploadedByEmail = adminEmail,
                            contentType = contentType
                        )
                    )
                } else {
                    false
                }
                _uiState.update { it.copy(isUploadingLegalDocument = null) }
                if (success) refreshLegalDocuments()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "Published $docId v$nextVersion" else "Upload failed — check your connection and try again"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploadingLegalDocument = null) }
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Upload failed — check your connection and try again"))
            }
        }
    }

    fun addSchemaItem(
        category: String,
        name: String,
        description: String = "",
        iconName: String = "Category",
        maxSubdivisions: Int? = null,
        scopedToIds: List<String> = emptyList(),
        amenityGroup: String = "",
        markerColor: String? = null
    ) {
        addNewSchemaItem(category, name, description, iconName, maxSubdivisions, scopedToIds, amenityGroup, markerColor)
    }

    fun seedDefaultAmenities() {
        val currentAmenities = _uiState.value.schema.amenities
        if (currentAmenities.isNotEmpty()) return
        val defaults = listOf(
            "A/C Climate Control",
            "Dual-Monitor Setup",
            "Whiteboard / Presentation Kit",
            "High-Speed Wi-Fi",
            "Soundproofing",
            "Ergonomic Seating",
            "Storage Locker",
            "Keyless Access Control",
            "Privacy Partition",
            "Natural Lighting",
            "Standing Desk"
        )
        defaults.forEach { name ->
            addNewSchemaItem(
                category = SchemaCategory.AMENITY,
                name = name,
                description = "",
                iconName = "Star"
            )
        }
    }

    fun addNewSchemaItem(
        category: String,
        name: String,
        description: String,
        iconName: String,
        maxSubdivisions: Int? = null,
        scopedToIds: List<String> = emptyList(),
        amenityGroup: String = "",
        markerColor: String? = null
    ) {
        viewModelScope.launch {
            try {
                val newItem = SchemaItem(
                    id = "SCH-" + category.take(3) + "-" + UUID.randomUUID().toString().take(6).uppercase(),
                    name = name,
                    description = description,
                    category = category,
                    iconName = iconName,
                    isEnabled = true,
                    isSystemDefault = false,
                    maxSubdivisions = maxSubdivisions,
                    scopedToIds = scopedToIds,
                    amenityGroup = amenityGroup,
                    markerColor = markerColor?.takeIf { it.isNotBlank() }
                )
                val success = repository.addSchemaItem(newItem)
                com.example.analytics.AnalyticsTracker.adminAction("schema_add", success)
                closeAddSchemaItemDialog()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "New schema entry added: $name" else "Failed to add schema entry — please try again"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun updateSchemaItem(item: SchemaItem) {
        viewModelScope.launch {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    ?.getIdToken(true)?.await()
                val success = repository.updateSchemaItem(item)
                com.example.analytics.AnalyticsTracker.adminAction("schema_edit", success)
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "\"${item.name}\" updated" else "Update failed — please try again"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ->
                        "Permission denied. Please sign out and sign back in to refresh your admin session."
                    e.message?.contains("UNAVAILABLE", ignoreCase = true) == true ->
                        "No network connection — please try again."
                    else -> e.localizedMessage ?: "Operation failed"
                }
                _events.emit(AdminUiEvent.ShowToast(msg))
            }
        }
    }

    /** Buffer-locally-commit-on-Save editor for an EXISTING SchemaItem's max-subdivisions
     * cap (task #106's pattern) — used to only ever be settable once, at creation, via
     * AddSchemaItemDialog; the Schema tab needs to edit it in place for every SPACE_TYPE
     * category, old and new alike. Per-category PAYG pricing used to live alongside this
     * same field — removed with PAYG; prices now come only from Google Play. */
    fun updateSchemaItemMaxSubdivisions(itemId: String, category: String, maxSubdivisions: Int?) {
        viewModelScope.launch {
            try {
                val success = repository.updateSchemaItemMaxSubdivisions(itemId, category, maxSubdivisions)
                com.example.analytics.AnalyticsTracker.adminAction("schema_max_subdivisions", success)
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "Max subdivisions updated" else "Failed to update max subdivisions — please try again"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun addAttendeePackage(
        name: String,
        description: String,
        priceUsd: Double,
        inclusions: List<String>,
        minAttendees: Int,
        maxAttendees: Int?
    ) {
        viewModelScope.launch {
            try {
                val pkg = AttendeePackage(
                    id = "APK-" + java.util.UUID.randomUUID().toString().take(6).uppercase(),
                    name = name.trim(),
                    description = description.trim(),
                    pricePerAttendeeUsd = priceUsd,
                    inclusions = inclusions,
                    minAttendees = minAttendees,
                    maxAttendees = maxAttendees,
                    isSystemDefault = false
                )
                val success = repository.addAttendeePackage(pkg)
                _events.emit(AdminUiEvent.ShowToast(if (success) "Package \"${pkg.name}\" added" else "Failed to add package"))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun updateAttendeePackage(pkg: AttendeePackage) {
        viewModelScope.launch {
            try {
                val success = repository.updateAttendeePackage(pkg)
                _events.emit(AdminUiEvent.ShowToast(if (success) "\"${pkg.name}\" updated" else "Update failed"))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun deleteAttendeePackage(pkgId: String) {
        viewModelScope.launch {
            try {
                val success = repository.deleteAttendeePackage(pkgId)
                _events.emit(AdminUiEvent.ShowToast(if (success) "Package deleted" else "Delete failed"))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun toggleAttendeePackage(pkgId: String) {
        viewModelScope.launch {
            try {
                repository.toggleAttendeePackage(pkgId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun openResetSchemaDialog() {
        _uiState.update { it.copy(isResetSchemaDialogOpen = true) }
    }

    fun closeResetSchemaDialog() {
        _uiState.update { it.copy(isResetSchemaDialogOpen = false) }
    }

    fun confirmResetSchema() {
        confirmResetSchemaToDefaults()
    }

    fun addMissingDefaultSchemaItems() {
        viewModelScope.launch {
            try {
                val added = repository.addMissingDefaultSchemaItems()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        when (added) {
                            null -> "Failed to update the schema — please try again"
                            0 -> "Nothing missing — every built-in entry is already in the schema"
                            else -> "Added $added missing built-in entr${if (added == 1) "y" else "ies"}. Existing entries were not changed."
                        }
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun confirmResetSchemaToDefaults() {
        viewModelScope.launch {
            try {
                val success = repository.resetSchemaToDefaults()
                closeResetSchemaDialog()
                _events.emit(
                    AdminUiEvent.ShowToast(
                        if (success) "Database schema architecture reset to Lebanese defaults" else "Failed to reset schema — please try again"
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun seedDemoContent() {
        viewModelScope.launch {
            if (!com.example.BuildConfig.DEBUG) {
                _events.emit(AdminUiEvent.ShowToast("Demo content can only be generated from a debug build."))
                return@launch
            }
            try {
                val success = repository.seedDemoContent()
                if (success) {
                    _events.emit(AdminUiEvent.ShowToast("Successfully generated legit demo listings, fake requests, and demo users!"))
                } else {
                    _events.emit(AdminUiEvent.ShowToast("Failed to seed demo content"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun purgeDemoContent() {
        viewModelScope.launch {
            try {
                val purged = repository.purgeDemoContent()
                _events.emit(AdminUiEvent.ShowToast("Successfully purged all demo content ($purged items removed)"))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    // --- Multi-Format Data Exports ---
    fun openExportDialog(title: String, content: String, format: String = "CSV") {
        _uiState.update {
            it.copy(
                isExportDialogOpen = true,
                exportDataTitle = title,
                exportDataContent = content,
                activeExportFormat = format
            )
        }
        viewModelScope.launch {
            try {
                _events.emit(AdminUiEvent.DataExportReady(title, content))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(AdminUiEvent.ShowToast(e.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun closeExportDialog() {
        _uiState.update { it.copy(isExportDialogOpen = false, exportDataContent = "") }
    }

    /** Returns the enriched CSV (with spending, listings, tenants) ready to share as a file. */
    fun getUsersContactSheetCsv(): String = repository.exportUsersToCsv()

    fun exportListingsCatalog(format: String = "CSV") {
        val content = if (format == "JSON") repository.exportListingsToJson() else repository.exportListingsToCsv()
        openExportDialog("ProHost Workspace Listings Catalog (${format})", content, format)
    }

    /** Used by the Admin Console's own Security & Audit tab export button. The same
     * CSV generator was previously only reachable via the unrelated drawer "Central
     * Security Audits" dialog, which called straight into ProHostViewModel.repository —
     * this gives the Admin Console tab its own proper entry point instead of reaching
     * around AdminViewModel. */
    fun exportAuditLogsCsv(startDateMillis: Long?, endDateMillis: Long?): String {
        return repository.exportAuditLogsToCsv(startDateMillis, endDateMillis)
    }

    /** "One-Click System Exports" content getters — the buttons write these to a real
     * file (rememberFileExportLauncher) rather than opening the clipboard/share-only
     * AdminExportDataDialog every other export button on this screen still uses. */
    fun getFullAuditReport(): String = repository.exportToAuditText()
    fun getMasterJsonExport(): String = repository.exportToJson()

}

data class GrantAccessUiState(
    val isLookingUp: Boolean = false,
    val target: com.example.data.auth.GrantLookupResult? = null,
    val isGranting: Boolean = false,
    val lastGrant: com.example.data.auth.GrantResult? = null,
    val error: String? = null
)

data class AdminAnalyticsUiState(
    val fromMillis: Long? = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000,
    val toMillis: Long? = System.currentTimeMillis(),
    /** Null = all countries. */
    val country: String? = null,
    val isLoading: Boolean = false,
    val data: com.example.data.auth.AdminAnalytics? = null,
    val error: String? = null
)
