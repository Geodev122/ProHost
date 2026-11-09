package admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendeePackage
import com.example.data.model.SchemaCategory
import com.example.data.model.SchemaItem
import com.example.data.model.SpaceArchitectureSchema
import com.example.data.repository.createDefaultSchema
import com.example.ui.theme.CarnationOrange
import com.example.ui.theme.CarnationOrangeContainer
import com.example.ui.theme.Spacing
import com.example.ui.theme.StatusError
import kotlinx.coroutines.launch

/**
 * ADMIN_PARITY items 45-52 (Schema): overview tiles, CRUD + enable switch for Space Types / Division Types / Facilities / Amenities /
 * Rental Strategies (CUSTOM chip, delete only for non-system items), edit dialog (name, description; space type max subdivisions and map marker
 * colour; amenity group and division-type scope), Add Node, Reset to defaults, Add missing built-ins, Seed Defaults, attendee tier templates,
 * hashtag usage and the Firestore contract card. The models and the built-in catalogue are the SAME code as the app's (`:shared`).
 * Differences to record: attendee-package inclusions are one per line in a text box (Android adds them as chips one by one).
 */
private val MARKER_COLOR_PRESETS = listOf("#5B9BFF", "#FF8F73", "#7DD9A0", "#B197FC", "#E8C468", "#6FE3E3", "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7", "#DDA0DD")
private val CATEGORIES = listOf(
  SchemaCategory.SPACE_TYPE to "Space Type",
  SchemaCategory.DIVISION_TYPE to "Division Type",
  SchemaCategory.FACILITY to "Facility (whole-space)",
  SchemaCategory.AMENITY to "Amenity (subdivision)",
  SchemaCategory.RENTAL_STRATEGY to "Rental Strategy",
)
private val DEFAULT_AMENITIES = listOf(
  "A/C Climate Control", "Dual-Monitor Setup", "Whiteboard / Presentation Kit", "High-Speed Wi-Fi", "Soundproofing", "Ergonomic Seating",
  "Storage Locker", "Keyless Access Control", "Privacy Partition", "Natural Lighting", "Standing Desk",
)

private fun randomHex6(): String {
  val chars = "0123456789ABCDEF"
  return buildString { repeat(6) { append(chars[(kotlin.random.Random.nextInt(16))]) } }
}

