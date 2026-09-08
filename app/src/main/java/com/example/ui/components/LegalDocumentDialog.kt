package com.example.ui.components

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import com.example.legal.LegalDocument
import com.example.legal.LegalPdfGenerator
import com.example.legal.toHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen "HTML popup" for a legal document (Privacy Policy / Terms of Use /
 * Revocation Policy) — renders [document] as real HTML in a WebView (matching the
 * Google Play submission expectation of an accessible, readable in-app policy
 * page, not just a plain-text dump) with a "Download PDF" action that generates
 * an A5-formatted PDF (LegalPdfGenerator.kt) and opens it in the system PDF
 * viewer / share sheet.
 */
@Composable
fun LegalDocumentDialog(
    document: LegalDocument,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingPdf by remember { mutableStateOf(false) }

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
                        Text(document.title, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyLarge.fontSize)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                enabled = !isGeneratingPdf,
                                onClick = {
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
                            ) {
                                if (isGeneratingPdf) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download PDF (A5)", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 4.dp),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            loadDataWithBaseURL(null, document.toHtml(), "text/html", "utf-8", null)
                        }
                    }
                )
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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Legal", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyLarge.fontSize, modifier = Modifier.padding(bottom = 8.dp))
                com.example.legal.LegalContent.all.forEach { doc ->
                    Surface(
                        onClick = { openDocument = doc },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(doc.title, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                            Text(doc.shortDescription, fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}
