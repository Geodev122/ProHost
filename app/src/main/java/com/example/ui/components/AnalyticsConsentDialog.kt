package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.analytics.AnalyticsConsent

/**
 * First-launch analytics opt-in. Both choices carry equal visual weight (no dark
 * pattern); the person must pick one, and can change it later in Profile.
 */
@Composable
fun AnalyticsConsentDialog() {
    val context = LocalContext.current
    ProHostDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.Default.Insights, contentDescription = null) },
        title = { Text("Help improve ProHost?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "With your permission we use Google Analytics to understand how the app is used — " +
                        "which screens you open, searches and filters, listings you view, and bookings and " +
                        "subscriptions you start — so we can make ProHost better."
                )
                Text(
                    "Reports are tied to your ProHost code (U-…), never your name, email or phone. " +
                        "No advertising ID is collected and nothing is used for ads. You can change this " +
                        "any time in Profile › Privacy.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://pro-host.tech/privacy"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Read the privacy policy") }
            }
        },
        confirmButton = {
            Button(onClick = { AnalyticsConsent.grant(context) }) { Text("Allow") }
        },
        dismissButton = {
            Button(
                onClick = { AnalyticsConsent.deny(context) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) { Text("Don't allow") }
        }
    )
}

/** Profile › Privacy switch to grant or withdraw analytics consent at any time. */
@Composable
fun AnalyticsConsentToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by AnalyticsConsent.state.collectAsState()
    ListItem(
        modifier = modifier,
        headlineContent = { Text("Share usage analytics") },
        supportingContent = {
            Text("Google Analytics usage data, tied to your ProHost code — never your name, email or phone. No ads.")
        },
        trailingContent = {
            Switch(
                checked = state == com.example.analytics.ConsentState.GRANTED,
                onCheckedChange = { on ->
                    if (on) AnalyticsConsent.grant(context) else AnalyticsConsent.deny(context)
                }
            )
        }
    )
}
