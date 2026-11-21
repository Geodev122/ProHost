package com.example.ui.components

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun HtmlDocumentView(html: String?, url: String?, modifier: Modifier) {
    if (html != null) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                }
            }
        )
    } else {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                }
            },
            update = { webView -> if (url != null) webView.loadUrl(url) }
        )
    }
}
