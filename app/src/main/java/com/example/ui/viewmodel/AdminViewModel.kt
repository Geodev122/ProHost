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
        // MRR depends on FOUR independent live sources (listings, pricing, the schema's
        // per-category prices, and each owner's package tier) that resolve at different
        // times on cold start — recomputing only from the `spaces` listener (as this
        // used to) meant whichever of the other three hadn't arrived yet at that moment
        // silently fed stale/default values into the calculation, with no later
        // recompute once they did. This is exactly why Active/Capacity MRR could show a
        // different number on every login — same real listings, different pricing/tier
        // data available by the time the (single) recompute happened to fire. combine()
        // re-runs the calculation whenever ANY of the four changes, so it's always
        // computed from the latest of all of them.
        viewModelScope.launch {
            combine(
                repository.spaces,
                repository.pricingState,
                repository.spaceArchitectureSchema,
                repository.users,
                repository.packagePlans
            ) { spaces, _, _, _, _ -> spaces }.collect { spaces ->
                _uiState.update {
                    it.copy(
                        allSpaces = spaces,
                        activeMrr = repository.calculateActiveMrr(),
                        potentialMrr = repository.calculatePotentialCapacityMrr(),
                        projectedArr = repository.calculateProjectedArr()
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.users.collect { users ->
                _uiState.update { it.copy(allUsers = users) }
            }
        }
        viewModelScope.launch {
            repository.transactions.collect { txs ->
                _uiState.update {
                    it.copy(
                        allTransactions = txs,
                        totalSettlementVolume = repository.calculateTotalSettlementVolume()
                    )
                }
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
    }

    fun setSubscriptionFee(fee: Double) {
        viewModelScope.launch {
            if (repository.updateMonthlySubscriptionFee(fee)) {
                _events.emit(AdminUiEvent.PricingUpdated(fee))
            } else {
                _events.emit(AdminUiEvent.ShowToast("Failed to update subscription fee"))
            }
        }
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

    fun togglePackagePlan(planId: String) {
        viewModelScope.launch {
            val success = repository.togglePackagePlan(planId)
            if (!success) {
                _events.emit(AdminUiEvent.ShowToast("Failed to toggle package"))
            }
        }
    }

    fun deletePackagePlan(planId: String) {
        viewModelScope.launch {
            val success = repository.deletePackagePlan(planId)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "Package removed" else "Failed to remove package"
                )
            )
        }
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

    fun applyPresetFee(fee: Double) {
        setSubscriptionFee(fee)
    }

    fun resetSubscriptionFeeBaseline() {
        viewModelScope.launch {
            if (repository.resetMonthlySubscriptionFee()) {
                _events.emit(AdminUiEvent.PricingUpdated(repository.pricingState.value.baselineFeeUsd))
            } else {
                _events.emit(AdminUiEvent.ShowToast("Failed to reset subscription fee"))
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

    fun addSchemaItem(
        category: String,
        name: String,
        description: String = "",
        iconName: String = "Category",
        maxSubdivisions: Int? = null
    ) {
        addNewSchemaItem(category, name, description, iconName, maxSubdivisions)
    }

    fun addNewSchemaItem(
        category: String,
        name: String,
        description: String,
        iconName: String,
        maxSubdivisions: Int? = null
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
                maxSubdivisions = maxSubdivisions
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

    /** Used by AdminRevenueScreen's own real-file export button (relocated from
     * ProHostViewModel — Admin-only functionality, no reason it lived on the shared
     * god object). */
    fun exportRevenueCsv(startDateMillis: Long?, endDateMillis: Long?): String {
        return repository.exportTransactionsToCsv(startDateMillis, endDateMillis)
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
