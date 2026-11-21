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
import androidx.compose.ui.unit.dp
import com.example.analytics.AnalyticsConsent
import com.example.shared.resources.Res
import com.example.shared.resources.advertising_id_and_no_ads_you_can_change_t
import com.example.shared.resources.google_analytics_shows_us_which_screens_se
import com.example.shared.resources.google_analytics_usage_data_tied_to_your_p
import com.example.shared.resources.prohost_better_reports_carry_your_prohost
import com.example.shared.resources.read_the_privacy_policy
import com.example.shared.resources.share_usage_analytics
import com.example.ui.util.appString
import com.example.ui.util.sharedText

/**
 * First-launch analytics opt-in, in the same native permission style as every other prompt
 * (SystemStylePermissionDialog). Both choices carry equal visual weight (no dark pattern); the
 * person must pick one, and can change it later in Profile. The notification prompt waits for it
 * (MainActivity), so two prompts never stack.
 */
@Composable
fun AnalyticsConsentDialog() {
    SystemStylePermissionDialog(
        icon = Icons.Default.Insights,
        title = permissionTitle(sharedText("share_usage_analytics_2")),
        message = appString(Res.string.google_analytics_shows_us_which_screens_se) +
            appString(Res.string.prohost_better_reports_carry_your_prohost) +
            appString(Res.string.advertising_id_and_no_ads_you_can_change_t),
        onAllow = { AnalyticsConsent.grant() },
        onDeny = { AnalyticsConsent.deny() },
        dismissible = false,
        extra = {
            TextButton(
                onClick = {
                    runCatching {
                        com.example.ui.util.PlatformActions.require().openUrl("https://pro-host.tech/privacy", newTask = true)
                    }
                }
            ) { Text(appString(Res.string.read_the_privacy_policy)) }
        }
    )
}

/** Profile › Privacy switch to grant or withdraw analytics consent at any time. */
@Composable
fun AnalyticsConsentToggle(modifier: Modifier = Modifier) {
    val state by AnalyticsConsent.state.collectAsState()
    ListItem(
        modifier = modifier,
        headlineContent = { Text(appString(Res.string.share_usage_analytics)) },
        supportingContent = {
            Text(appString(Res.string.google_analytics_usage_data_tied_to_your_p))
        },
        trailingContent = {
            Switch(
                checked = state == com.example.analytics.ConsentState.GRANTED,
                onCheckedChange = { on ->
                    if (on) AnalyticsConsent.grant() else AnalyticsConsent.deny()
                }
            )
        }
    )
}
