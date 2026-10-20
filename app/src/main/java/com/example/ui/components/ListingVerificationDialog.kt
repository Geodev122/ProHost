package com.example.ui.components

import com.example.R
import androidx.compose.ui.res.stringResource
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
 * Host is the real owner. Uploading either document and tapping Submit sends it
 * to Admin for manual review (functions/src/admin/listings.ts's
 * requestListingVerification pushes every Admin, then Admin Console's Listings
 * Catalog "Pending Review" filter is where the document actually gets looked at
 * and the badge granted via setListingVerification) — not an instant grant.
 */
@Composable
fun ListingVerificationDialog(
    space: SpaceListing,
    viewModel: ProHostViewModel,
    onDismiss: () -> Unit
) {
    com.example.ui.components.TrackScreen("listing_verification", "Dialog")
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
    var submitError by remember { mutableStateOf<String?>(null) }

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
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.get_listing_verified), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(space.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.optional_submit_a_document_for_admin_revie) +
                    stringResource(R.string.this_listing_published),
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
                            title = stringResource(R.string.i_m_re_renting_this_space),
                            subtitle = stringResource(R.string.the_real_owner_signs_an_authorization),
                            icon = Icons.Default.Description,
                            selected = chosenPath == ListingVerificationDocType.RERENTAL_AUTHORIZATION,
                            modifier = Modifier.weight(1f)
                        ) {
                            chosenPath = ListingVerificationDocType.RERENTAL_AUTHORIZATION
                            docState = DocumentPickerState()
                            uploadedUrl = null
                        }
                        PathCard(
                            title = stringResource(R.string.i_am_the_space_owner),
                            subtitle = stringResource(R.string.upload_your_own_proof_of_ownership),
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
                                        stringResource(R.string.t_1_download_the_authorization_template_prin) +
                                        stringResource(R.string.name_id_and_sign_it_2_scan_or_photograph_t),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    OutlinedButton(onClick = { showTemplateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(stringResource(R.string.download_authorization_template))
                                    }
                                }
                            }
                            DocumentPickerField(
                                label = stringResource(R.string.signed_authorization_statement),
                                helperText = stringResource(R.string.pdf_jpg_or_png),
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
                                stringResource(R.string.a_title_deed_lease_contract_or_other_docum),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            DocumentPickerField(
                                label = stringResource(R.string.proof_of_ownership),
                                helperText = stringResource(R.string.pdf_jpg_or_png),
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
                    colors = proPrimaryButtonColors(),
                    onClick = {
                        val docType = chosenPath ?: return@Button
                        val url = uploadedUrl ?: return@Button
                        isSubmitting = true
                        submitError = null
                        coroutineScope.launch {
                            val success = viewModel.requestListingVerification(space.id, url, docType)
                            if (success) {
                                onDismiss()
                            } else {
                                submitError = "Couldn't reach server. Your document is saved — tap Submit again to notify admins."
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = chosenPath != null && uploadedUrl != null && !isUploading && !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.submit_for_admin_review))
                }
                if (submitError != null) {
                    Text(
                        submitError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
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
        onClick = onClick,
        modifier = modifier,
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
