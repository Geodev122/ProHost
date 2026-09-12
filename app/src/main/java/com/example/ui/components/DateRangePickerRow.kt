package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "From: ... / To: ..." date-range chips + Material3 date pickers, extracted from
 * DrawerDialogsHandler.kt's Audit Logs dialog (the only place this pattern existed) so
 * the Owners & Payments chart, the Security tab's CSV export, and the Transactions tab's
 * CSV export can all share one implementation instead of three copies. [toMillis] is
 * adjusted to an inclusive end-of-day (+24h-1ms) the moment a date is picked, so callers
 * can compare directly against it with `<=` without re-deriving that adjustment themselves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerRow(
    fromMillis: Long?,
    toMillis: Long?,
    onFromChange: (Long?) -> Unit,
    onToChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val sdfShort = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = { showFromPicker = true },
            label = { Text(fromMillis?.let { "From: ${sdfShort.format(Date(it))}" } ?: "From: Any", fontSize = MaterialTheme.typography.labelSmall.fontSize) },
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) }
        )
        AssistChip(
            onClick = { showToPicker = true },
            label = { Text(toMillis?.let { "To: ${sdfShort.format(Date(it))}" } ?: "To: Any", fontSize = MaterialTheme.typography.labelSmall.fontSize) },
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) }
        )
        if (fromMillis != null || toMillis != null) {
            IconButton(onClick = { onFromChange(null); onToChange(null) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear date filter", modifier = Modifier.size(16.dp))
            }
        }
    }

    if (showFromPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = fromMillis)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onFromChange(pickerState.selectedDateMillis)
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showToPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = toMillis)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Inclusive end-of-day so "To: today" also includes today's entries.
                    onToChange(pickerState.selectedDateMillis?.plus(24L * 60 * 60 * 1000 - 1))
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
