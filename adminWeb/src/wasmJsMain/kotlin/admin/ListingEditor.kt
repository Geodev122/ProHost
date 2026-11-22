package admin

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.data.gateway.BridgedStorageGateway
import com.example.data.model.AppUser
import com.example.data.model.SpaceArchitectureSchema
import com.example.data.model.SpaceListing
import com.example.data.model.UserRole
import com.example.data.repository.createDefaultSchema
import com.example.data.repository.RepositoryDefaults
import com.example.ui.components.CreateListingDialog
import com.example.web.WebSeams
import kotlinx.coroutines.launch

/**
 * ADMIN_PARITY item 37: Edit listing with the app's own wizard (`CreateListingDialog` in its admin-edit mode, as
 * `AdminConsoleScreen` opens it): the full listing document is read, the wizard saves through `onListingUpdated`, and the
 * write is the Android one (`ProHostRepository.updateSpaceListing`: server-owned fields kept from the stored listing, then
 * `toFirestoreMap()` merged into `workspace_listings/{id}`, audit `LISTING_UPDATED`). Photos upload through the shared
 * `BridgedStorageGateway` to the app's paths. Listed difference: the wizard acts as the listing's owner (name, phone, photo
 * stay the host's), where the Android admin edit wrote the admin's own name over them.
 */
@Composable
fun ListingEditDialog(gateway: AdminGateway, listingId: String, onDone: (String?) -> Unit) {
  var listing by remember { mutableStateOf<SpaceListing?>(null) }
  var schema by remember { mutableStateOf<SpaceArchitectureSchema?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(listingId) {
    try {
      WebSeams.install()
      RepositoryDefaults.storage = { BridgedStorageGateway(gateway.storageBridge()) }
      @Suppress("UNCHECKED_CAST")
      val doc = gateway.readDoc("workspace_listings/$listingId")?.let { Json.parse(it).toKotlin() as? Map<String, Any?> }
      if (doc == null) { error = "This listing no longer exists."; return@LaunchedEffect }
      @Suppress("UNCHECKED_CAST")
      schema = gateway.readDoc("schema_architecture/main")?.let { SpaceArchitectureSchema.fromFirestoreMap(Json.parse(it).toKotlin() as? Map<String, Any?> ?: emptyMap()) }
        ?: createDefaultSchema()
      listing = SpaceListing.fromFirestoreMap(listingId, doc)
    } catch (e: Throwable) {
      error = "Couldn't open the listing: ${e.message}"
    }
  }

  error?.let { message ->
    AlertDialog(
      onDismissRequest = { onDone(null) },
      title = { Text("Edit listing") },
      text = { Text(message) },
      confirmButton = { TextButton(onClick = { onDone(null) }) { Text("Close") } },
    )
    return
  }
  val current = listing ?: return
  val s = schema ?: return
  val owner = AppUser(
    id = current.ownerId, email = "", fullName = current.ownerName, role = UserRole.PRO_HOST, specialty = "",
    phone = current.ownerPhone, profilePictureUrl = current.ownerProfilePictureUrl,
  )
  CreateListingDialog(
    currentUser = owner,
    existingDraft = current,
    onDismiss = { onDone(null) },
    onListingCreated = {},
    onListingUpdated = { updated ->
      scope.launch {
        val safe = updated.copy(
          ownerId = current.ownerId,
          isVerified = current.isVerified,
          isActiveSubscription = current.isActiveSubscription,
          isOwnerSuspended = current.isOwnerSuspended,
          isOwnerPackageLapsed = current.isOwnerPackageLapsed,
          ownerProfilePictureUrl = current.ownerProfilePictureUrl,
        )
        val result = try {
          gateway.mergeDoc("workspace_listings/${safe.id}", toJsonText(safe.toFirestoreMap()))
          audit(gateway, "LISTING_UPDATED", "Admin updated workspace listing #${safe.id} (${safe.title})", "INFO")
          "Listing updated successfully"
        } catch (e: Throwable) {
          "Failed to update listing: ${e.message}"
        }
        onDone(result)
      }
    },
    spaceCategories = s.spaceTypes,
    availableAmenities = s.amenities.filter { it.isEnabled },
  )
}
