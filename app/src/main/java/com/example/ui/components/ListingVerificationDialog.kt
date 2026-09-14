package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ListingVerificationDocType
import com.example.data.model.SpaceListing
import com.example.data.storage.FirebaseStorageService
import com.example.legal.LegalContent
import com.example.ui.theme.Spacing
import com.example.ui.viewmodel.ProHostViewModel
import kotlinx.coroutines.launch

/**
 * "Get Listing Verified" — optional, reachable from the listing card's Listing
 * Verified chip (OwnerHubScreen.kt), never part of the publish flow. Two ways to
 * qualify (see SpaceListing.verificationDocUrl's doc comment): a re-rental
 * authorization signed by the real property owner (template downloadable here,
 * reusing LegalDocumentDialog/LegalPdfGenerator unmodified), or proof the Pro
 * Host is the real owner. Either upload, once on file, lets
 * ProHostViewModel.requestListingVerification auto-grant the badge — no manual
 * review, same as every other "kept on file" document in this app.
 */
@Composable
fun ListingVerificationDialog(
    space: SpaceListing,
    viewModel: ProHostViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storageService = remember { FirebaseStorageService.getInstance() }

    var chosenPath by remember { mutableStateOf<ListingVerificationDocType?>(null) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var docState by remember { mutableStateOf(DocumentPickerState()) }
    var uploadedUrl by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    // uploadAndGetUrl already catches its own failures and returns null — this
    // surfaces that instead of leaving the Submit button silently disabled with
    // no explanation, same pattern as CreateListingDialog's upload error state.
    var uploadError by remember { mutableStateOf<String?>(null) }

    if (showTemplateDialog) {
        LegalDocumentDialog(
            document = LegalContent.rerentalAuthorizationTemplate,
            onDismiss = { showTemplateDialog = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Get Listing Verified", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(space.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    "Optional — earns the Listing Verified badge shown to Specialists. Not required to keep this listing published.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.md))

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Path selector
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PathCard(
                            title = "I'm re-renting this space",
                            subtitle = "The real owner signs an authorization",
                            icon = Icons.Default.Description,
                            selected = chosenPath == ListingVerificationDocType.RERENTAL_AUTHORIZATION,
                            modifier = Modifier.weight(1f)
                        ) {
                            chosenPath = ListingVerificationDocType.RERENTAL_AUTHORIZATION
                            docState = DocumentPickerState()
                            uploadedUrl = null
                        }
                        PathCard(
                            title = "I am the space owner",
                            subtitle = "Upload your own proof of ownership",
                            icon = Icons.Default.HomeWork,
                            selected = chosenPath == ListingVerificationDocType.SELF_OWNERSHIP_PROOF,
                            modifier = Modifier.weight(1f)
                        ) {
                            chosenPath = ListingVerificationDocType.SELF_OWNERSHIP_PROOF
                            docState = DocumentPickerState()
                            uploadedUrl = null
                        }
                    }

                    when (chosenPath) {
                        ListingVerificationDocType.RERENTAL_AUTHORIZATION -> {
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    Text(
                                        "1. Download the authorization template, print it, and have the real property owner fill in their name/ID and sign it. 2. Scan or photograph the signed document and upload it below.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    OutlinedButton(onClick = { showTemplateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text("Download Authorization Template")
                                    }
                                }
                            }
                            DocumentPickerField(
                                label = "Signed Authorization Statement",
                                helperText = "PDF, JPG, or PNG",
                                state = docState,
                                onStateChanged = { newState ->
                                    docState = newState
                                    val uri = newState.uri
                                    if (uri != null) {
                                        coroutineScope.launch {
                                            isUploading = true
                                            uploadError = null
                                            val ext = newState.fileName?.substringAfterLast('.', "pdf") ?: "pdf"
                                            uploadedUrl = storageService.uploadListingVerificationDocument(space.id, uri, ext)
                                            if (uploadedUrl == null) {
                                                uploadError = "Couldn't upload that document. Check your connection and try again."
                                            }
                                            isUploading = false
                                        }
                                    } else {
                                        uploadedUrl = null
                                        uploadError = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                required = false
                            )
                        }
                        ListingVerificationDocType.SELF_OWNERSHIP_PROOF -> {
                            Text(
                                "A title deed, lease contract, or other document showing you're the real owner of this space.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            DocumentPickerField(
                                label = "Proof of Ownership",
                                helperText = "PDF, JPG, or PNG",
                                state = docState,
                                onStateChanged = { newState ->
                                    docState = newState
                                    val uri = newState.uri
                                    if (uri != null) {
                                        coroutineScope.launch {
                                            isUploading = true
                                            uploadError = null
                                            val ext = newState.fileName?.substringAfterLast('.', "pdf") ?: "pdf"
                                            uploadedUrl = storageService.uploadListingVerificationDocument(space.id, uri, ext)
                                            if (uploadedUrl == null) {
                                                uploadError = "Couldn't upload that document. Check your connection and try again."
                                            }
                                            isUploading = false
                                        }
                                    } else {
                                        uploadedUrl = null
                                        uploadError = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                required = false
                            )
                        }
                        null -> {}
                    }

                    if (isUploading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (uploadError != null) {
                        Text(
                            uploadError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                Button(
                    onClick = {
                        val docType = chosenPath ?: return@Button
                        val url = uploadedUrl ?: return@Button
                        isSubmitting = true
                        viewModel.requestListingVerification(space.id, url, docType, context)
                        onDismiss()
                    },
                    enabled = chosenPath != null && uploadedUrl != null && !isUploading && !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Submit for Listing Verified Badge")
                }
            }
        }
    }
}

@Composable
private fun PathCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
