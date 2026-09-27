package com.example.ui.state

import com.example.data.model.*

/**
 * Immutable UI State for the Admin Governance & Audit Console.
 */
data class AdminUiState(
    val pricingState: AdminPricingState = AdminPricingState(),
    val allSpaces: List<SpaceListing> = emptyList(),
    val allUsers: List<AppUser> = emptyList(),
    val allBookings: List<RentalBookingRequest> = emptyList(),
    val auditLogs: List<AuditSecurityLog> = emptyList(),
    val schema: SpaceArchitectureSchema = SpaceArchitectureSchema(),
    val hashtagAnalytics: List<HashtagUsageEntry> = emptyList(),
    /** Number of users with an active Google Play subscription. Wired up in AdminViewModel. */
    val activeSubscriberCount: Int = 0,
    val selectedTab: Int = 0,

    // Filter and search states
    val userSearchQuery: String = "",
    val selectedUserRoleFilter: UserRole? = null,

    val listingSearchQuery: String = "",
    // A SchemaItem.id from the admin-defined Space Category catalog (or a legacy
    // SpaceType name) — see SpaceListing.matchesCategory.
    val selectedListingTypeFilter: String? = null,
    val selectedListingStatusFilter: String = "ALL", // "ALL", "ACTIVE_30D", "EXPIRED", "VERIFIED", "PENDING_VERIFICATION"


    val selectedSchemaCategoryFilter: String = "ALL", // retained for potential use but God Schema tab no longer uses a flat filter

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
    // Lets a category-specific "Add" button (Renting Formulas section, Package
    // Configuration's "Add Category Type") open the same dialog pre-selected on the
    // right category, instead of every "Add" entry point defaulting to the same one.
    val addSchemaItemPresetCategory: String? = null,
    val isResetSchemaDialogOpen: Boolean = false,

    // Admin-managed, purchasable Pro Host packages — replaces the old fixed
    // Package 2/3 fee+limit inputs entirely (see PackagePlan/PackagePlanCatalog).
    val packagePlans: PackagePlanCatalog = PackagePlanCatalog(),
    val isAddPackagePlanDialogOpen: Boolean = false,

    // Delete-package confirmation dialog — subscriber-aware (BUG-C3)
    val isDeletePackagePlanDialogOpen: Boolean = false,
    val pendingDeletePlanId: String? = null,
    val pendingDeletePlanSubscriberCount: Int = 0,

    // Currently-published version of each of the 3 admin-manageable legal documents
    // (LegalDocumentVersion.ADMIN_MANAGED_DOC_IDS), keyed by doc id — null means
    // nothing has ever been uploaded for that doc yet. Loaded once on ViewModel init
    // and refreshed after every successful upload (see AdminViewModel.
    // refreshLegalDocuments/uploadLegalDocument) — not a live listener, since this
    // content changes rarely and a fresh one-shot read on demand is plenty.
    val legalDocuments: Map<String, LegalDocumentVersion?> = emptyMap(),
    val isUploadingLegalDocument: String? = null, // the docId currently mid-upload, if any
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
                "PENDING_VERIFICATION" -> !space.verificationDocUrl.isNullOrBlank() && !space.isVerified
                else -> true
            }
            matchesQuery && matchesType && matchesStatus
        }

    // "Owner"/"Host" here means the account holds the PRO_HOST role (i.e. has listed
    // at least one workspace) — every PRO_HOST account is still also fundamentally a
    // Specialist underneath (can book workspaces exactly like a SPECIALIST account),
    // not a separate, mutually-exclusive user category.
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

