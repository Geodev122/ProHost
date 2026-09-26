package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.FirebaseFunctionsClient
import com.example.data.model.*
import com.example.data.repository.ProHostRepository
import com.example.ui.state.AdminUiEvent
import com.example.ui.state.AdminUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel managing Admin console operations, user directory governance, listings CRUD,
 * Whish Money audit monitoring, dynamic space architecture schema modifications, and multi-format exports.
 */
class AdminViewModel(
    private val repository: ProHostRepository = ProHostRepository.getInstance()
) : ViewModel() {

    private val functionsClient = FirebaseFunctionsClient()

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AdminUiEvent>()
    val events: SharedFlow<AdminUiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.pricingState.collect { pricing ->
                _uiState.update { it.copy(pricingState = pricing) }
            }
        }
        // One-shot fetch (not a live listener) — hashtag popularity changes slowly
        // enough that a snapshot from whenever the Admin Console tab was opened is
        // accurate enough for this analytics view. refreshHashtagAnalytics() below
        // can be called again to re-fetch on demand.
        refreshHashtagAnalytics()
        refreshLegalDocuments()
        viewModelScope.launch {
            repository.spaces.collect { spaces ->
                _uiState.update { it.copy(allSpaces = spaces) }
            }
        }
        viewModelScope.launch {
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
        }
        viewModelScope.launch {
            repository.transactions.collect { txs ->
                _uiState.update { it.copy(allTransactions = txs) }
            }
        }
        viewModelScope.launch {
            repository.bookingRequests.collect { bookings ->
                _uiState.update { it.copy(allBookings = bookings) }
            }
        }
        viewModelScope.launch {
            repository.auditLogs.collect { logs ->
                _uiState.update { it.copy(auditLogs = logs) }
            }
        }
        viewModelScope.launch {
            repository.spaceArchitectureSchema.collect { schema ->
                _uiState.update { it.copy(schema = schema) }
            }
        }
        viewModelScope.launch {
            repository.packagePlans.collect { plans ->
                _uiState.update { it.copy(packagePlans = plans) }
            }
        }
    }

    // --- Navigation & Pricing ---
    fun setSelectedTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
        if (tabIndex == 7) loadIdReviewQueue()
    }

    fun addPackagePlan(plan: PackagePlan) {
        viewModelScope.launch {
            val success = repository.addPackagePlan(plan)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "Package '${plan.name}' added" else "Failed to add package"
                )
            )
            if (success) closeAddPackagePlanDialog()
        }
    }

    fun updatePackagePlan(plan: PackagePlan) {
        viewModelScope.launch {
            val success = repository.updatePackagePlan(plan)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "Package '${plan.name}' updated" else "Failed to update package"
                )
            )
        }
    }

    fun grantPackageToUser(targetUserId: String, packageId: String, durationDays: Int) {
        viewModelScope.launch {
            val result = functionsClient.grantPackageToUser(targetUserId, packageId, durationDays)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (result.isSuccess) "Package granted to user successfully"
                    else "Failed to grant package: ${result.exceptionOrNull()?.message}"
                )
            )
        }
    }

    fun togglePackagePlan(planId: String) {
        viewModelScope.launch {
            val success = repository.togglePackagePlan(planId)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) {
                        val isNowEnabled = _uiState.value.packagePlans.packages[planId]?.isEnabled == true
                        "Package ${if (isNowEnabled) "enabled" else "disabled"}"
                    } else "Failed to toggle package"
                )
            )
        }
    }

    fun deletePackagePlan(planId: String) {
        val now = System.currentTimeMillis()
        val activeSubscriberCount = _uiState.value.allUsers.count { u ->
            u.ownerPackageId == planId &&
            u.ownerPackageExpiryMillis != null &&
            u.ownerPackageExpiryMillis > now
        }
        _uiState.update {
            it.copy(
                pendingDeletePlanId = planId,
                pendingDeletePlanSubscriberCount = activeSubscriberCount,
                isDeletePackagePlanDialogOpen = true
            )
        }
    }

    fun confirmDeletePackagePlan() {
        val planId = _uiState.value.pendingDeletePlanId ?: return
        _uiState.update { it.copy(isDeletePackagePlanDialogOpen = false, pendingDeletePlanId = null, pendingDeletePlanSubscriberCount = 0) }
        viewModelScope.launch {
            val success = repository.deletePackagePlan(planId)
            _events.emit(AdminUiEvent.ShowToast(if (success) "Package removed" else "Failed to remove package"))
        }
    }

    fun cancelDeletePackagePlan() {
        _uiState.update { it.copy(isDeletePackagePlanDialogOpen = false, pendingDeletePlanId = null, pendingDeletePlanSubscriberCount = 0) }
    }

    fun openAddPackagePlanDialog() {
        _uiState.update { it.copy(isAddPackagePlanDialogOpen = true) }
    }

    fun closeAddPackagePlanDialog() {
        _uiState.update { it.copy(isAddPackagePlanDialogOpen = false) }
    }


    fun updateGovernanceTag(tag: String) {
        viewModelScope.launch {
            val success = repository.updateGovernanceTag(tag)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "Admin governance control tag updated" else "Failed to update governance tag"
                )
            )
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
            val success = repository.updateUser(user)
            closeEditUserDialog()
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "User profile updated successfully" else "Failed to update user profile"
                )
            )
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
            val success = repository.deleteUser(userId)
            closeDeleteUserDialog()
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "User profile removed from platform" else "Failed to remove user — please try again"
                )
            )
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
            val result = functionsClient.grantAdminRole(email)
            closeGrantAdminDialog()
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (result.isSuccess) "Admin role granted to $email" else "Failed to grant Admin role"
                )
            )
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
            val result = functionsClient.setAccountSuspended(user.id, newSuspended)
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
            val result = functionsClient.revokeProHostRole(user.id)
            closeRevokeProHostDialog()
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (result.isSuccess) "${user.fullName} downgraded to Specialist; their listings were deactivated"
                    else "Could not revoke Pro Host role for ${user.fullName}"
                )
            )
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
            val success = repository.updateSpaceListing(listing)
            closeEditListingDialog()
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "Listing updated successfully" else "Failed to update listing"
                )
            )
        }
    }

    fun toggleListingVerification(spaceId: String, currentVerified: Boolean = false) {
        viewModelScope.launch {
            val success = repository.toggleListingVerification(spaceId)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "Verification status toggled for space" else "Failed to toggle verification status"
                )
            )
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
            val success = repository.toggleListingActive(spaceId)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "Listing subscription active status toggled" else "Failed to toggle subscription status"
                )
            )
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
            val success = repository.deleteSpaceListing(spaceId)
            closeDeleteListingDialog()
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "Listing permanently removed from catalog" else "Failed to remove listing — please try again"
                )
            )
        }
    }

    // --- Whish Money & Transaction Audit Filtering ---
    fun setTxSearchQuery(query: String) {
        _uiState.update { it.copy(txSearchQuery = query) }
    }

    fun setSelectedTxStatusFilter(status: String) {
        _uiState.update { it.copy(selectedTxStatusFilter = status) }
    }

    fun setTxStatusFilter(status: String) {
        setSelectedTxStatusFilter(status)
    }

    // --- Dynamic Space Architecture Schema Management ---
    fun setSelectedSchemaCategoryFilter(category: String) {
        _uiState.update { it.copy(selectedSchemaCategoryFilter = category) }
    }

    fun setSchemaCategoryFilter(category: String) {
        setSelectedSchemaCategoryFilter(category)
    }

    fun toggleSchemaItemEnabled(itemId: String, category: String = "", currentEnabled: Boolean = false) {
        viewModelScope.launch {
            val success = repository.toggleSchemaItem(itemId)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "Schema item status updated" else "Failed to update schema item — please try again"
                )
            )
        }
    }

    fun deleteSchemaItem(itemId: String, category: String = "") {
        viewModelScope.launch {
            val success = repository.deleteSchemaItem(itemId)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "Schema item deleted from database registry" else "Failed to delete schema item — please try again"
                )
            )
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
            val entries = repository.fetchHashtagAnalytics()
            _uiState.update { it.copy(hashtagAnalytics = entries) }
        }
    }

    fun refreshLegalDocuments() {
        viewModelScope.launch {
            val docIds = LegalDocumentVersion.ADMIN_MANAGED_DOC_IDS + LegalDocumentVersion.RERENTAL_TEMPLATE_DOC_ID
            val versions = docIds.associateWith { docId ->
                repository.getLatestLegalDocumentVersion(docId)
            }
            _uiState.update { it.copy(legalDocuments = versions) }
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
        }
    }

    fun addSchemaItem(
        category: String,
        name: String,
        description: String = "",
        iconName: String = "Category",
        maxSubdivisions: Int? = null,
        scopedToIds: List<String> = emptyList(),
        amenityGroup: String = ""
    ) {
        addNewSchemaItem(category, name, description, iconName, maxSubdivisions, scopedToIds, amenityGroup)
    }

    fun addNewSchemaItem(
        category: String,
        name: String,
        description: String,
        iconName: String,
        maxSubdivisions: Int? = null,
        scopedToIds: List<String> = emptyList(),
        amenityGroup: String = ""
    ) {
        viewModelScope.launch {
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
                amenityGroup = amenityGroup
            )
            val success = repository.addSchemaItem(newItem)
            closeAddSchemaItemDialog()
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "New schema entry added: $name" else "Failed to add schema entry — please try again"
                )
            )
        }
    }

    fun updateSchemaItem(item: SchemaItem) {
        viewModelScope.launch {
            val success = repository.updateSchemaItem(item)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "\"${item.name}\" updated" else "Update failed — please try again"
                )
            )
        }
    }

    /** Buffer-locally-commit-on-Save editor for an EXISTING SchemaItem's max-subdivisions
     * cap (task #106's pattern) — used to only ever be settable once, at creation, via
     * AddSchemaItemDialog; the Schema tab needs to edit it in place for every SPACE_TYPE
     * category, old and new alike. Per-category PAYG pricing used to live alongside this
     * same field — removed with PAYG; package pricing now lives on PackagePlan instead. */
    fun updateSchemaItemMaxSubdivisions(itemId: String, category: String, maxSubdivisions: Int?) {
        viewModelScope.launch {
            val success = repository.updateSchemaItemMaxSubdivisions(itemId, category, maxSubdivisions)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "Max subdivisions updated" else "Failed to update max subdivisions — please try again"
                )
            )
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
        }
    }

    fun updateAttendeePackage(pkg: AttendeePackage) {
        viewModelScope.launch {
            val success = repository.updateAttendeePackage(pkg)
            _events.emit(AdminUiEvent.ShowToast(if (success) "\"${pkg.name}\" updated" else "Update failed"))
        }
    }

    fun deleteAttendeePackage(pkgId: String) {
        viewModelScope.launch {
            val success = repository.deleteAttendeePackage(pkgId)
            _events.emit(AdminUiEvent.ShowToast(if (success) "Package deleted" else "Delete failed"))
        }
    }

    fun toggleAttendeePackage(pkgId: String) {
        viewModelScope.launch {
            repository.toggleAttendeePackage(pkgId)
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

    fun confirmResetSchemaToDefaults() {
        viewModelScope.launch {
            val success = repository.resetSchemaToDefaults()
            closeResetSchemaDialog()
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "Database schema architecture reset to Lebanese defaults" else "Failed to reset schema — please try again"
                )
            )
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
            _events.emit(AdminUiEvent.DataExportReady(title, content))
        }
    }

    fun closeExportDialog() {
        _uiState.update { it.copy(isExportDialogOpen = false, exportDataContent = "") }
    }

    fun exportUsersDirectory(format: String = "CSV") {
        val content = if (format == "JSON") repository.exportUsersToJson() else repository.exportUsersToCsv()
        openExportDialog("ProHost Registered Users Directory (${format})", content, format)
    }

    fun exportListingsCatalog(format: String = "CSV") {
        val content = if (format == "JSON") repository.exportListingsToJson() else repository.exportListingsToCsv()
        openExportDialog("ProHost Workspace Listings Catalog (${format})", content, format)
    }

    fun exportTransactionsLedger() {
        val content = repository.exportTransactionsToCsv()
        openExportDialog("Whish Pay Transactions Ledger (CSV)", content, "CSV")
    }

    fun exportOwnerRegistrations() {
        val content = repository.exportOwnerRegistrationsToCsv()
        openExportDialog("Workspace Hosts & Property Ownership Audit (CSV)", content, "CSV")
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

    // ── ID Document Review Queue (tab 7) ─────────────────────────────────────

    fun loadIdReviewQueue() {
        viewModelScope.launch {
            _uiState.update { it.copy(isIdReviewLoading = true) }
            val result = repository.loadIdReviewQueue()
            _uiState.update { it.copy(
                idReviewQueue = result,
                isIdReviewLoading = false
            ) }
        }
    }

    fun approveIdDocument(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(idReviewDecisionInProgress = userId) }
            val result = functionsClient.reviewIdDocument(userId, "APPROVED")
            _uiState.update { it.copy(idReviewDecisionInProgress = null) }
            if (result.isSuccess) {
                loadIdReviewQueue()
                _events.emit(AdminUiEvent.ShowToast("ID document approved — user notified."))
            } else {
                _events.emit(AdminUiEvent.ShowToast("Failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun openRejectIdDialog(userId: String) {
        _uiState.update { it.copy(isRejectIdDialogOpen = true, rejectingIdUserId = userId) }
    }

    fun closeRejectIdDialog() {
        _uiState.update { it.copy(isRejectIdDialogOpen = false, rejectingIdUserId = null) }
    }

    fun rejectIdDocument(userId: String, reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(idReviewDecisionInProgress = userId, isRejectIdDialogOpen = false) }
            val result = functionsClient.reviewIdDocument(userId, "REJECTED", reason.takeIf { it.isNotBlank() })
            _uiState.update { it.copy(idReviewDecisionInProgress = null, rejectingIdUserId = null) }
            if (result.isSuccess) {
                loadIdReviewQueue()
                _events.emit(AdminUiEvent.ShowToast("ID document rejected — user notified."))
            } else {
                _events.emit(AdminUiEvent.ShowToast("Failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }
}
