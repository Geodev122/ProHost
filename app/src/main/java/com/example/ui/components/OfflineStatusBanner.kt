package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A thin, always-in-the-same-place banner surfacing ProHostRepository's existing
 * isOfflineMode/syncStatusMessage StateFlows. Rendered once in the shared app-shell
 * (ProHostNavGraph), so every top-level screen gets offline visibility for free instead
 * of each screen wiring its own collectAsState()-and-render boilerplate.
 */
@Composable
fun OfflineStatusBanner(
    isOffline: Boolean,
    syncStatusMessage: String?,
    modifier: Modifier = Modifier
) {
    ProHostStatusStrip(
        visible = isOffline,
        message = syncStatusMessage ?: "You're offline — showing cached data",
        severity = ProHostAlertSeverity.ERROR,
        icon = Icons.Default.CloudOff,
        modifier = modifier
    )
}
