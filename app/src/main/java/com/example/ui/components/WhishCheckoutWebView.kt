package com.example.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val WHISH_REDIRECT_HOST = "hopebearer-award.com"

/**
 * Hosts Whish's checkout page inside an in-app WebView instead of an external
 * browser Intent. Whish's own hosted page still redirects to
 * https://hopebearer-award.com/payment/success|failure when the user finishes
 * (see initiateWhishPayment.ts) — this WebViewClient detects that exact
 * navigation directly, in code this app fully controls, and dismisses itself
 * immediately rather than depending on the Android App Link (assetlinks.json/
 * MainActivity.kt) reliably intercepting it, which needs an exact
 * signing-certificate fingerprint match and isn't guaranteed on every device.
 *
 * This is a responsiveness improvement only, not a trust boundary change: the
 * real confirmation always comes from ProHostViewModel's checkWhishStatus
 * polling (already running independently once payment initiates), never from
 * this redirect being observed.
 */
@Composable
fun WhishCheckoutWebView(
    collectUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Whish Pay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider()
                WhishWebViewContent(
                    collectUrl = collectUrl,
                    onRedirectDetected = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WhishWebViewContent(
    collectUrl: String,
    onRedirectDetected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loadingState = remember { mutableStateOf(true) }
    val errorState = remember { mutableStateOf<String?>(null) }

    val client = remember {
        object : WebViewClient() {
            private fun isWhishRedirect(url: Uri?): Boolean =
                url?.host == WHISH_REDIRECT_HOST && url.path?.startsWith("/payment") == true

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingState.value = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    loadingState.value = false
                    errorState.value = "Page failed to load. Check your connection and try again."
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url
                if (isWhishRedirect(url)) {
                    val isSuccess = url?.path?.contains("success") == true
                    if (isSuccess) {
                        android.widget.Toast.makeText(context, "Payment successful! Granting membership...", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Payment was unsuccessful or cancelled.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    onRedirectDetected()
                    return true
                }
                return false
            }
        }
    }

    val isLoading = loadingState.value
    val loadError = errorState.value

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = client
                    loadUrl(collectUrl)
                }
            }
        )
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }
        if (loadError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = loadError,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
