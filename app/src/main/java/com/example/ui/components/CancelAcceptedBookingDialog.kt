package com.example.ui.components

import com.example.R
import androidx.compose.ui.res.stringResource
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
import com.example.ui.components.CustomButton
import com.example.ui.components.CustomButtonVariant
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
    com.example.ui.components.TrackScreen("cancel_booking", "Dialog")
    var selectedReason by remember { mutableStateOf<CancellationReasonCode?>(null) }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.cancel_accepted_booking), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    stringResource(R.string.this_ends_the_active_booking_for_x_immedia, spaceTitle, partyLabel) +
                    stringResource(R.string.settle_anything_owed_directly_with_x, partyLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(stringResource(R.string.reason), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
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
                                Text(reason.displayName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.additional_note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CustomButton(
                        text = stringResource(R.string.keep_booking),
                        onClick = onDismiss,
                        variant = CustomButtonVariant.OUTLINED,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    CustomButton(
                        text = stringResource(R.string.cancel_booking),
                        onClick = { selectedReason?.let { onConfirm(it, note.ifBlank { null }) } },
                        variant = CustomButtonVariant.DANGER,
                        enabled = selectedReason != null,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
