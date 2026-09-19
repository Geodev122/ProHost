package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CancellationReasonCode
import com.example.ui.theme.Spacing

/**
 * Early-termination confirmation for an already-ACCEPTED booking — a reason code
 * plus an optional note, deliberately simple (no refund/penalty logic, since no
 * rent settlement happens in-app). Shared between MyBookingsScreen (practitioner
 * side) and OwnerRentingProgressScreen (host side); [partyLabel] just changes the
 * warning copy to name the other party correctly.
 */
@Composable
fun CancelAcceptedBookingDialog(
    spaceTitle: String,
    partyLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (reasonCode: CancellationReasonCode, note: String?) -> Unit
) {
    var selectedReason by remember { mutableStateOf<CancellationReasonCode?>(null) }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Cancel Accepted Booking?", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyLarge.fontSize)
                }
                Text(
                    "This ends the active booking for \"$spaceTitle\" immediately and notifies $partyLabel. There is no in-app refund or penalty — settle anything owed directly with $partyLabel.",
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Reason", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(CancellationReasonCode.entries) { reason ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (selectedReason == reason) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            onClick = { selectedReason = reason },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason })
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(reason.displayName, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Additional note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Keep Booking")
                    }
                    Button(
                        onClick = { selectedReason?.let { onConfirm(it, note.ifBlank { null }) } },
                        enabled = selectedReason != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel Booking")
                    }
                }
            }
        }
    }
}
