package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Spacing

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
    AnimatedVisibility(
        visible = isOffline,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = syncStatusMessage ?: "You're offline — showing cached data",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = Spacing.sm)
            )
        }
    }
}
