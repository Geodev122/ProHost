package com.example.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CredentialDocument
import com.example.data.model.DocumentType
import com.example.data.model.UserRole
import com.example.data.storage.FirebaseStorageService
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Interactive Dialog for Uploading Professional Credential Documents
 * Supports Android file picking, instant metadata capture, syndicate ID entry, and cryptographic validation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialUploadDialog(
    initialType: DocumentType? = null,
    userRole: UserRole = UserRole.PROFESSIONAL,
    onDismiss: () -> Unit,
    onDocumentUploaded: (type: DocumentType, fileName: String, fileSizeKb: Int, docNumber: String, issuingAuth: String, expiryDate: String, fileUri: String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storageService = remember { FirebaseStorageService.getInstance() }

    var selectedType by remember { mutableStateOf(initialType ?: DocumentType.SYNDICATE_CARD) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSizeKb by remember { mutableStateOf(0) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var documentNumber by remember { mutableStateOf("") }
    var issuingAuthority by remember {
        mutableStateOf(
            when (selectedType) {
                DocumentType.SYNDICATE_CARD -> "Order of Engineers & Architects (OEA) Beirut"
                DocumentType.NATIONAL_ID -> "Ministry of Interior and Municipalities - Civil Status"
                DocumentType.PRACTICE_LICENSE -> "Ministry of Public Works / Health"
                DocumentType.COMMERCIAL_REGISTER -> "Ministry of Justice - Commercial Registry"
                DocumentType.TITLE_DEED_OR_LEASE -> "General Directorate of Land Registry (Cadastre)"
                DocumentType.TAX_REGISTRATION -> "Republic of Lebanon Ministry of Finance"
            }
        )
    }
    var expiryDate by remember { mutableStateOf("2027-12-31") }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            var resolvedName: String? = null
            var resolvedSizeBytes = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) resolvedName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0) resolvedSizeBytes = cursor.getLong(sizeIndex)
                }
            }
            val name = resolvedName ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "Credential_Document_Scan.pdf")
            selectedFileName = if (name.endsWith(".pdf") || name.endsWith(".jpg") || name.endsWith(".png")) name else "$name.pdf"
            selectedFileSizeKb = if (resolvedSizeBytes > 0) (resolvedSizeBytes / 1024).toInt().coerceAtLeast(1) else 0
        }
    }

    // Update default authority when type changes
    LaunchedEffect(selectedType) {
        if (issuingAuthority.isBlank() || issuingAuthority.contains("Ministry") || issuingAuthority.contains("Order")) {
            issuingAuthority = when (selectedType) {
                DocumentType.SYNDICATE_CARD -> "Order of Engineers & Architects (OEA) Beirut"
                DocumentType.NATIONAL_ID -> "Ministry of Interior and Municipalities - Civil Status"
                DocumentType.PRACTICE_LICENSE -> "Ministry of Public Works / Health"
                DocumentType.COMMERCIAL_REGISTER -> "Ministry of Justice - Commercial Registry"
                DocumentType.TITLE_DEED_OR_LEASE -> "General Directorate of Land Registry (Cadastre)"
                DocumentType.TAX_REGISTRATION -> "Republic of Lebanon Ministry of Finance"
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
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
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Upload Credential Document",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Lebanese Specialist Identity Accreditation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isUploading
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Document Category Selector
                Text(
                    text = "Select Document Classification",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                var expandedTypeDropdown by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedTypeDropdown,
                    onExpandedChange = { if (!isUploading) expandedTypeDropdown = !expandedTypeDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedType.title,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeDropdown) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        supportingText = {
                            Text(selectedType.officialLebaneseLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTypeDropdown,
                        onDismissRequest = { expandedTypeDropdown = false }
                    ) {
                        DocumentType.values().forEach { docType ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(docType.title, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            docType.officialLebaneseLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedType = docType
                                    expandedTypeDropdown = false
                                }
                            )
                        }
                    }
                }

                // File Attachment Area
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = 1.5.dp,
                            color = if (selectedFileName != null) OxfordBlue else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .background(if (selectedFileName != null) OxfordBlueContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable(enabled = !isUploading) {
                            filePickerLauncher.launch("application/pdf,image/*")
                        },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedFileName == null) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Tap to Select Document / PDF / Image",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Supported: PDF, JPG, PNG (Max 10MB)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = OxfordBlue,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedFileName ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${selectedFileSizeKb} KB • Ready for Upload",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OxfordBlue
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        selectedFileName = null
                                        selectedFileUri = null
                                    },
                                    enabled = !isUploading
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove File", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // Document Metadata Fields
                OutlinedTextField(
                    value = documentNumber,
                    onValueChange = { documentNumber = it },
                    label = { Text("Document ID / Syndicate License #") },
                    placeholder = { Text(selectedType.placeholderDocNumber) },
                    leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = !isUploading
                )

                OutlinedTextField(
                    value = issuingAuthority,
                    onValueChange = { issuingAuthority = it },
                    label = { Text("Issuing Authority / Syndicate Branch") },
                    leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = !isUploading
                )

                OutlinedTextField(
                    value = expiryDate,
                    onValueChange = { expiryDate = it },
                    label = { Text("Document Expiry Date (YYYY-MM-DD)") },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = !isUploading
                )

                // Upload Progress Bar
                AnimatedVisibility(visible = isUploading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { uploadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "Encrypting and transmitting credentials... ${(uploadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isUploading
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val fileUri = selectedFileUri
                            if (fileUri == null || selectedFileName == null) {
                                Toast.makeText(context, "Please choose a file to upload", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (documentNumber.isBlank()) {
                                Toast.makeText(context, "Please enter the document / license number", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            if (uid == null) {
                                Toast.makeText(context, "You must be signed in to upload documents", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            coroutineScope.launch {
                                isUploading = true
                                uploadProgress = 0f
                                val extension = selectedFileName?.substringAfterLast('.', "pdf") ?: "pdf"
                                val docId = "${selectedType.name}-${System.currentTimeMillis()}"
                                val downloadUrl = storageService.uploadCredentialDocument(
                                    uid = uid,
                                    docId = docId,
                                    fileUri = fileUri,
                                    fileExtension = extension,
                                    onProgress = { uploadProgress = it }
                                )
                                isUploading = false

                                if (downloadUrl == null) {
                                    Toast.makeText(context, "Upload failed. Check your connection and try again.", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                onDocumentUploaded(
                                    selectedType,
                                    selectedFileName ?: "document.pdf",
                                    selectedFileSizeKb,
                                    documentNumber,
                                    issuingAuthority,
                                    expiryDate,
                                    downloadUrl
                                )
                                Toast.makeText(context, "Document uploaded for compliance review!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isUploading && selectedFileName != null
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isUploading) "Uploading..." else "Upload Document")
                    }
                }
            }
        }
    }
}