@Composable
fun SchemaTab(gateway: AdminGateway) {
  var schema by remember { mutableStateOf<SpaceArchitectureSchema?>(null) }
  var loadError by remember { mutableStateOf<String?>(null) }
  var notice by remember { mutableStateOf<String?>(null) }
  var editing by remember { mutableStateOf<SchemaItem?>(null) }
  var adding by remember { mutableStateOf<String?>(null) } // preset category
  var resetOpen by remember { mutableStateOf(false) }
  var pkgDialog by remember { mutableStateOf<AttendeePackage?>(null) }
  var pkgNew by remember { mutableStateOf(false) }
  var amenityGroup by remember { mutableStateOf("All") }
  var hashtags by remember { mutableStateOf<List<Json>>(emptyList()) }
  var hashtagReload by remember { mutableStateOf(0) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    try {
      val json = gateway.readDoc("schema_architecture/main")
      @Suppress("UNCHECKED_CAST")
      schema = if (json == null) createDefaultSchema() else SpaceArchitectureSchema.fromFirestoreMap(Json.parse(json).toKotlin() as? Map<String, Any?> ?: emptyMap())
    } catch (e: Throwable) { loadError = "Couldn't load the schema: ${e.message}"; schema = createDefaultSchema() }
  }
  LaunchedEffect(hashtagReload) { hashtags = try { Json.parse(gateway.hashtagUsage()).list() } catch (_: Throwable) { emptyList() } }

  suspend fun audit(type: String, details: String, severity: String) {
    try { gateway.call("recordClientAuditLog", """{"actionType":${jsonQuote(type)},"details":${jsonQuote(details)},"severity":${jsonQuote(severity)}}""") } catch (_: Throwable) { /* the write itself succeeded */ }
  }

  /** Saves the whole schema document (like `FirestoreService.saveSchema`) and reports; [then] runs after a successful save. */
  fun save(updated: SpaceArchitectureSchema, ok: String, fail: String, then: suspend () -> Unit = {}) {
    scope.launch {
      notice = try {
        gateway.mergeDoc("schema_architecture/main", toJsonText(updated.toFirestoreMap()))
        schema = updated; then(); ok
      } catch (e: Throwable) { "$fail (${e.message})" }
    }
  }

  fun List<SchemaItem>.mapIf(id: String, f: (SchemaItem) -> SchemaItem) = map { if (it.id == id) f(it) else it }
  fun SpaceArchitectureSchema.mapItems(f: (List<SchemaItem>) -> List<SchemaItem>) = copy(
    spaceTypes = f(spaceTypes), divisionTypes = f(divisionTypes), facilities = f(facilities), amenities = f(amenities), rentalStrategies = f(rentalStrategies),
  )

  val s = schema
  if (s == null) { Text("Loading schema…", color = MaterialTheme.colorScheme.onSurfaceVariant); return }

  val toggle: (SchemaItem) -> Unit = { item ->
    save(s.mapItems { l -> l.mapIf(item.id) { it.copy(isEnabled = !it.isEnabled) } }, "Schema item status updated", "Failed to update schema item — please try again") {
      audit("SCHEMA_ITEM_TOGGLED", "Admin toggled schema item #${item.id} active status", "INFO")
    }
  }
  val delete: (SchemaItem) -> Unit = { item ->
    save(s.mapItems { l -> l.filterNot { it.id == item.id } }, "Schema item deleted from database registry", "Failed to delete schema item — please try again") {
      audit("SCHEMA_ITEM_DELETED", "Admin removed custom schema item #${item.id}", "WARN")
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
    loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Column {
            Text("God Schema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Live admin-editable database", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = { resetOpen = true }) { Text("Reset") }
            Button(onClick = { adding = SchemaCategory.DIVISION_TYPE }) { Text("Add Node") }
          }
        }
        // Additive only: unlike Reset, keeps every existing entry and its edits.
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
          val defaults = createDefaultSchema()
          fun merge(existing: List<SchemaItem>, builtIn: List<SchemaItem>): List<SchemaItem> {
            val ids = existing.map { it.id }.toSet()
            val names = existing.map { it.name.trim().lowercase() }.toSet()
            return existing + builtIn.filter { it.id !in ids && it.name.trim().lowercase() !in names }
          }
          val updated = s.copy(
            spaceTypes = merge(s.spaceTypes, defaults.spaceTypes), divisionTypes = merge(s.divisionTypes, defaults.divisionTypes),
            facilities = merge(s.facilities, defaults.facilities), amenities = merge(s.amenities, defaults.amenities),
            rentalStrategies = merge(s.rentalStrategies, defaults.rentalStrategies),
            attendeePackages = s.attendeePackages + defaults.attendeePackages.filter { d -> s.attendeePackages.none { it.id == d.id || it.name.trim().equals(d.name.trim(), ignoreCase = true) } },
          )
          val added = updated.totalItemsCount - s.totalItemsCount
          if (added == 0) notice = "Nothing to add — every built-in entry is already there"
          else save(updated, "Added $added missing built-in schema item(s)", "Couldn't add the missing entries") {
            audit("SCHEMA_DEFAULTS_MERGED", "Admin added $added missing built-in schema item(s); existing items unchanged", "SECURE")
          }
        }) { Text("Add Missing Built-in Entries") }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          listOf("Space Types" to s.spaceTypes, "Division Types" to s.divisionTypes, "Facilities" to s.facilities, "Amenities" to s.amenities, "Strategies" to s.rentalStrategies).forEach { (title, items) ->
            Tile(title, "${items.size}", "${items.count { it.isEnabled }} active · ${items.count { !it.isEnabled }} off")
          }
          Tile("Packages", "${s.attendeePackages.size}", "${s.attendeePackages.count { it.isEnabled }} active")
        }
      }
    }

    Section("Space Types", "Top-level listing categories", s.spaceTypes, { adding = SchemaCategory.SPACE_TYPE }, toggle, delete, { editing = it }) { item ->
      item.maxSubdivisions?.let { Text("Max subdivisions: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Section("Division Types", "Subdivision types (available across all space types)", s.divisionTypes, { adding = SchemaCategory.DIVISION_TYPE }, toggle, delete, { editing = it })
    Section("Facilities", "Whole-space facilities — shown to all subdivisions", s.facilities, { adding = SchemaCategory.FACILITY }, toggle, delete, { editing = it })

    val groups = listOf("All") + s.amenities.map { it.amenityGroup }.filter { it.isNotBlank() }.distinct().sorted()
    val visibleAmenities = if (amenityGroup == "All") s.amenities else s.amenities.filter { it.amenityGroup == amenityGroup }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Column {
            Text("Amenities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Subdivision-level features, equipment & amenities", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Button(onClick = { adding = SchemaCategory.AMENITY }) { Text("Add") }
        }
        if (groups.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          groups.forEach { g -> FilterChip(selected = amenityGroup == g, onClick = { amenityGroup = g }, label = { Text(g, style = MaterialTheme.typography.labelSmall) }) }
        }
        visibleAmenities.forEach { item ->
          ItemRow(item, { toggle(item) }, { delete(item) }, { editing = item }) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
              if (item.amenityGroup.isNotBlank()) Chip(item.amenityGroup, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
              Chip(if (item.scopedToIds.isEmpty()) "All divisions" else "${item.scopedToIds.size} div. types", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
        if (visibleAmenities.isEmpty()) {
          Text("No amenities in this group.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (s.amenities.isEmpty()) OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            val seeded = DEFAULT_AMENITIES.map {
              SchemaItem(id = "SCH-" + SchemaCategory.AMENITY.take(3) + "-" + randomHex6(), name = it, description = "", category = SchemaCategory.AMENITY, iconName = "Star", isEnabled = true, isSystemDefault = false)
            }
            save(s.copy(amenities = s.amenities + seeded), "Default amenities added", "Couldn't seed the defaults") {
              audit("SCHEMA_ITEM_ADDED", "Admin added ${seeded.size} default amenities", "SECURE")
            }
          }) { Text("Seed Defaults") }
        }
      }
    }

    Section("Rental Strategies", "Reference labels — mirrors RentalStrategyType", s.rentalStrategies, { adding = SchemaCategory.RENTAL_STRATEGY }, toggle, delete, { editing = it })

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Column {
            Text("Attendee Tier Templates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Starting tiers hosts can copy into a per-attendee room, then edit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Button(onClick = { pkgNew = true }) { Text("Add") }
        }
        if (s.attendeePackages.isEmpty()) Text("No attendee packages defined yet.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.attendeePackages.forEach { pkg ->
          Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(pkg.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (!pkg.isSystemDefault) Chip("CUSTOM", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
              }
              val price = pkg.pricePerAttendeeUsd.let { if (it == kotlin.math.floor(it)) it.toLong().toString() else (kotlin.math.round(it * 100) / 100.0).toString() }
              Text("$$price/person · min ${pkg.minAttendees}${pkg.maxAttendees?.let { " · max $it" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
              if (pkg.inclusions.isNotEmpty()) Text(pkg.inclusions.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Switch(checked = pkg.isEnabled, onCheckedChange = {
                save(s.copy(attendeePackages = s.attendeePackages.map { if (it.id == pkg.id) it.copy(isEnabled = !it.isEnabled) else it }), "Package updated", "Update failed")
              })
              TextButton(onClick = { pkgDialog = pkg }) { Text("Edit") }
              TextButton(onClick = { save(s.copy(attendeePackages = s.attendeePackages.filter { it.id != pkg.id }), "Package deleted", "Delete failed") }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
      }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Column {
            Text("Target Disciplines Usage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Hashtags hosts attach to listings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          TextButton(onClick = { hashtagReload++ }) { Text("Refresh") }
        }
        if (hashtags.isEmpty()) Text("No hashtags recorded yet.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        hashtags.forEach { e ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("#${e["tag"].str()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("${e["count"].int()} uses • ${e["governorate"].str()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Cloud Firestore Collections Contract", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
          "• Collection: 'workspace_listings' (Documents: SpaceListing)\n" +
            "• Collection: 'user_profiles' (Documents: AppUser)\n" +
            "• Collection: 'booking_requests' (Documents: RentalBookingRequest)\n" +
            "• Collection: 'subscriptions' (Google Play subscription records, server-only)\n" +
            "• Collection: 'audit_security_logs' (Documents: AuditSecurityLog)\n" +
            "• Collection: 'system_metadata' (Documents: AdminPricingState)\n" +
            "• Collection: 'hashtag_usage' (Documents: HashtagUsageEntry)\n" +
            "• Collection: 'schema_architecture' (Documents: SpaceArchitectureSchema)",
          style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  editing?.let { item ->
    EditItemDialog(item, s.divisionTypes, onDismiss = { editing = null }) { updated ->
      editing = null
      save(s.mapItems { l -> l.mapIf(updated.id) { updated } }, "Schema item updated", "Couldn't save the schema item") {
        audit("SCHEMA_ITEM_UPDATED", "Schema item ${updated.id} updated: ${updated.name}", "INFO")
      }
    }
  }
  adding?.let { preset ->
    AddNodeDialog(preset, onDismiss = { adding = null }) { name, category, description, maxSub, color ->
      adding = null
      val item = SchemaItem(
        id = "SCH-" + category.take(3) + "-" + randomHex6(), name = name, description = description, category = category, iconName = "Category",
        isEnabled = true, isSystemDefault = false, maxSubdivisions = maxSub, markerColor = color?.takeIf { it.isNotBlank() },
      )
      val updated = when (category) {
        SchemaCategory.SPACE_TYPE -> s.copy(spaceTypes = s.spaceTypes + item)
        SchemaCategory.DIVISION_TYPE -> s.copy(divisionTypes = s.divisionTypes + item)
        SchemaCategory.FACILITY -> s.copy(facilities = s.facilities + item)
        SchemaCategory.AMENITY -> s.copy(amenities = s.amenities + item)
        SchemaCategory.RENTAL_STRATEGY -> s.copy(rentalStrategies = s.rentalStrategies + item)
        else -> s
      }
      save(updated, "New schema entry added: $name", "Failed to add schema entry — please try again") {
        audit("SCHEMA_ITEM_ADDED", "Admin added schema node '${item.name}' under category '${item.category}'", "SECURE")
      }
    }
  }
  if (resetOpen) AlertDialog(
    onDismissRequest = { resetOpen = false }, title = { Text("Reset Architecture Schema?") },
    text = { Text("This will restore all default workspace classifications — space types, division types, facilities, amenities, rental strategies and attendee packages — and discard every custom entry and edit.") },
    confirmButton = {
      Button(onClick = {
        resetOpen = false
        save(createDefaultSchema(), "Schema restored to the factory baseline", "Couldn't reset the schema") {
          audit("SCHEMA_RESET_DEFAULTS", "Admin restored factory baseline schema definitions for Lebanese workspaces", "SECURE")
        }
      }) { Text("Confirm Reset") }
    },
    dismissButton = { OutlinedButton(onClick = { resetOpen = false }) { Text("Cancel") } },
  )
  if (pkgNew || pkgDialog != null) PackageDialog(pkgDialog, onDismiss = { pkgNew = false; pkgDialog = null }) { name, desc, price, inclusions, min, max ->
    val existing = pkgDialog
    pkgNew = false; pkgDialog = null
    if (existing == null) {
      val pkg = AttendeePackage(id = "APK-" + randomHex6(), name = name.trim(), description = desc.trim(), pricePerAttendeeUsd = price, inclusions = inclusions, minAttendees = min, maxAttendees = max, isSystemDefault = false)
      save(s.copy(attendeePackages = s.attendeePackages + pkg), "Package \"${pkg.name}\" added", "Failed to add package")
    } else {
      val pkg = existing.copy(name = name.trim(), description = desc.trim(), pricePerAttendeeUsd = price, inclusions = inclusions, minAttendees = min, maxAttendees = max)
      save(s.copy(attendeePackages = s.attendeePackages.map { if (it.id == pkg.id) pkg else it }), "Package updated", "Update failed")
    }
  }
}

@Composable
private fun Tile(title: String, value: String, subtitle: String) {
  Card(Modifier.width(140.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
    Column(Modifier.padding(Spacing.md)) {
      Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun Chip(text: String, container: Color, content: Color) {
  Surface(color = container, shape = MaterialTheme.shapes.extraSmall) { Text(text, style = MaterialTheme.typography.labelSmall, color = content, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
}

@Composable
private fun Section(title: String, subtitle: String, items: List<SchemaItem>, onAdd: () -> Unit, onToggle: (SchemaItem) -> Unit, onDelete: (SchemaItem) -> Unit, onEdit: (SchemaItem) -> Unit, extra: (@Composable (SchemaItem) -> Unit)? = null) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
          Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onAdd) { Text("Add") }
      }
      if (items.isEmpty()) Text("No items yet.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      items.forEachIndexed { i, item ->
        ItemRow(item, { onToggle(item) }, { onDelete(item) }, { onEdit(item) }, extra?.let { f -> { f(item) } })
        if (i < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
      }
    }
  }
}

@Composable
private fun ItemRow(item: SchemaItem, onToggle: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit, extra: (@Composable () -> Unit)? = null) {
  Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (!item.isSystemDefault) Chip("CUSTOM", CarnationOrangeContainer, CarnationOrange)
      }
      Text("ID: ${item.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
      if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      extra?.invoke()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onEdit) { Text("Edit") }
      Switch(checked = item.isEnabled, onCheckedChange = { onToggle() })
      if (!item.isSystemDefault) TextButton(onClick = onDelete) { Text("Delete", color = StatusError) }
    }
  }
}

@Composable
private fun ColorPicker(value: String, onChange: (String) -> Unit) {
  Text("Map Marker Color", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
    MARKER_COLOR_PRESETS.forEach { hex ->
      val selected = value.equals(hex, ignoreCase = true)
      val rgb = hex.removePrefix("#").toLong(16)
      Box(
        Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF000000 or rgb))
          .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
          .clickable { onChange(hex) },
      )
    }
  }
  OutlinedTextField(value, { onChange(it.take(7)) }, label = { Text("Hex color (e.g. #5B9BFF)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun EditItemDialog(item: SchemaItem, divisionTypes: List<SchemaItem>, onDismiss: () -> Unit, onSave: (SchemaItem) -> Unit) {
  var name by remember { mutableStateOf(item.name) }
  var description by remember { mutableStateOf(item.description) }
  var group by remember { mutableStateOf(item.amenityGroup) }
  var maxSubs by remember { mutableStateOf(item.maxSubdivisions?.toString() ?: "") }
  var scoped by remember { mutableStateOf(item.scopedToIds.toSet()) }
  var color by remember { mutableStateOf(item.markerColor ?: "") }
  AlertDialog(
    onDismissRequest = onDismiss, title = { Text("Edit Schema Item", fontWeight = FontWeight.Bold) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        if (item.category == SchemaCategory.SPACE_TYPE) {
          OutlinedTextField(maxSubs, { maxSubs = it }, label = { Text("Max Subdivisions (leave blank = unlimited)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
          ColorPicker(color) { color = it }
        }
        if (item.category == SchemaCategory.AMENITY) {
          OutlinedTextField(group, { group = it }, label = { Text("Amenity Group (e.g. Comfort, Access, Equipment)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
          if (divisionTypes.isNotEmpty()) {
            Text("Scoped to Division Types (empty = all):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            divisionTypes.forEach { dt ->
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = dt.id in scoped, onCheckedChange = { c -> scoped = if (c) scoped + dt.id else scoped - dt.id })
                Text(dt.name, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(enabled = name.isNotBlank(), onClick = {
        onSave(item.copy(
          name = name.trim(), description = description.trim(), amenityGroup = group.trim(),
          maxSubdivisions = if (item.category == SchemaCategory.SPACE_TYPE) maxSubs.trim().toIntOrNull() else item.maxSubdivisions,
          scopedToIds = if (item.category == SchemaCategory.AMENITY) scoped.toList() else item.scopedToIds,
          markerColor = if (item.category == SchemaCategory.SPACE_TYPE) color.takeIf { it.isNotBlank() } else item.markerColor,
        ))
      }) { Text("Save") }
    },
    dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun AddNodeDialog(initial: String, onDismiss: () -> Unit, onAdd: (String, String, String, Int?, String?) -> Unit) {
  var name by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var category by remember { mutableStateOf(initial) }
  var maxSubs by remember { mutableStateOf("") }
  var color by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss, title = { Text("Add Architecture Schema Node", fontWeight = FontWeight.Bold) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Target Schema Category:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          CATEGORIES.forEach { (id, label) -> FilterChip(selected = category == id, onClick = { category = id }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }) }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Schema Node Name (e.g. Laser Surgery Suite)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Description / Compliance Context") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        if (category == SchemaCategory.SPACE_TYPE) {
          OutlinedTextField(maxSubs, { maxSubs = it }, label = { Text("Max subdivisions this category includes (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
          ColorPicker(color) { color = it }
        }
      }
    },
    confirmButton = {
      Button(enabled = name.isNotBlank(), onClick = {
        onAdd(name, category, description, if (category == SchemaCategory.SPACE_TYPE) maxSubs.toIntOrNull() else null, if (category == SchemaCategory.SPACE_TYPE) color.takeIf { it.isNotBlank() } else null)
      }) { Text("Create Node") }
    },
    dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun PackageDialog(existing: AttendeePackage?, onDismiss: () -> Unit, onSave: (String, String, Double, List<String>, Int, Int?) -> Unit) {
  var name by remember { mutableStateOf(existing?.name ?: "") }
  var description by remember { mutableStateOf(existing?.description ?: "") }
  var price by remember { mutableStateOf(existing?.pricePerAttendeeUsd?.let { if (it == kotlin.math.floor(it)) it.toLong().toString() else (kotlin.math.round(it * 100) / 100.0).toString() } ?: "") }
  var min by remember { mutableStateOf(existing?.minAttendees?.toString() ?: "1") }
  var max by remember { mutableStateOf(existing?.maxAttendees?.toString() ?: "") }
  var inclusions by remember { mutableStateOf(existing?.inclusions?.joinToString("\n") ?: "") }
  val priceValue = price.trim().toDoubleOrNull()
  AlertDialog(
    onDismissRequest = onDismiss, title = { Text(if (existing != null) "Edit Attendee Package" else "Add Attendee Package", fontWeight = FontWeight.Bold) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        OutlinedTextField(name, { name = it }, label = { Text("Package Name") }, placeholder = { Text("e.g. Standard Package") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          OutlinedTextField(price, { price = it }, label = { Text("Price / Person (USD)") }, prefix = { Text("$") }, singleLine = true, modifier = Modifier.weight(1f))
          OutlinedTextField(min, { min = it }, label = { Text("Min Attendees") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(max, { max = it }, label = { Text("Max Attendees (leave blank = no cap)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(inclusions, { inclusions = it }, label = { Text("Inclusions (one per line)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = {
      Button(enabled = name.isNotBlank() && priceValue != null && priceValue >= 0, onClick = {
        onSave(name, description, priceValue ?: 0.0, inclusions.lines().map { it.trim() }.filter { it.isNotEmpty() }, min.trim().toIntOrNull() ?: 1, max.trim().toIntOrNull())
      }) { Text("Save") }
    },
    dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
