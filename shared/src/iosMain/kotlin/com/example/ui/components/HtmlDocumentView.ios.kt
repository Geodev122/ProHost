package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebpagePreferences

/** iOS: a WKWebView with JavaScript off, like Android's WebView — [html] inline, else the page at [url]. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun HtmlDocumentView(html: String?, url: String?, modifier: Modifier) {
    UIKitView(
        factory = {
            val config = WKWebViewConfiguration().apply {
                defaultWebpagePreferences = WKWebpagePreferences().apply { allowsContentJavaScript = false }
            }
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config).apply {
                if (html != null) loadHTMLString(html, baseURL = null)
            }
        },
        modifier = modifier,
        update = { webView ->
            if (html == null && url != null) {
                val target = NSURL.URLWithString(url)
                if (target != null && webView.URL?.absoluteString != target.absoluteString) {
                    webView.loadRequest(NSURLRequest.requestWithURL(target))
                }
            }
        }
    )
}
