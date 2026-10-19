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
import com.example.analytics.AnalyticsConsent

/**
 * First-launch analytics opt-in, in the same native permission style as every other prompt
 * (SystemStylePermissionDialog). Both choices carry equal visual weight (no dark pattern); the
 * person must pick one, and can change it later in Profile. The notification prompt waits for it
 * (MainActivity), so two prompts never stack.
 */
@Composable
fun AnalyticsConsentDialog() {
    val context = LocalContext.current
    SystemStylePermissionDialog(
        icon = Icons.Default.Insights,
        title = permissionTitle("share usage analytics"),
        message = "Google Analytics shows us which screens, searches and listings people use, so we can make " +
            "ProHost better. Reports carry your ProHost code (U-…), never your name, email or phone. No " +
            "advertising ID and no ads. You can change this any time in Profile › Privacy.",
        onAllow = { AnalyticsConsent.grant(context) },
        onDeny = { AnalyticsConsent.deny(context) },
        dismissible = false,
        extra = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://pro-host.tech/privacy"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            ) { Text("Read the privacy policy") }
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
