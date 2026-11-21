package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A read-only web view for legal documents, JavaScript off: [html] rendered inline, or the page at [url] when [html] is null.
 * Android: `WebView` in `AndroidView` (the code LegalDocumentDialog had); iOS: WKWebView in C3 (empty until then).
 */
@Composable
expect fun HtmlDocumentView(html: String?, url: String?, modifier: Modifier)
