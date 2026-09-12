package com.example.ui.state

import com.example.data.model.*

/**
 * Immutable UI State for the Admin Governance & Audit Console.
 */
data class AdminUiState(
    val pricingState: AdminPricingState = AdminPricingState(),
    val allSpaces: List<SpaceListing> = emptyList(),
    val allUsers: List<AppUser> = emptyList(),
    val allTransactions: List<WhishTransaction> = emptyList(),
    val allBookings: List<RentalBookingRequest> = emptyList(),
    val auditLogs: List<AuditSecurityLog> = emptyList(),
    val schema: SpaceArchitectureSchema = SpaceArchitectureSchema(),
    val hashtagAnalytics: List<HashtagUsageEntry> = emptyList(),
    val activeMrr: Double = 0.0,
    val potentialMrr: Double = 0.0,
    val projectedArr: Double = 0.0,
    val totalSettlementVolume: Double = 0.0,
    val selectedTab: Int = 0,

    // Filter and search states
    val userSearchQuery: String = "",
    val selectedUserRoleFilter: UserRole? = null,

    val listingSearchQuery: String = "",
    // A SchemaItem.id from the admin-defined Space Category catalog (or a legacy
    // SpaceType name) — see SpaceListing.matchesCategory.
    val selectedListingTypeFilter: String? = null,
    val selectedListingStatusFilter: String = "ALL", // "ALL", "ACTIVE_30D", "EXPIRED", "VERIFIED"

    val txSearchQuery: String = "",
    val selectedTxStatusFilter: String = "ALL", // "ALL", "SUCCESS", "PENDING", "FAILED"

    val selectedSchemaCategoryFilter: String = "ALL", // "ALL", "SPACE_TYPE", "SUBCATEGORY", "AMENITY", "EQUIPMENT", "SPECIALTY", "RENTAL_STRATEGY"

    // Dialog and Modal states
    val isExportDialogOpen: Boolean = false,
    val exportDataContent: String = "",
    val exportDataTitle: String = "Export ProHost Data",
    val activeExportFormat: String = "CSV",

    val isEditListingDialogOpen: Boolean = false,
    val editingListing: SpaceListing? = null,

    val isDeleteListingDialogOpen: Boolean = false,
    val deletingListing: SpaceListing? = null,

    val isEditUserDialogOpen: Boolean = false,
    val editingUser: AppUser? = null,

    val isDeleteUserDialogOpen: Boolean = false,
    val deletingUser: AppUser? = null,

    val isGrantAdminDialogOpen: Boolean = false,
    val grantingAdminUser: AppUser? = null,

    val isSuspendUserDialogOpen: Boolean = false,
    val suspendingUser: AppUser? = null,

    val isRevokeProHostDialogOpen: Boolean = false,
    val revokingProHostUser: AppUser? = null,

    val isAddSchemaItemDialogOpen: Boolean = false,
    val isResetSchemaDialogOpen: Boolean = false
) {
    val filteredUsers: List<AppUser>
        get() = allUsers.filter { user ->
            val matchesQuery = userSearchQuery.isBlank() ||
                    user.fullName.contains(userSearchQuery, ignoreCase = true) ||
                    user.email.contains(userSearchQuery, ignoreCase = true) ||
                    user.specialty.contains(userSearchQuery, ignoreCase = true) ||
                    user.phone.contains(userSearchQuery, ignoreCase = true) ||
                    user.city.contains(userSearchQuery, ignoreCase = true) ||
                    user.governorate.contains(userSearchQuery, ignoreCase = true) ||
                    user.country.contains(userSearchQuery, ignoreCase = true)

            val matchesRole = selectedUserRoleFilter == null || user.role == selectedUserRoleFilter
            matchesQuery && matchesRole
        }

    val filteredSpaces: List<SpaceListing>
        get() = allSpaces.filter { space ->
            val matchesQuery = listingSearchQuery.isBlank() ||
                    space.title.contains(listingSearchQuery, ignoreCase = true) ||
                    space.district.contains(listingSearchQuery, ignoreCase = true) ||
                    space.streetAddress.contains(listingSearchQuery, ignoreCase = true) ||
                    space.ownerName.contains(listingSearchQuery, ignoreCase = true) ||
                    space.ownerPhone.contains(listingSearchQuery, ignoreCase = true) ||
                    space.governorate.displayName.contains(listingSearchQuery, ignoreCase = true)

            val matchesType = space.matchesCategory(selectedListingTypeFilter)
            val matchesStatus = when (selectedListingStatusFilter) {
                "ACTIVE_30D" -> space.isActiveSubscription
                "EXPIRED" -> !space.isActiveSubscription
                "VERIFIED" -> space.isVerified
                else -> true
            }
            matchesQuery && matchesType && matchesStatus
        }

    val filteredTransactions: List<WhishTransaction>
        get() = allTransactions.filter { tx ->
            val matchesQuery = txSearchQuery.isBlank() ||
                    tx.id.contains(txSearchQuery, ignoreCase = true) ||
                    tx.orderId.contains(txSearchQuery, ignoreCase = true) ||
                    tx.payerName.contains(txSearchQuery, ignoreCase = true) ||
                    tx.payerPhone.contains(txSearchQuery, ignoreCase = true) ||
                    tx.spaceId.contains(txSearchQuery, ignoreCase = true)

            val matchesStatus = when (selectedTxStatusFilter) {
                "SUCCESS" -> tx.status == TransactionStatus.SUCCESS
                "PENDING" -> tx.status == TransactionStatus.PENDING
                "FAILED" -> tx.status == TransactionStatus.FAILED
                else -> true
            }
            matchesQuery && matchesStatus
        }

    // "Owner"/"Host" here means the account holds the PRO_HOST role (i.e. has listed
    // at least one workspace) — every PRO_HOST account is still also fundamentally a
    // Specialist underneath (can book workspaces exactly like a SPECIALIST account;
    // see AdminConsoleScreen's "Workspace Hosts & Property Ownership" section, which
    // this backs), not a separate, mutually-exclusive user category.
    val ownerUsers: List<AppUser>
        get() = allUsers.filter { it.role == UserRole.PRO_HOST }
}

/**
 * Events for Admin actions.
 */
sealed interface AdminUiEvent {
    data class ShowToast(val message: String) : AdminUiEvent
    data class PricingUpdated(val newFee: Double) : AdminUiEvent
    data class DataExportReady(val title: String, val content: String) : AdminUiEvent
}

