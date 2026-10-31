package com.example.ui.components

import com.example.ui.util.uiText
import com.example.R
import androidx.compose.ui.res.stringResource
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
        title = permissionTitle(uiText(R.string.share_usage_analytics_2)),
        message = stringResource(R.string.google_analytics_shows_us_which_screens_se) +
            stringResource(R.string.prohost_better_reports_carry_your_prohost) +
            stringResource(R.string.advertising_id_and_no_ads_you_can_change_t),
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
            ) { Text(stringResource(R.string.read_the_privacy_policy)) }
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
        headlineContent = { Text(stringResource(R.string.share_usage_analytics)) },
        supportingContent = {
            Text(stringResource(R.string.google_analytics_usage_data_tied_to_your_p))
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
