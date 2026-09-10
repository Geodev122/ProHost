package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.Spacing

/**
 * The room/desk ("subdivision") builder — add, list, and remove individually
 * rentable rooms or desks within a Center/Polyclinic/Co-working listing. Shared
 * by CreateListingDialog's Step 2 (at creation) and SpaceScheduleEditorDialog's
 * "Rooms & Subdivisions" section (post-publish), so a host is never stuck with
 * whatever subdivisions they happened to define during the original wizard.
 *
 * Only [subdivisionsList] and [onSubdivisionsChange] are hoisted — the "add a
 * new room" form fields are transient, single-use input state that resets after
 * each add, so they live locally here rather than in either caller.
 */
@Composable
fun SubdivisionEditorSection(
    subdivisionsList: List<Subdivision>,
    onSubdivisionsChange: (List<Subdivision>) -> Unit,
    modifier: Modifier = Modifier
) {
    var subName by remember { mutableStateOf("") }
    var subType by remember { mutableStateOf(Level2Type.ROOMS) }
    var subAmenitiesSelected by remember { mutableStateOf(setOf<String>()) }

    var subHourlyRate by remember { mutableStateOf("15") }
    var subHourlyEnabled by remember { mutableStateOf(false) }

    var subShiftRate by remember { mutableStateOf("60") }
    var subShiftHours by remember { mutableStateOf("Morning Shift (8AM - 1PM)") }
    var subShiftEnabled by remember { mutableStateOf(false) }

    var subDailyRate by remember { mutableStateOf("120") }
    var subDailyEnabled by remember { mutableStateOf(false) }

    var subMonthlyRate by remember { mutableStateOf("450") }
    var subMonthlyEnabled by remember { mutableStateOf(false) }

    val subAmenitiesPreset = listOf(
        "A/C Climate Control",
        "Dual-Monitor Workstation",
        "Whiteboard / Presentation kit",
        "High-Speed LAN/Wi-Fi",
        "Professional Soundproofing",
        "Patient Consultation Recliner",
        "Medical Sterilization Tray",
        "Keyless Lock / Access Control",
        "Privacy Curtains / Drapes"
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Configure Rooms & Workspace Subdivisions",
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Add individual rooms, offices, clinical chambers, or shared desks that professionals can rent separately. Specify individual features and custom pricing strategies for each.",
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
                                    "Renting: " + sub.rentalStrategies.joinToString { "${it.strategy.displayName} ($${it.rateUsd})" },
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (sub.amenities.isNotEmpty()) {
                                    Text("Amenities: ${sub.amenities.joinToString()}", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                Text("Room / Subdivision Type", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Level2Type.values()) { type ->
                        FilterChip(
                            selected = subType == type,
                            onClick = { subType = type },
                            label = { Text(type.displayName, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                        )
                    }
                }

                Text("Room Amenities", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(subAmenitiesPreset) { amen ->
                        val isSel = subAmenitiesSelected.contains(amen)
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                subAmenitiesSelected = if (isSel) subAmenitiesSelected - amen else subAmenitiesSelected + amen
                            },
                            label = { Text(amen, fontSize = 10.sp) }
                        )
                    }
                }

                Text("Renting Strategies & Rates for this Room", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.labelMedium.fontSize)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = subHourlyEnabled, onCheckedChange = { subHourlyEnabled = it })
                        Text("Hourly Basis", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                    }
                    if (subHourlyEnabled) {
                        OutlinedTextField(
                            value = subHourlyRate,
                            onValueChange = { subHourlyRate = it },
                            label = { Text("USD/hr") },
                            modifier = Modifier.width(100.dp),
                            singleLine = true
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = subShiftEnabled, onCheckedChange = { subShiftEnabled = it })
                            Text("Shift-Based", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        }
                        if (subShiftEnabled) {
                            OutlinedTextField(
                                value = subShiftHours,
                                onValueChange = { subShiftHours = it },
                                label = { Text("Shift Details (e.g. 8AM-1PM)") },
                                modifier = Modifier.fillMaxWidth(0.55f),
                                singleLine = true
                            )
                        }
                    }
                    if (subShiftEnabled) {
                        OutlinedTextField(
                            value = subShiftRate,
                            onValueChange = { subShiftRate = it },
                            label = { Text("USD/shift") },
                            modifier = Modifier.width(100.dp),
                            singleLine = true
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = subDailyEnabled, onCheckedChange = { subDailyEnabled = it })
                        Text("Daily Basis", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                    }
                    if (subDailyEnabled) {
                        OutlinedTextField(
                            value = subDailyRate,
                            onValueChange = { subDailyRate = it },
                            label = { Text("USD/day") },
                            modifier = Modifier.width(100.dp),
                            singleLine = true
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = subMonthlyEnabled, onCheckedChange = { subMonthlyEnabled = it })
                        Text("Monthly Basis", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                    }
                    if (subMonthlyEnabled) {
                        OutlinedTextField(
                            value = subMonthlyRate,
                            onValueChange = { subMonthlyRate = it },
                            label = { Text("USD/mo") },
                            modifier = Modifier.width(100.dp),
                            singleLine = true
                        )
                    }
                }

                Button(
                    onClick = {
                        val subStrategies = mutableListOf<SubdivisionStrategy>()
                        if (subHourlyEnabled) {
                            subStrategies.add(SubdivisionStrategy(RentalStrategy.HOURLY, subHourlyRate.toDoubleOrNull() ?: 15.0))
                        }
                        if (subShiftEnabled) {
                            subStrategies.add(SubdivisionStrategy(RentalStrategy.SHIFT_BASED, subShiftRate.toDoubleOrNull() ?: 60.0, availableHoursOrShifts = subShiftHours))
                        }
                        if (subDailyEnabled) {
                            subStrategies.add(SubdivisionStrategy(RentalStrategy.DAILY, subDailyRate.toDoubleOrNull() ?: 120.0))
                        }
                        if (subMonthlyEnabled) {
                            subStrategies.add(SubdivisionStrategy(RentalStrategy.MONTHLY, subMonthlyRate.toDoubleOrNull() ?: 450.0))
                        }

                        // No hardcoded stock photo per type anymore (was a fixed
                        // Unsplash URL regardless of the actual room/desk). Rooms
                        // and desks inherit the parent listing's real cover photos
                        // visually; a dedicated per-subdivision photo picker is a
                        // separate feature, not part of this fix.
                        val newSub = Subdivision(
                            name = subName,
                            type = subType,
                            imageUrls = emptyList(),
                            amenities = subAmenitiesSelected.toList(),
                            rentalStrategies = subStrategies
                        )
                        onSubdivisionsChange(subdivisionsList + newSub)
                        subName = ""
                        subAmenitiesSelected = emptySet()
                        subHourlyEnabled = false
                        subShiftEnabled = false
                        subDailyEnabled = false
                        subMonthlyEnabled = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = subName.isNotBlank() && (subHourlyEnabled || subShiftEnabled || subDailyEnabled || subMonthlyEnabled)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Add Room / Desk to Listing", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                }
            }
        }
    }
}
