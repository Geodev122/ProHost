package com.example.ui.components

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.LegalDocumentVersion
import com.example.legal.LegalDocument
import com.example.legal.LegalPdfGenerator
import com.example.legal.toHtml
import com.example.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen "HTML popup" for a legal document (Privacy Policy / Terms of Use /
 * Revocation Policy / the re-rental authorization template) — renders as real HTML
 * in a WebView (matching the Google Play submission expectation of an accessible,
 * readable in-app policy page, not just a plain-text dump).
 *
 * [document]'s id decides the content source: for the 3 admin-manageable documents
 * (LegalDocumentVersion.ADMIN_MANAGED_DOC_IDS — Privacy Policy/Terms of Use/
 * Revocation Policy), this fetches the currently-published admin-uploaded HTML
 * version and loads it directly by URL, showing a placeholder if nothing has been
 * uploaded yet rather than falling back to [document]'s own Kotlin-hardcoded
 * content — Admin Console's Legal Documents card is now the sole source of truth
 * for these 3. The 4th document (the re-rental authorization template) always
 * renders [document]'s own hardcoded content as its WebView body, but its
 * "Download PDF" button prefers a real admin-uploaded PDF
 * (LegalDocumentVersion.RERENTAL_TEMPLATE_DOC_ID) when Admin Console's Legal
 * Documents card has one published, opening it directly by URL instead of
 * generating one from the hardcoded template — falling back to generation only
 * when no admin PDF has been uploaded yet, since this is a load-bearing part
 * of the listing-ownership-verification flow that must never dead-end.
 */
@Composable
fun LegalDocumentDialog(
    document: LegalDocument,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingPdf by remember { mutableStateOf(false) }

    val isAdminManaged = document.id in LegalDocumentVersion.ADMIN_MANAGED_DOC_IDS
    val isRerentalTemplate = document.id == LegalDocumentVersion.RERENTAL_TEMPLATE_DOC_ID
    // null = still loading (admin-managed only); Unit-like "checked, nothing published"
    // is represented by adminVersion staying null after hasCheckedAdminVersion flips true.
    var adminVersion by remember(document.id) { mutableStateOf<LegalDocumentVersion?>(null) }
    var hasCheckedAdminVersion by remember(document.id) { mutableStateOf(false) }
    // The re-rental template's admin-uploaded PDF (if any) — separate from
    // adminVersion above since this document's WebView body always stays the
    // Kotlin-hardcoded content; only the download button's target changes.
    var rerentalPdfVersion by remember(document.id) { mutableStateOf<LegalDocumentVersion?>(null) }

    LaunchedEffect(document.id) {
        if (isAdminManaged) {
            adminVersion = com.example.data.repository.ProHostRepository.getInstance()
                .getLatestLegalDocumentVersion(document.id)
            hasCheckedAdminVersion = true
        } else if (isRerentalTemplate) {
            rerentalPdfVersion = com.example.data.repository.ProHostRepository.getInstance()
                .getLatestLegalDocumentVersion(document.id)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(document.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isAdminManaged) {
                                TextButton(
                                    enabled = !isGeneratingPdf,
                                    onClick = {
                                        val adminPdfUrl = rerentalPdfVersion?.url
                                        if (adminPdfUrl != null) {
                                            // A real admin-uploaded template exists — open it
                                            // directly by URL, same pattern MyBookingsScreen
                                            // already uses for a Storage-hosted PDF, instead
                                            // of generating one from the hardcoded content.
                                            runCatching {
                                                context.startActivity(
                                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(adminPdfUrl))
                                                )
                                            }
                                        } else {
                                            isGeneratingPdf = true
                                            coroutineScope.launch {
                                                val file = withContext(Dispatchers.IO) {
                                                    LegalPdfGenerator.generate(context, document)
                                                }
                                                isGeneratingPdf = false
                                                if (file != null) {
                                                    runCatching {
                                                        context.startActivity(LegalPdfGenerator.buildOpenIntent(context, file))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    if (isGeneratingPdf) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download PDF (A5)", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }
                }

                when {
                    !isAdminManaged -> {
                        AndroidView(
                            modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                    loadDataWithBaseURL(null, document.toHtml(), "text/html", "utf-8", null)
                                }
                            }
                        )
                    }
                    !hasCheckedAdminVersion -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    adminVersion == null -> {
                        Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                            Text(
                                "This document hasn't been published yet — check back soon.",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        val version = adminVersion!!
                        AndroidView(
                            modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                }
                            },
                            update = { webView -> webView.loadUrl(version.url) }
                        )
                    }
                }
            }
        }
    }
}

/** Lightweight entry point: three tappable rows opening [LegalDocumentDialog] on demand. */
@Composable
fun LegalDocumentsMenu(onDismiss: () -> Unit) {
    var openDocument by remember { mutableStateOf<LegalDocument?>(null) }

    if (openDocument != null) {
        LegalDocumentDialog(document = openDocument!!, onDismiss = { openDocument = null })
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Legal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                com.example.legal.LegalContent.all.forEach { doc ->
                    Card(
                        onClick = { openDocument = doc },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(doc.shortDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.sm)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}
