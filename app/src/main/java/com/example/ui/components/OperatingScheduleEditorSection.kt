package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The opening/closing-hour fields + day-of-week chip row shared by
 * CreateListingDialog's Step 2 (setting a brand-new listing's operating schedule)
 * and SpaceScheduleEditorDialog's post-publish "Facility Operating Window" editor —
 * previously hand-rolled separately in both places despite being the identical
 * control (same fields, same day-chip toggle behavior), the one piece of Phase 2's
 * plan that was never actually extracted. Callers own everything around this
 * (titles, a Sunday toggle, a Save button, a wizard's surrounding form) — this is
 * just the two real inputs.
 */
@Composable
fun OperatingScheduleEditorSection(
    openingHour: String,
    onOpeningHourChange: (String) -> Unit,
    closingHour: String,
    onClosingHourChange: (String) -> Unit,
    selectedDays: Set<String>,
    onDaysChange: (Set<String>) -> Unit,
    weekDayOptions: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = openingHour,
                onValueChange = onOpeningHourChange,
                label = { Text("Opens (HH:mm)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = closingHour,
                onValueChange = onClosingHourChange,
                label = { Text("Closes (HH:mm)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Text("Operating Days", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(weekDayOptions) { day ->
                val isSelected = selectedDays.contains(day)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onDaysChange(if (isSelected) selectedDays - day else selectedDays + day)
                    },
                    label = { Text(day, fontSize = MaterialTheme.typography.labelMedium.fontSize) }
                )
            }
        }
    }
}
