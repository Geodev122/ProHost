package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ProSpaceRepository
import com.example.ui.state.OwnerHubUiEvent
import com.example.ui.state.OwnerHubUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing Space Owner operations: listing management, schedule editor, booking request approvals, and AI Avatar marketing.
 */
class OwnerHubViewModel(
    private val repository: ProSpaceRepository = ProSpaceRepository.getInstance()
) : ViewModel() {

    private val _selectedSpaceForSchedule = MutableStateFlow<SpaceListing?>(null)
    private val _selectedSpaceForAvatar = MutableStateFlow<SpaceListing?>(null)
    private val _isCreateListingOpen = MutableStateFlow(false)
    private val _isScheduleEditorOpen = MutableStateFlow(false)
    private val _isAvatarStudioOpen = MutableStateFlow(false)
    private val _isExportDialogOpen = MutableStateFlow(false)
    private val _isIncomingRequestsOpen = MutableStateFlow(false)

    private val _events = MutableSharedFlow<OwnerHubUiEvent>()
    val events: SharedFlow<OwnerHubUiEvent> = _events.asSharedFlow()

    private val _dialogState = combine(
        _selectedSpaceForSchedule,
        _selectedSpaceForAvatar,
        _isCreateListingOpen,
        _isScheduleEditorOpen,
        _isAvatarStudioOpen,
        _isExportDialogOpen,
        _isIncomingRequestsOpen
    ) { args: Array<Any?> ->
        Tuple7(
            args[0] as? SpaceListing,
            args[1] as? SpaceListing,
            args[2] as Boolean,
            args[3] as Boolean,
            args[4] as Boolean,
            args[5] as Boolean,
            args[6] as Boolean
        )
    }

    val uiState: StateFlow<OwnerHubUiState> = combine(
        repository.spaces,
        repository.currentUser,
        repository.bookingRequests,
        repository.avatarCampaigns,
        _dialogState
    ) { spaces: List<SpaceListing>, user: AppUser?, requests: List<RentalBookingRequest>, campaigns: List<AvatarCampaign>, dialogs: Tuple7<SpaceListing?, SpaceListing?, Boolean, Boolean, Boolean, Boolean, Boolean> ->
        val (spaceForSchedule, spaceForAvatar, createOpen, scheduleOpen, avatarOpen, exportOpen, incomingOpen) = dialogs

        val ownerSpaces = if (user == null) emptyList()
        else spaces.filter {
            it.ownerName.contains(user.fullName, ignoreCase = true) ||
            it.ownerPhone == user.phone ||
            user.role == UserRole.ADMIN
        }

        val mySpaceIds = ownerSpaces.map { it.id }.toSet()
        val incomingRequests = if (user == null) emptyList()
        else if (user.role == UserRole.ADMIN) requests
        else requests.filter { it.ownerId == user.id || mySpaceIds.contains(it.spaceId) }

        val totalEarnings = incomingRequests
            .filter { it.status == BookingRequestStatus.ACCEPTED || it.isExternalPaymentSettled }
            .sumOf { it.totalAmountUsd }

        val occupancyRate = if (ownerSpaces.isEmpty()) 0.0 else {
            val bookedCount = incomingRequests.count { it.status == BookingRequestStatus.ACCEPTED }
            (bookedCount.toDouble() / (ownerSpaces.size * 3).coerceAtLeast(1)).coerceIn(0.0, 1.0) * 100
        }

        OwnerHubUiState(
            ownerSpaces = ownerSpaces,
            incomingRequests = incomingRequests,
            avatarCampaigns = campaigns,
            selectedSpaceForSchedule = spaceForSchedule,
            selectedSpaceForAvatar = spaceForAvatar,
            isCreateListingOpen = createOpen,
            isScheduleEditorOpen = scheduleOpen,
            isAvatarStudioOpen = avatarOpen,
            isExportDialogOpen = exportOpen,
            isIncomingRequestsOpen = incomingOpen,
            totalEarningsUsd = totalEarnings,
            activeOccupancyRate = occupancyRate,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OwnerHubUiState())

    fun openCreateListing(isOpen: Boolean) {
        _isCreateListingOpen.value = isOpen
    }

    fun openScheduleEditor(space: SpaceListing?) {
        _selectedSpaceForSchedule.value = space
        _isScheduleEditorOpen.value = space != null
    }

    fun openAvatarStudio(space: SpaceListing?) {
        _selectedSpaceForAvatar.value = space
        _isAvatarStudioOpen.value = space != null
    }

    fun openExportDialog(isOpen: Boolean) {
        _isExportDialogOpen.value = isOpen
    }

    fun openIncomingRequests(isOpen: Boolean) {
        _isIncomingRequestsOpen.value = isOpen
    }

    fun acceptBookingRequest(requestId: String) {
        viewModelScope.launch {
            repository.acceptBookingRequest(requestId)
            _events.emit(OwnerHubUiEvent.RequestStatusUpdated(requestId, BookingRequestStatus.ACCEPTED))
        }
    }

    fun rejectBookingRequest(requestId: String, note: String? = null) {
        viewModelScope.launch {
            repository.rejectBookingRequest(requestId, note)
            _events.emit(OwnerHubUiEvent.RequestStatusUpdated(requestId, BookingRequestStatus.REJECTED))
        }
    }

    fun addListing(listing: SpaceListing) {
        viewModelScope.launch {
            repository.addSpaceListing(listing)
            _isCreateListingOpen.value = false
            _events.emit(OwnerHubUiEvent.ListingCreated(listing))
        }
    }

    fun generateAvatarCampaign(space: SpaceListing) {
        viewModelScope.launch {
            repository.generateAvatarCampaign(space)
            _events.emit(OwnerHubUiEvent.ShowToast("AI Avatar Campaign Generated for ${space.title}!"))
        }
    }
}

private data class Tuple7<A, B, C, D, E, F, G>(
    val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G
)
