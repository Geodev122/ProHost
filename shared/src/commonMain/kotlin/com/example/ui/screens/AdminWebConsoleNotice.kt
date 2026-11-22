package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.ProCard
import com.example.ui.components.ProPrimaryButton
import com.example.ui.theme.Spacing
import com.example.ui.util.PlatformActions
import com.example.ui.util.sharedText

/** The web console (owner decision, 9 Nov 2026: `pro-host.tech/console`, admin email + password). */
const val ADMIN_WEB_CONSOLE_URL = "https://pro-host.tech/console"

/**
 * What an ADMIN account sees in an app without the admin screens (iOS today, Android after the cutover; decision D1):
 * the console lives on the web, so the app points there and offers Sign out.
 */
@Composable
fun AdminWebConsoleNotice(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        ProCard(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    sharedText("admin_web_console_title"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    sharedText("admin_web_console_body"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                ProPrimaryButton(
                    text = sharedText("admin_web_console_open"),
                    onClick = { PlatformActions.current?.openUrl(ADMIN_WEB_CONSOLE_URL) },
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onSignOut) { Text(sharedText("sign_out")) }
            }
        }
    }
}
