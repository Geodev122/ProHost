package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.*
import com.example.ui.components.*
import androidx.compose.foundation.BorderStroke
import com.example.ui.state.AdminUiEvent
import com.example.ui.state.AdminUiState
import com.example.ui.theme.*
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.ProHostViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =========================================================================
// TAB 5: SECURITY AUDIT & LOGS
// =========================================================================
@Composable
internal fun AdminSecurityAuditTab(
    uiState: com.example.ui.state.AdminUiState,
    adminViewModel: AdminViewModel,
    currentUser: com.example.data.model.AppUser? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            var auditExportFromMillis by remember { mutableStateOf<Long?>(null) }
            var auditExportToMillis by remember { mutableStateOf<Long?>(null) }
            val exportAuditCsvFile = rememberFileExportLauncher(mimeType = "text/csv")

            ProSurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "Security & Audit",
                        icon = Icons.Default.Shield
                    )

                    Text(
                        text = "Total Registered Logs: ${uiState.auditLogs.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DateRangePickerRow(
                        fromMillis = auditExportFromMillis,
                        toMillis = auditExportToMillis,
                        onFromChange = { auditExportFromMillis = it },
                        onToChange = { auditExportToMillis = it }
                    )

                    CustomButton(
                        text = "Export Audit Logs (CSV)",
                        onClick = {
                            val csv = adminViewModel.exportAuditLogsCsv(auditExportFromMillis, auditExportToMillis)
                            exportAuditCsvFile("prohost_audit_logs.csv", csv)
                        },
                        variant = CustomButtonVariant.PRIMARY,
                        icon = Icons.Default.Download,
                        compact = true
                    )
                }
            }
        }

        item {
            val context = LocalContext.current
            ProSurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "Legal Documents",
                        subtitle = "Upload to publish new version",
                        icon = Icons.Default.Gavel
                    )
                    data class LegalDocSlot(
                        val docId: String,
                        val title: String,
                        val mimeType: String,
                        val extension: String,
                        val noPublishedCopyIsBlocking: Boolean
                    )
                    val slots = listOf(
                        LegalDocSlot("privacy_policy", "Privacy Policy", "text/html", "html", true),
                        LegalDocSlot("terms_of_use", "Terms of Use", "text/html", "html", true),
                        LegalDocSlot("revocation_policy", "Revocation Policy", "text/html", "html", true),
                        LegalDocSlot(
                            LegalDocumentVersion.RERENTAL_TEMPLATE_DOC_ID,
                            "Re-Rental Authorization Template (PDF)",
                            "application/pdf",
                            "pdf",
                            // Never a dead end: the app falls back to generating this PDF
                            // from the built-in template when no admin version exists yet.
                            false
                        )
                    )
                    slots.forEach { slot ->
                        val current = uiState.legalDocuments[slot.docId]
                        val isUploading = uiState.isUploadingLegalDocument == slot.docId
                        val pickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.GetContent()
                        ) { uri: Uri? ->
                            if (uri != null) {
                                var resolvedName: String? = null
                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (cursor.moveToFirst() && nameIndex >= 0) resolvedName = cursor.getString(nameIndex)
                                }
                                adminViewModel.uploadLegalDocument(
                                    slot.docId, uri, resolvedName,
                                    currentUser?.email ?: "admin@prohost.app",
                                    contentType = slot.mimeType,
                                    fileExtension = slot.extension
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(slot.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (current != null) {
                                        "v${current.version} — published ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(current.uploadedAtMillis))}"
                                    } else if (slot.noPublishedCopyIsBlocking) {
                                        "Not yet published"
                                    } else {
                                        "Not yet published — app falls back to a generated PDF"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (current != null || !slot.noPublishedCopyIsBlocking) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                            CustomButton(
                                text = if (current != null) "Upload New Version" else "Upload",
                                onClick = { pickerLauncher.launch(slot.mimeType) },
                                variant = CustomButtonVariant.OUTLINED,
                                enabled = !isUploading,
                                isLoading = isUploading,
                                icon = Icons.Default.UploadFile,
                                compact = true
                            )
                        }
                    }
                }
            }
        }

        items(uiState.auditLogs, key = { it.id }) { log ->
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isHighSeverity = log.severity.contains(
                            "WARN",
                            ignoreCase = true
                        ) || log.severity.contains("CRIT", ignoreCase = true) || log.severity.contains("HIGH", ignoreCase = true)
                        Surface(
                            color = if (isHighSeverity) StatusErrorContainer else StatusSuccessContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = log.severity,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isHighSeverity) StatusError else StatusSuccess,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(log.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = log.actionType,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = log.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Actor: ${log.actorEmail} • IP: ${log.ipAddress} • ID: ${log.id}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// =========================================================================
// MODAL DIALOGS IMPLEMENTATION
// =========================================================================

/**
 * 1. Multi-Format Export Dialog
 */
@Composable
internal fun AdminExportDataDialog(
    title: String,
    content: String,
    format: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lineCount = remember(content) { content.lines().size }
    val charCount = remember(content) { content.length }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(Spacing.sm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Format: $format • $lineCount lines • $charCount characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Scrollable Content Box
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = content,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(Spacing.md)
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomButton(
                        text = "Copy Text",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(title, content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.OUTLINED,
                        icon = Icons.Default.ContentCopy
                    )

                    CustomButton(
                        text = "Share / Export",
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TEXT, content)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Export Data"))
                        },
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.PRIMARY,
                        icon = Icons.Default.Share
                    )
                }
            }
        }
    }
}
