package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.R
import com.example.data.model.BookingRequestStatus
import com.example.data.model.SpaceListing
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.util.UiMessages
import com.example.ui.viewmodel.ProHostViewModel
import kotlinx.coroutines.launch

/**
 * The one way a host edits a published listing — the Create listing wizard in edit mode, opened from My Listings (Edit)
 * and from the Manage page. Saves straight to the listing (status kept) and passes the listing's live bookings so the
 * wizard never deletes or reshapes a division someone has booked.
 */
@Composable
fun EditListingDialog(space: SpaceListing, viewModel: ProHostViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsState()
    val schema by viewModel.spaceArchitectureSchema.collectAsState()
    val topHashtags by viewModel.topHashtags.collectAsState()
    val allBookings by viewModel.bookingRequests.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshTopHashtags() }
    val facilities = remember(schema) {
        schema.facilities.filter { it.isEnabled }.map { it.name }.ifEmpty { FacilityCatalog.standard }
    }
    CreateListingDialog(
        currentUser = currentUser,
        existingDraft = space,
        suggestedHashtags = topHashtags,
        availableFacilities = facilities,
        spaceCategories = schema.spaceTypes,
        availableAmenities = schema.amenities.filter { it.isEnabled },
        attendeeTemplates = schema.attendeePackages.filter { it.isEnabled },
        activeBookings = activeBookingsFor(space.id, allBookings),
        onAddCustomSchemaItem = { category, name, scopedToIds -> viewModel.addUserSuggestedSchemaItem(category, name, scopedToIds) },
        onDismiss = onDone,
        onListingCreated = {},
        onListingUpdated = { updated ->
            coroutineScope.launch {
                val success = viewModel.updateOwnerListing(updated)
                onDone()
                UiMessages.show(
                    context,
                    if (success) context.getString(R.string.listing_updated_successfully) else context.getString(R.string.failed_to_update_listing_please_try_again)
                )
            }
        }
    )
}

/** Pending and accepted bookings of a listing whose term hasn't ended — the ones a listing edit must not break. */
fun activeBookingsFor(spaceId: String, bookings: List<com.example.data.model.BookingRequest>) = bookings.filter {
    it.spaceId == spaceId &&
        (it.status == BookingRequestStatus.PENDING || it.status == BookingRequestStatus.ACCEPTED) &&
        !SpaceCalculationUtils.hasEnded(it)
}
