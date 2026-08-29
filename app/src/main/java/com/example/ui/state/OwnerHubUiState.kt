package com.example.ui.state

import com.example.data.model.*

/**
 * Immutable UI State for the Owner Hub & Listing Management.
 */
data class OwnerHubUiState(
    val ownerSpaces: List<SpaceListing> = emptyList(),
    val incomingRequests: List<RentalBookingRequest> = emptyList(),
    val avatarCampaigns: List<AvatarCampaign> = emptyList(),
    val selectedSpaceForSchedule: SpaceListing? = null,
    val selectedSpaceForAvatar: SpaceListing? = null,
    val isCreateListingOpen: Boolean = false,
    val isScheduleEditorOpen: Boolean = false,
    val isAvatarStudioOpen: Boolean = false,
    val isExportDialogOpen: Boolean = false,
    val isIncomingRequestsOpen: Boolean = false,
    val isLoading: Boolean = false,
    val totalEarningsUsd: Double = 0.0,
    val activeOccupancyRate: Double = 0.0
)

/**
 * Events from Owner Hub actions.
 */
sealed interface OwnerHubUiEvent {
    data class ShowToast(val message: String) : OwnerHubUiEvent
    data class LaunchWhatsApp(val url: String) : OwnerHubUiEvent
    data class ListingCreated(val listing: SpaceListing) : OwnerHubUiEvent
    data class RequestStatusUpdated(val requestId: String, val status: BookingRequestStatus) : OwnerHubUiEvent
}
