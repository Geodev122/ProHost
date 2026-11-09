package admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.FreshGreen
import com.example.ui.theme.Spacing
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusOnErrorContainer
import com.example.ui.theme.StatusWarningContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ADMIN_PARITY items 29-36, 38, 39 (Listings & Bookings): totals, search (listings + bookings), category and status chips,
 * the "Awaiting verification review" queue, listing cards with verification document / Activate / Verify / Delete / Host profile,
 * booking cards with renter and host profiles. Not yet: Edit (the full listing wizard, item 37, waits for the shared owner screens, P6-W5)
 * and the price on the card (needs the shared pricing port, P5); both are tracked in ADMIN_PARITY.md.
 */
private val LEGACY_CATEGORIES = listOf("PRIVATE_OFFICE" to "Private Office", "CENTER" to "Center", "POLYCLINIC" to "Polyclinic", "COWORKING_SPACE" to "Co-working Space")
private val STATUS_FILTERS = listOf("ALL" to "All", "ACTIVE_30D" to "Active", "EXPIRED" to "Expired", "VERIFIED" to "Verified", "PENDING_VERIFICATION" to "Pending review")

/** The four original categories keep matching old listings (ST-01..04 or the bare enum names), as `legacySpaceTypeForCategoryId` does on Android. */
private fun legacyType(categoryId: String?): String? = when (categoryId) {
  "ST-01", "PRIVATE_OFFICE" -> "PRIVATE_OFFICE"
  "ST-02", "CENTER" -> "CENTER"
  "ST-03", "POLYCLINIC" -> "POLYCLINIC"
  "ST-04", "COWORKING_SPACE" -> "COWORKING_SPACE"
  else -> null
}

private fun matchesCategory(space: Json, categoryId: String?): Boolean {
  if (categoryId == null) return true
  val own = space["spaceCategoryId"].str().ifEmpty { null }
  if (own == categoryId) return true
  val legacy = legacyType(categoryId) ?: return false
  return if (own != null) legacyType(own) == legacy else space["spaceType"].str() == legacy
}

private fun matchesStatus(space: Json, filter: String): Boolean = when (filter) {
  "ACTIVE_30D" -> space["isActiveSubscription"].bool()
  "EXPIRED" -> !space["isActiveSubscription"].bool()
  "VERIFIED" -> space["isVerified"].bool()
  "PENDING_VERIFICATION" -> space["verificationDocUrl"].str().isNotBlank() && !space["isVerified"].bool()
  else -> true
}

