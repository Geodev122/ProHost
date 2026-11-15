package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.Spacing
import com.example.ui.viewmodel.ProHostViewModel

/** Reasons as the server knows them (`functions/src/reports/reportRules.ts` REPORT_REASONS), with their labels. */
private val REPORT_REASONS = listOf(
    "MISLEADING" to R.string.report_reason_misleading,
    "SCAM" to R.string.report_reason_scam,
    "OFFENSIVE" to R.string.report_reason_offensive,
    "UNSAFE" to R.string.report_reason_unsafe,
    "HARASSMENT" to R.string.report_reason_harassment,
    "OTHER" to R.string.report_reason_other,
)

/**
 * Report and Block for a listing or a person (App Review 1.2, approved mockup 14 Nov 2026): the two menu items of the
 * three-dot menu, the report sheet, the "Report sent" note and the block confirmation. Place it inside the Box that
 * holds the three-dot button; [menuExpanded] is that button's state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportBlockMenu(
    viewModel: ProHostViewModel,
    target: ProHostViewModel.ReportTarget,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
) {
    val isListing = target.listingId != null
    var showReport by remember { mutableStateOf(false) }
    var showSent by remember { mutableStateOf(false) }
    var showBlock by remember { mutableStateOf(false) }

    DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(if (isListing) R.string.report_this_listing else R.string.report_this_person)) },
            onClick = { onMenuDismiss(); showReport = true },
            leadingIcon = { Icon(Icons.Default.Flag, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(if (isListing) R.string.block_this_host else R.string.block_this_person)) },
            onClick = { onMenuDismiss(); showBlock = true },
            leadingIcon = { Icon(Icons.Default.Block, null) }
        )
    }

    if (showReport) {
        TrackScreen("report_sheet")
        var reason by remember { mutableStateOf<String?>(null) }
        var details by remember { mutableStateOf("") }
        var alsoBlock by remember { mutableStateOf(false) }
        var sending by remember { mutableStateOf(false) }
        ProHostBottomSheet(onDismissRequest = { if (!sending) showReport = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(stringResource(if (isListing) R.string.report_this_listing else R.string.report_this_person), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.report_why), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    REPORT_REASONS.forEach { (key, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(selected = reason == key, role = Role.RadioButton, onClick = { reason = key }).padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            RadioButton(selected = reason == key, onClick = null)
                            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                InputField(
                    value = details,
                    onValueChange = { details = it.take(500) },
                    label = stringResource(R.string.report_details_optional),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 4
                )
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(value = alsoBlock, role = Role.Checkbox, onValueChange = { alsoBlock = it }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Checkbox(checked = alsoBlock, onCheckedChange = null)
                    Text(stringResource(if (isListing) R.string.report_also_block_host else R.string.report_also_block_person), style = MaterialTheme.typography.bodyMedium)
                }
                ProPrimaryButton(
                    text = stringResource(R.string.send_report),
                    onClick = {
                        val picked = reason ?: return@ProPrimaryButton
                        sending = true
                        viewModel.sendReport(target, picked, details, alsoBlock) { ok ->
                            sending = false
                            if (ok) { showReport = false; showSent = true }
                        }
                    },
                    enabled = reason != null && !sending,
                    isLoading = sending,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showSent) {
        AlertDialog(
            onDismissRequest = { showSent = false },
            title = { Text(stringResource(R.string.report_sent)) },
            text = { Text(stringResource(if (isListing) R.string.report_sent_body_host else R.string.report_sent_body_person)) },
            confirmButton = { TextButton(onClick = { showSent = false }) { Text(stringResource(R.string.done)) } }
        )
    }

    if (showBlock) {
        var blocking by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!blocking) showBlock = false },
            title = {
                Text(if (target.name.isBlank()) stringResource(if (isListing) R.string.block_this_host else R.string.block_this_person) + "?"
                    else stringResource(R.string.block_x_question, target.name))
            },
            text = { Text(stringResource(R.string.block_explainer)) },
            confirmButton = {
                TextButton(enabled = !blocking, onClick = {
                    blocking = true
                    viewModel.blockPerson(target) { blocking = false; showBlock = false }
                }) { Text(stringResource(R.string.block), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !blocking, onClick = { showBlock = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** Profile › More › Blocked people: the list with Unblock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedPeopleSheet(viewModel: ProHostViewModel, onDismiss: () -> Unit) {
    TrackScreen("blocked_people")
    var people by remember { mutableStateOf<List<ProHostViewModel.BlockedPerson>?>(null) }
    LaunchedEffect(Unit) { people = viewModel.loadBlockedPeople() }
    ProHostBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(stringResource(R.string.blocked_people), style = MaterialTheme.typography.titleLarge)
            val list = people
            when {
                list == null -> androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.padding(Spacing.md))
                list.isEmpty() -> Text(stringResource(R.string.blocked_people_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> list.forEach { person ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(person.name.ifBlank { stringResource(R.string.blocked_person_unnamed) }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            viewModel.unblockPerson(person.uid) { ok -> if (ok) people = people?.filterNot { it.uid == person.uid } }
                        }) { Text(stringResource(R.string.unblock)) }
                    }
                }
            }
        }
    }
}
