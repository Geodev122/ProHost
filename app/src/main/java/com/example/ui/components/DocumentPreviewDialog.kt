package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CredentialDocument
import com.example.data.model.DocumentStatus
import com.example.data.model.UserRole
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Inspection & Certificate Preview Dialog for Credential Documents
 */
@Composable
fun DocumentPreviewDialog(
    document: CredentialDocument,
    isAdmin: Boolean = false,
    onDismiss: () -> Unit,
    onRemoveDocument: (String) -> Unit = {},
    onApproveDocument: (String) -> Unit = {},
    onRejectDocument: (String, String) -> Unit = { _, _ -> }
) {
    var showRejectReasonPrompt by remember { mutableStateOf(false) }
    var rejectionReasonInput by remember { mutableStateOf("") }

    val formattedDate = remember(document.uploadedAt) {
        document.uploadedAt?.let {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(it))
        } ?: "Recently Uploaded"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when (document.status) {
                                DocumentStatus.VERIFIED -> StatusSuccessContainer
                                DocumentStatus.PENDING_REVIEW -> StatusWarningContainer
                                DocumentStatus.REJECTED -> StatusErrorContainer
                                DocumentStatus.NOT_UPLOADED -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (document.status) {
                                        DocumentStatus.VERIFIED -> Icons.Default.Verified
                                        DocumentStatus.PENDING_REVIEW -> Icons.Default.PendingActions
                                        DocumentStatus.REJECTED -> Icons.Default.ErrorOutline
                                        DocumentStatus.NOT_UPLOADED -> Icons.Default.Description
                                    },
                                    contentDescription = null,
                                    tint = when (document.status) {
                                        DocumentStatus.VERIFIED -> StatusSuccess
                                        DocumentStatus.PENDING_REVIEW -> AmberWarning
                                        DocumentStatus.REJECTED -> StatusError
                                        DocumentStatus.NOT_UPLOADED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = document.type.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = document.type.officialLebaneseLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Official Lebanese Accreditation Certificate Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    LebaneseCedarGreen.copy(alpha = 0.6f),
                                    OxfordBlue.copy(alpha = 0.6f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    color = SandstoneContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Watermark / Header Ribbon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = LebaneseCedarGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "PROSPACE LEBANON ACCREDITATION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = LebaneseCedarGreen,
                                    letterSpacing = 1.sp
                                )
                            }

                            ProStatusBadge(
                                type = when (document.status) {
                                    DocumentStatus.VERIFIED -> ProBadgeType.CUSTOM_SUCCESS
                                    DocumentStatus.PENDING_REVIEW -> ProBadgeType.CUSTOM_WARNING
                                    DocumentStatus.REJECTED -> ProBadgeType.CUSTOM_ERROR
                                    DocumentStatus.NOT_UPLOADED -> ProBadgeType.CUSTOM_INFO
                                },
                                customText = document.status.displayName
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Metadata Grid
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Document ID #:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(document.documentNumber.ifBlank { "Not specified" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Issuing Body:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(document.issuingAuthority.ifBlank { "Lebanese Official Registry" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Valid Until:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(document.expiryDate.ifBlank { "Permanent" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("File Scan:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${document.fileName ?: "Scan.pdf"} (${document.fileSizeKb} KB)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OxfordBlue)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Uploaded At:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formattedDate, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Cryptographic Tamper-Proof Hash
                        if (!document.verificationHash.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Cryptographic Verification Fingerprint:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = document.verificationHash,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        ),
                                        color = OxfordBlue
                                    )
                                }
                            }
                        }

                        // Reviewer or Rejection Notes
                        if (!document.reviewerNotes.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (document.status == DocumentStatus.VERIFIED) StatusSuccessContainer else StatusWarningContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (document.status == DocumentStatus.VERIFIED) Icons.Default.CheckCircle else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (document.status == DocumentStatus.VERIFIED) StatusSuccess else AmberWarning,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = document.reviewerNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (document.status == DocumentStatus.VERIFIED) StatusOnSuccessContainer else NeutralGray800
                                    )
                                }
                            }
                        }

                        if (!document.rejectionReason.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusErrorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Revision Reason: ${document.rejectionReason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusError
                                    )
                                }
                            }
                        }
                    }
                }

                // Super Admin Inspection & Decision Bar (if Admin)
                if (isAdmin) {
                    Text(
                        text = "Super Admin Review Actions",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AmberWarning
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onApproveDocument(document.id)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = { showRejectReasonPrompt = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Request Fix", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (showRejectReasonPrompt) {
                        OutlinedTextField(
                            value = rejectionReasonInput,
                            onValueChange = { rejectionReasonInput = it },
                            label = { Text("Reason for Rejection / Revision") },
                            placeholder = { Text("e.g., Scan illegible, expired date, missing stamp") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Button(
                            onClick = {
                                if (rejectionReasonInput.isNotBlank()) {
                                    onRejectDocument(document.id, rejectionReasonInput)
                                    showRejectReasonPrompt = false
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                        ) {
                            Text("Send Revision Request to Member", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Member Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onRemoveDocument(document.id)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Doc", style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Close Preview", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
