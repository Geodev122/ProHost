package com.example.ui.components

import com.example.ui.theme.Spacing

import com.example.ui.theme.Radius
import com.example.ui.theme.Elevation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.util.sharedText

/**
 * The one look for every in-app permission or consent prompt, matching Android's own runtime permission
 * dialog (Android 12+): centred icon, centred title with the app name in bold, optional detail, and
 * full-width stacked choices of equal weight. The system dialogs (notifications, location) and these
 * pre-/post-prompts therefore read as one family instead of mixing old AlertDialog styles.
 */
@Composable
fun SystemStylePermissionDialog(
    icon: ImageVector,
    title: AnnotatedString,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    message: String? = null,
    allowLabel: String = sharedText("permission_allow"),
    denyLabel: String = sharedText("permission_dont_allow"),
    dismissible: Boolean = true,
    extra: (@Composable ColumnScope.() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDeny() },
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible)
    ) {
        Surface(
            shape = Radius.Lg,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Elevation.level3,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                extra?.invoke(this)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    PermissionChoice(allowLabel, onAllow)
                    PermissionChoice(denyLabel, onDeny)
                }
            }
        }
    }
}

@Composable
private fun PermissionChoice(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = Radius.Md,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
}

/** "Allow **ProHost** to …?" — the system dialog's title pattern. */
fun permissionTitle(action: String): AnnotatedString = buildAnnotatedString {
    append(sharedText("allow"))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("ProHost") }
    append(sharedText("to_x_2", action))
}

/**
 * Location for the "locate me" buttons through the platform's native UI. Android: the system permission prompt (after a one-time "why" in
 * [SystemStylePermissionDialog] when a rationale is due), the "open settings" prompt once it is blocked for good, and Google Play services'
 * "turn on location" dialog. iOS: Core Location's prompt (milestone C3). [onReady] runs once location can actually be read; returns the
 * action to call from the button.
 */
@Composable
expect fun rememberLocationAccess(onReady: () -> Unit): () -> Unit