@Composable
fun ListingsTab(gateway: AdminGateway) {
  var counts by remember { mutableStateOf<Json?>(null) }
  var query by remember { mutableStateOf("") }
  var category by remember { mutableStateOf<String?>(null) }
  var status by remember { mutableStateOf("ALL") }
  var categories by remember { mutableStateOf(LEGACY_CATEGORIES) }
  var queue by remember { mutableStateOf<List<Json>>(emptyList()) }
  var listings by remember { mutableStateOf<List<Json>>(emptyList()) }
  var bookings by remember { mutableStateOf<List<Json>>(emptyList()) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var notice by remember { mutableStateOf<String?>(null) }
  var refresh by remember { mutableStateOf(0) }
  var deleting by remember { mutableStateOf<Json?>(null) }
  var profileUid by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(refresh) {
    counts = try { Json.parse(gateway.call("adminCounts")) } catch (_: Throwable) { null }
    queue = try { Json.parse(gateway.reviewQueue()).list() } catch (e: Throwable) { error = "Couldn't load the review queue: ${e.message}"; emptyList() }
  }
  LaunchedEffect(Unit) {
    // Category chips come from the admin-defined schema (same catalogue the host's wizard publishes under); the legacy enum is the fallback.
    val schema = try { gateway.readDoc("schema_architecture/main")?.let { Json.parse(it) } } catch (_: Throwable) { null }
    val fromSchema = schema?.get("spaceTypes")?.list().orEmpty().filter { it["isEnabled"].bool(true) }.map { it["id"].str() to it["name"].str() }.filter { it.first.isNotEmpty() }
    if (fromSchema.isNotEmpty()) categories = fromSchema
  }
  LaunchedEffect(query, refresh) {
    val q = query.trim()
    if (q.isEmpty()) { listings = emptyList(); bookings = emptyList(); loading = false; return@LaunchedEffect }
    loading = true; error = null
    delay(350)
    try {
      val r = Json.parse(gateway.call("adminSearch", """{"query":${jsonQuote(q)},"kind":"all"}"""))
      listings = r["listings"].list(); bookings = r["bookings"].list()
    } catch (e: Throwable) { listings = emptyList(); bookings = emptyList(); error = "Search failed: ${e.message}" }
    loading = false
  }

  val source = if (query.isBlank()) queue else listings
  val shown = source.filter { matchesCategory(it, category) && matchesStatus(it, status) }

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
      Tile("Listings", counts?.get("totalListings")?.int()?.toString() ?: "…", Modifier.weight(1f))
      Tile("Published", counts?.get("activeListings")?.int()?.toString() ?: "…", Modifier.weight(1f))
      Tile("To review", counts?.get("pendingReview")?.int()?.toString() ?: "…", Modifier.weight(1f))
      Tile("Bookings", counts?.get("totalBookings")?.int()?.toString() ?: "…", Modifier.weight(1f))
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Listings & Bookings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Search by title, L-/D-/B- code, id or the host's / renter's email", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query, { query = it }, label = { Text("Title, L-/D-/B- code, id or email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All categories", style = MaterialTheme.typography.labelSmall) })
          categories.forEach { (id, name) -> FilterChip(selected = category == id, onClick = { category = id }, label = { Text(name, style = MaterialTheme.typography.labelSmall) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          STATUS_FILTERS.forEach { (key, label) -> FilterChip(selected = status == key, onClick = { status = key }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }) }
        }
      }
    }
    Text(
      if (query.isBlank()) "Awaiting verification review (${queue.size}) — search to find any listing" else "${shown.size} listing(s), ${bookings.size} booking(s)",
      style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when {
      loading -> Text("Searching…", color = MaterialTheme.colorScheme.onSurfaceVariant)
      error != null && query.isNotBlank() -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
      shown.isEmpty() && bookings.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (query.isBlank()) "Nothing to review" else "No matches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(if (query.isBlank()) "No listing is waiting for verification." else "Nothing matches \"${query.trim()}\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    bookings.forEach { BookingCard(it, onOpenPerson = { uid -> profileUid = uid }) }
    shown.forEach { space ->
      ListingCard(
        space,
        onDocument = { scope.launch { try { openUrl(Json.parse(gateway.call("adminVerificationDocUrl", """{"spaceId":${jsonQuote(space["id"].str())}}"""))["url"].str()) } catch (e: Throwable) { notice = "Couldn't open the document. (${e.message})" } } },
        onToggleActive = { scope.launch { notice = try { gateway.call("setListingSubscriptionActive", """{"spaceId":${jsonQuote(space["id"].str())},"active":${!space["isActiveSubscription"].bool()}}"""); refresh++; "Listing subscription active status toggled" } catch (e: Throwable) { "Failed to toggle subscription status: ${e.message}" } } },
        onToggleVerified = { scope.launch { notice = try { gateway.call("setListingVerification", """{"spaceId":${jsonQuote(space["id"].str())},"verified":${!space["isVerified"].bool()}}"""); refresh++; "Verification status toggled for space" } catch (e: Throwable) { "Failed to toggle verification status: ${e.message}" } } },
        onHost = { profileUid = space["ownerId"].str() },
        onDelete = { deleting = space },
      )
    }
  }

  deleting?.let { space ->
    AlertDialog(
      onDismissRequest = { deleting = null },
      title = { Text("Delete Workspace Listing?") },
      text = { Text("Are you sure you want to delete '${space["title"].str()}' in ${space["district"].str()}? This will remove it from discovery and cancel any active rental bookings.") },
      confirmButton = {
        Button(onClick = {
          deleting = null
          scope.launch {
            val id = space["id"].str()
            notice = try {
              gateway.deleteDoc("workspace_listings/$id") // the server cleanup trigger cancels bookings
              try { gateway.purgeStorage("listings/$id") } catch (_: Throwable) { /* best effort, like Android */ }
              try { gateway.call("recordClientAuditLog", """{"actionType":"LISTING_DELETED","details":${jsonQuote("Admin permanently deleted workspace listing #$id (${space["title"].str().ifBlank { "Unknown" }})")},"severity":"WARN"}""") } catch (_: Throwable) { }
              refresh++
              "Listing permanently removed from catalog"
            } catch (e: Throwable) { "Failed to remove listing — please try again (${e.message})" }
          }
        }) { Text("Confirm Delete") }
      },
      dismissButton = { OutlinedButton(onClick = { deleting = null }) { Text("Cancel") } },
    )
  }
  profileUid?.takeIf { it.isNotBlank() }?.let { uid -> ProfileFlow(gateway, uid, onClose = { profileUid = null }, onChanged = { refresh++ }) }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
  Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(Spacing.md)) {
      Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun Chip(text: String, container: Color, content: Color) {
  Surface(color = container, shape = MaterialTheme.shapes.small) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = content, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
  }
}

private fun typeName(raw: String): String = LEGACY_CATEGORIES.firstOrNull { it.first == raw }?.second ?: raw.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun ListingCard(space: Json, onDocument: () -> Unit, onToggleActive: () -> Unit, onToggleVerified: () -> Unit, onHost: () -> Unit, onDelete: () -> Unit) {
  val verified = space["isVerified"].bool()
  val active = space["isActiveSubscription"].bool()
  val hasDoc = space["verificationDocUrl"].str().isNotBlank()
  val location = listOf(space["city"].str().ifBlank { space["district"].str() }, space["country"].str()).filter { it.isNotBlank() }.joinToString(", ")
  Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
      Text(space["title"].str(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text("$location • ${space["streetAddress"].str()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Chip(typeName(space["spaceType"].str()), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        if (active) Chip("Active", Color(0xFFE3F4EA), FreshGreen) else Chip("Expired", StatusErrorContainer, StatusOnErrorContainer)
        if (verified) Chip("Verified", Color(0xFFE3F4EA), FreshGreen) else if (hasDoc) Chip("Pending Verification", StatusWarningContainer, AmberWarning)
      }
      if (!verified && hasDoc) {
        OutlinedButton(onClick = onDocument, modifier = Modifier.fillMaxWidth()) { Text("View Verification Document (${space["verificationDocType"].str().replace('_', ' ').ifBlank { "on file" }})") }
      }
      Text(
        "Host: ${space["ownerName"].str()} • Phone: ${space["ownerPhone"].str()} • Pricing: ${space["pricing"]["strategyType"].str().lowercase().replace('_', ' ')}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onToggleActive) { Text(if (active) "Deactivate" else "Activate") }
        OutlinedButton(onClick = onToggleVerified) { Text(if (verified) "Unverify" else "Verify") }
        if (space["ownerId"].str().isNotBlank()) TextButton(onClick = onHost) { Text("Host profile") }
        TextButton(onClick = onDelete) { Text("Delete", color = StatusError) }
      }
    }
  }
}

@Composable
private fun BookingCard(b: Json, onOpenPerson: (String) -> Unit) {
  val total = b["totalAmountUsd"].num() ?: 0.0
  Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(b["displayCode"].str().ifBlank { b["id"].str() }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(b["status"].str().lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
      }
      Text(b["spaceTitle"].str().ifBlank { "Listing" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(
        "${b["practitionerName"].str().ifBlank { "Renter" }} → ${b["ownerName"].str().ifBlank { "Host" }} · ${b["startDate"].str()} · $${(kotlin.math.round(total * 100) / 100.0)}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (b["practitionerId"].str().isNotBlank()) OutlinedButton(onClick = { onOpenPerson(b["practitionerId"].str()) }) { Text("Renter profile") }
        if (b["ownerId"].str().isNotBlank()) OutlinedButton(onClick = { onOpenPerson(b["ownerId"].str()) }) { Text("Host profile") }
      }
    }
  }
}
