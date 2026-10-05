package com.example.ui.state

import com.example.data.auth.AdminCounts
import com.example.data.auth.AdminUserDossier
import com.example.data.auth.BillingHealth
import com.example.data.auth.PendingPayment
import com.example.data.model.*

/**
 * Immutable UI State for the Admin Governance & Audit Console.
 */
data class AdminUiState(
    val pricingState: AdminPricingState = AdminPricingState(),
    // The console loads nothing in bulk: real totals come from adminCounts, rows from the
    // server search (adminDirectory.ts), plus the small live verification review queue.
    val counts: AdminCounts? = null,
    val userResults: List<AppUser> = emptyList(),
    val listingResults: List<SpaceListing> = emptyList(),
    val bookingResults: List<RentalBookingRequest> = emptyList(),
    val reviewQueue: List<SpaceListing> = emptyList(),
    val isSearchingUsers: Boolean = false,
    val isSearchingListings: Boolean = false,
    val searchError: String? = null,
    val dossier: AdminUserDossier? = null,
    val isLoadingDossier: Boolean = false,
    val isExportingUsers: Boolean = false,
    // Billing rescue (Admin › Packages): Play API health, paid purchases not active yet,
    // and the "Activate for this user" flow (account picker + reassignment confirmation).
    val billingHealth: BillingHealth? = null,
    val pendingPayments: List<PendingPayment> = emptyList(),
    val isCheckingBilling: Boolean = false,
    val assigningPayment: PendingPayment? = null,
    val assignCandidates: List<AppUser> = emptyList(),
    val reassignConfirm: ReassignConfirm? = null,
    val activatingPaymentId: String? = null,
    val auditLogs: List<AuditSecurityLog> = emptyList(),
    val schema: SpaceArchitectureSchema = SpaceArchitectureSchema(),
    val hashtagAnalytics: List<HashtagUsageEntry> = emptyList(),
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

    // Currently-published version of each of the 3 admin-manageable legal documents
    // (LegalDocumentVersion.ADMIN_MANAGED_DOC_IDS), keyed by doc id — null means
    // nothing has ever been uploaded for that doc yet. Loaded once on ViewModel init
    // and refreshed after every successful upload (see AdminViewModel.
    // refreshLegalDocuments/uploadLegalDocument) — not a live listener, since this
    // content changes rarely and a fresh one-shot read on demand is plenty.
    val legalDocuments: Map<String, LegalDocumentVersion?> = emptyMap(),
    val isUploadingLegalDocument: String? = null, // the docId currently mid-upload, if any
) {
    /** Users search results narrowed by the role chip. */
    val filteredUsers: List<AppUser>
        get() = userResults.filter { selectedUserRoleFilter == null || it.role == selectedUserRoleFilter }

    /** Listings: search results, or the verification review queue when nothing is searched. */
    val filteredSpaces: List<SpaceListing>
        get() = (if (listingSearchQuery.isBlank()) reviewQueue else listingResults).filter { space ->
            val matchesType = space.matchesCategory(selectedListingTypeFilter)
            val matchesStatus = when (selectedListingStatusFilter) {
                "ACTIVE_30D" -> space.isActiveSubscription
                "EXPIRED" -> !space.isActiveSubscription
                "VERIFIED" -> space.isVerified
                "PENDING_VERIFICATION" -> !space.verificationDocUrl.isNullOrBlank() && !space.isVerified
                else -> true
            }
            matchesType && matchesStatus
        }
}

/**
 * Events for Admin actions.
 */
sealed interface AdminUiEvent {
    data class ShowToast(val message: String) : AdminUiEvent
    data class PricingUpdated(val newFee: Double) : AdminUiEvent
    data class DataExportReady(val title: String, val content: String) : AdminUiEvent
}

/** "This purchase was made for [taggedForName] — activate it for [targetName] instead?" */
data class ReassignConfirm(
    val payment: PendingPayment,
    val targetUid: String,
    val targetName: String,
    val taggedForName: String
)
