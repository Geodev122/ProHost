package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ProSpaceRepository
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
    private val repository: ProSpaceRepository = ProSpaceRepository.getInstance()
) : ViewModel() {

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
        viewModelScope.launch {
            repository.spaces.collect { spaces ->
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
    }

    // --- Navigation & Pricing ---
    fun setSelectedTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun setSubscriptionFee(fee: Double) {
        viewModelScope.launch {
            repository.updateMonthlySubscriptionFee(fee)
            _events.emit(AdminUiEvent.PricingUpdated(fee))
        }
    }

    fun updatePaygFee(spaceType: SpaceType, fee: Double) {
        viewModelScope.launch {
            repository.updatePaygFee(spaceType, fee)
            _events.emit(AdminUiEvent.ShowToast("PAYG fee updated for ${spaceType.displayName}"))
        }
    }

    fun updatePackageFees(package2Fee: Double, package3Fee: Double) {
        viewModelScope.launch {
            repository.updatePackageFees(package2Fee, package3Fee)
            _events.emit(AdminUiEvent.ShowToast("Owner package pricing updated successfully"))
        }
    }

    fun updateGovernanceTag(tag: String) {
        viewModelScope.launch {
            repository.updateGovernanceTag(tag)
            _events.emit(AdminUiEvent.ShowToast("Admin governance control tag updated"))
        }
    }

    fun applyPresetFee(fee: Double) {
        setSubscriptionFee(fee)
    }

    fun resetSubscriptionFeeBaseline() {
        viewModelScope.launch {
            repository.resetMonthlySubscriptionFee()
            _events.emit(AdminUiEvent.PricingUpdated(repository.pricingState.value.baselineFeeUsd))
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

    fun setSelectedUserStatusFilter(status: MemberVerificationStatus?) {
        _uiState.update { it.copy(selectedUserStatusFilter = status) }
    }

    fun setUserStatusFilter(status: MemberVerificationStatus?) {
        setSelectedUserStatusFilter(status)
    }

    fun openEditUserDialog(user: AppUser) {
        _uiState.update { it.copy(editingUser = user, isEditUserDialogOpen = true) }
    }

    fun closeEditUserDialog() {
        _uiState.update { it.copy(editingUser = null, isEditUserDialogOpen = false) }
    }

    fun saveUser(user: AppUser) {
        viewModelScope.launch {
            repository.updateUser(user)
            closeEditUserDialog()
            _events.emit(AdminUiEvent.ShowToast("User profile updated successfully"))
        }
    }

    fun saveUserEdits(
        userId: String,
        fullName: String,
        role: UserRole,
        specialty: String,
        syndicateNumber: String,
        affiliation: String,
        phone: String,
        verificationStatus: MemberVerificationStatus,
        verificationTier: VerificationTier,
        trustScore: Int,
        verificationNotes: String?
    ) {
        val existing = _uiState.value.allUsers.find { it.id == userId }
        val updated = (existing ?: AppUser(
            id = userId,
            email = "",
            fullName = fullName,
            role = role,
            specialty = specialty,
            phone = phone,
            affiliation = affiliation,
            syndicateNumber = syndicateNumber
        )).copy(
            fullName = fullName,
            role = role,
            specialty = specialty,
            syndicateNumber = syndicateNumber,
            affiliation = affiliation,
            phone = phone,
            isVerified = verificationStatus == MemberVerificationStatus.VERIFIED,
            verificationStatus = verificationStatus,
            verificationTier = verificationTier,
            trustScore = trustScore,
            verificationNotes = verificationNotes
        )
        saveUser(updated)
    }

    fun toggleUserVerification(userId: String) {
        viewModelScope.launch {
            val success = repository.toggleUserVerification(userId)
            _events.emit(
                AdminUiEvent.ShowToast(
                    if (success) "User verification status updated" else "Failed to update verification status"
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
            repository.deleteUser(userId)
            closeDeleteUserDialog()
            _events.emit(AdminUiEvent.ShowToast("User account removed from platform"))
        }
    }

    // --- Listings Filtering & Governance ---
    fun setListingSearchQuery(query: String) {
        _uiState.update { it.copy(listingSearchQuery = query) }
    }

    fun setSelectedListingTypeFilter(type: SpaceType?) {
        _uiState.update { it.copy(selectedListingTypeFilter = type) }
    }

    fun setListingTypeFilter(type: SpaceType?) {
        setSelectedListingTypeFilter(type)
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
            repository.updateSpaceListing(listing)
            closeEditListingDialog()
            _events.emit(AdminUiEvent.ShowToast("Listing updated successfully"))
        }
    }

    fun saveListingEdits(
        spaceId: String,
        title: String,
        spaceType: SpaceType,
        governorate: Governorate,
        district: String,
        streetAddress: String,
        floorInfo: String,
        baseMonthlyRateUsd: Double,
        isVerified: Boolean,
        isActiveSubscription: Boolean,
        ownerName: String,
        ownerPhone: String,
        ownerEmail: String
    ) {
        val existing = _uiState.value.allSpaces.find { it.id == spaceId }
        val updated = (existing ?: SpaceListing(
            id = spaceId,
            title = title,
            spaceType = spaceType,
            governorate = governorate,
            district = district,
            streetAddress = streetAddress,
            floorInfo = floorInfo,
            lat = 33.8938,
            lng = 35.5018,
            isShared = false,
            complementarySpecialties = emptyList(),
            residentPractitioners = emptyList(),
            essentialFacilities = emptyList(),
            equipment = emptyList(),
            rentalFormulas = emptyList(),
            rules = PremisesRules(),
            ownerId = "",
            ownerName = ownerName,
            ownerPhone = ownerPhone,
            ownerEmail = ownerEmail
        )).copy(
            title = title,
            spaceType = spaceType,
            governorate = governorate,
            district = district,
            streetAddress = streetAddress,
            floorInfo = floorInfo,
            baseMonthlyRateUsd = baseMonthlyRateUsd,
            isVerified = isVerified,
            isActiveSubscription = isActiveSubscription,
            ownerName = ownerName,
            ownerPhone = ownerPhone,
            ownerEmail = ownerEmail
        )
        saveListing(updated)
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
            repository.deleteSpaceListing(spaceId)
            closeDeleteListingDialog()
            _events.emit(AdminUiEvent.ShowToast("Listing permanently removed from catalog"))
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

    fun simulateManualWhishSync() {
        viewModelScope.launch {
            repository.addAuditLog(
                actionType = "WHISH_MANUAL_RECONCILIATION",
                details = "Admin initiated manual ledger sync with Whish Money gateway API (Channel 15462415)",
                severity = "INFO"
            )
            _events.emit(AdminUiEvent.ShowToast("Whish Money transactions synchronized successfully"))
        }
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
            repository.toggleSchemaItem(itemId)
            _events.emit(AdminUiEvent.ShowToast("Schema item status updated"))
        }
    }

    fun deleteSchemaItem(itemId: String, category: String = "") {
        viewModelScope.launch {
            repository.deleteSchemaItem(itemId)
            _events.emit(AdminUiEvent.ShowToast("Schema item deleted from database registry"))
        }
    }

    fun openAddSchemaItemDialog() {
        _uiState.update { it.copy(isAddSchemaItemDialogOpen = true) }
    }

    fun closeAddSchemaItemDialog() {
        _uiState.update { it.copy(isAddSchemaItemDialogOpen = false) }
    }

    fun addSchemaItem(
        category: String,
        name: String,
        description: String = "",
        iconName: String = "Category"
    ) {
        addNewSchemaItem(category, name, description, iconName)
    }

    fun addNewSchemaItem(
        category: String,
        name: String,
        description: String,
        iconName: String
    ) {
        viewModelScope.launch {
            val newItem = SchemaItem(
                id = "SCH-" + category.take(3) + "-" + UUID.randomUUID().toString().take(6).uppercase(),
                name = name,
                description = description,
                category = category,
                iconName = iconName,
                isEnabled = true,
                isSystemDefault = false
            )
            repository.addSchemaItem(newItem)
            closeAddSchemaItemDialog()
            _events.emit(AdminUiEvent.ShowToast("New schema entry added: $name"))
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
            repository.resetSchemaToDefaults()
            closeResetSchemaDialog()
            _events.emit(AdminUiEvent.ShowToast("Database schema architecture reset to Lebanese defaults"))
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

    fun exportAllAuditReport() {
        val content = repository.exportToAuditText()
        openExportDialog("Full ProSpace Audit & Revenue Report", content, "TXT")
    }

    fun exportAllCsv() {
        val content = repository.exportToCsv()
        openExportDialog("Complete ProSpace Master Ledger (CSV)", content, "CSV")
    }

    fun exportAllJson() {
        val content = repository.exportToJson()
        openExportDialog("ProSpace Platform JSON Export", content, "JSON")
    }

    fun exportUsersDirectory(format: String = "CSV") {
        val content = if (format == "JSON") repository.exportUsersToJson() else repository.exportUsersToCsv()
        openExportDialog("ProSpace Registered Users Directory (${format})", content, format)
    }

    fun exportListingsCatalog(format: String = "CSV") {
        val content = if (format == "JSON") repository.exportListingsToJson() else repository.exportListingsToCsv()
        openExportDialog("ProSpace Workspace Listings Catalog (${format})", content, format)
    }

    fun exportTransactionsLedger() {
        val content = repository.exportTransactionsToCsv()
        openExportDialog("Whish Pay Transactions Ledger (CSV)", content, "CSV")
    }

    fun exportOwnerRegistrations() {
        val content = repository.exportOwnerRegistrationsToCsv()
        openExportDialog("Workspace Hosts & Property Ownership Audit (CSV)", content, "CSV")
    }
}
