package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.data.storage.FirebaseStorageService
import com.example.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The room/desk ("subdivision") builder — add, list, and remove individually
 * rentable rooms or desks within a Center/Polyclinic/Co-working listing. Shared
 * by CreateListingDialog's Step 3 (at creation) and SpaceScheduleEditorDialog's
 * "Rooms & Subdivisions" section (post-publish), so a host is never stuck with
 * whatever subdivisions they happened to define during the original wizard.
 *
 * [operatingDays]/[openingHour]/[closingHour] come from the parent space's own
 * [SpaceOperatingSchedule] — the per-division pricing editor keys its Hourly/Shift/
 * Day-Based tables off these, so a division can never offer a slot outside hours
 * the space itself doesn't operate in.
 *
 * Only [subdivisionsList] and [onSubdivisionsChange] are hoisted — the "add a
 * new room" form fields are transient, single-use input state that resets after
 * each add, so they live locally here rather than in either caller.
 */
@Composable
fun SubdivisionEditorSection(
    subdivisionsList: List<Subdivision>,
    onSubdivisionsChange: (List<Subdivision>) -> Unit,
    operatingDays: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
    openingHour: String = "08:00",
    closingHour: String = "20:00",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storageService = remember { FirebaseStorageService.getInstance() }

    var subName by remember { mutableStateOf("") }
    var subType by remember { mutableStateOf(Level2Type.ROOMS) }
    var subAmenitiesSelected by remember { mutableStateOf(setOf<String>()) }
    var subAmenitySearch by remember { mutableStateOf("") }
    var subImageUrls by remember { mutableStateOf(listOf<String>()) }
    var isUploadingSubImage by remember { mutableStateOf(false) }
    var subPricing by remember { mutableStateOf(RentalPricingConfig.default()) }

    // Pending id so images upload to their final path before the Subdivision object
    // itself is created — same "generate the id up front" pattern CreateListingDialog
    // uses for the parent listing's own photos. Regenerated after each successful Add
    // (see the Button below) — a single remember{} here would give every room added
    // in the same session the identical id, silently overwriting each other's images.
    var pendingSubId by remember { mutableStateOf("SUB-" + UUID.randomUUID().toString().take(6).uppercase()) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingSubImage = true
            uris.forEach { uri ->
                val imageId = UUID.randomUUID().toString().take(8)
                // spaceId isn't known here (a subdivision can be configured before the
                // parent listing itself is ever saved) — pendingSubId doubles as both
                // the eventual Subdivision.id and a unique enough path segment, since
                // uploadSubdivisionImage's path is keyed by spaceId anyway once this
                // moves under a real listing; using it here too keeps every image this
                // section ever uploads under a name unique to this one room.
                val url = storageService.uploadSubdivisionImage(
                    spaceId = pendingSubId,
                    subdivisionId = pendingSubId,
                    imageId = imageId,
                    fileUri = uri,
                    fileExtension = "jpg"
                )
                if (url != null) subImageUrls = subImageUrls + url
            }
            isUploadingSubImage = false
        }
    }

    val amenityCatalog = listOf(
        "A/C Climate Control", "Dual-Monitor Setup", "Whiteboard / Presentation Kit",
        "High-Speed Wi-Fi", "Soundproofing", "Ergonomic Seating", "Storage Locker",
        "Keyless Access Control", "Privacy Partition", "Natural Lighting", "Standing Desk"
    )
    val filteredAmenities = amenityCatalog.filter {
        subAmenitySearch.isBlank() || it.contains(subAmenitySearch, ignoreCase = true)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Configure Rooms & Workspace Subdivisions",
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Add individual rooms, offices, or shared desks that professionals can rent separately. Each gets its own type, amenities, images, and renting strategy.",
            fontSize = MaterialTheme.typography.labelMedium.fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (subdivisionsList.isNotEmpty()) {
            Text("Configured Subdivisions (${subdivisionsList.size})", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subdivisionsList.forEachIndexed { index, sub ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sub.name, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                                Text("Type: ${sub.type.displayName}", fontSize = MaterialTheme.typography.labelMedium.fontSize, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "Strategy: ${sub.pricing.strategyType.displayName}",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (sub.amenities.isNotEmpty()) {
                                    Text("Amenities: ${sub.amenities.joinToString()}", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (sub.imageUrls.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(sub.imageUrls) { url ->
                                            AsyncImage(
                                                model = url,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            IconButton(onClick = { onSubdivisionsChange(subdivisionsList.filterIndexed { i, _ -> i != index }) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), MaterialTheme.shapes.medium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add Room / Unit Details", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodySmall.fontSize)

                InputField(
                    value = subName,
                    onValueChange = { subName = it },
                    label = "Room Name / Desk ID (e.g. Treatment Room B)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Type", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Level2Type.values()) { type ->
                        FilterChip(
                            selected = subType == type,
                            onClick = { subType = type },
                            label = { Text(type.displayName, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                        )
                    }
                }

                Text("Amenities", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                OutlinedTextField(
                    value = subAmenitySearch,
                    onValueChange = { subAmenitySearch = it },
                    placeholder = { Text("Search amenities...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filteredAmenities) { amen ->
                        val isSel = subAmenitiesSelected.contains(amen)
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                subAmenitiesSelected = if (isSel) subAmenitiesSelected - amen else subAmenitiesSelected + amen
                            },
                            label = { Text(amen, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                        )
                    }
                }

                Text("Images", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(subImageUrls) { url ->
                        Box(modifier = Modifier.size(72.dp)) {
                            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            IconButton(
                                onClick = { subImageUrls = subImageUrls - url },
                                modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove image")
                            }
                        }
                    }
                    item {
                        Surface(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable(enabled = !isUploadingSubImage) { imagePickerLauncher.launch("image/*") },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isUploadingSubImage) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = "Add room image")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                RentalPricingConfigEditor(
                    config = subPricing,
                    operatingDays = operatingDays,
                    openingHour = openingHour,
                    closingHour = closingHour,
                    onConfigChange = { subPricing = it }
                )

                Button(
                    onClick = {
                        val newSub = Subdivision(
                            id = pendingSubId,
                            name = subName,
                            type = subType,
                            imageUrls = subImageUrls,
                            amenities = subAmenitiesSelected.toList(),
                            pricing = subPricing
                        )
                        onSubdivisionsChange(subdivisionsList + newSub)
                        subName = ""
                        subAmenitiesSelected = emptySet()
                        subImageUrls = emptyList()
                        subPricing = RentalPricingConfig.default()
                        pendingSubId = "SUB-" + UUID.randomUUID().toString().take(6).uppercase()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = subName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Add Room / Desk to Listing", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                }
            }
        }
    }
}
