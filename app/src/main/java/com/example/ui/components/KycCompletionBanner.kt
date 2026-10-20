package com.example.ui.components

import com.example.R
import androidx.compose.ui.res.stringResource
import com.example.ui.theme.Elevation
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppUser
import com.example.data.model.UserRole
import com.example.ui.theme.Spacing
import com.example.ui.theme.proColors

/**
 * Profile progress card: the same items the app checks in place — photo and WhatsApp number
 * ([AppUser.canTransact]), plus country and city for Pro Hosts ([AppUser.canHost]). Email is
 * not a step: email and Google sign-ups are verified by signing in. [onComplete] opens the
 * shared RequirementsSheet. Hidden once everything is done.
 */
@Composable
fun KycCompletionBanner(
    user: AppUser,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHost = user.role == UserRole.PRO_HOST
    if (if (isHost) user.canHost() else user.canTransact()) return

    data class Step(val icon: ImageVector, val label: String, val done: Boolean)

    val steps = buildList {
        add(Step(Icons.Default.Photo, "Profile photo", !user.profilePictureUrl.isNullOrBlank()))
        add(Step(Icons.Default.PhoneAndroid, "WhatsApp number", user.hasWhatsAppNumber()))
        if (isHost) add(Step(Icons.Default.LocationOn, "Country & city", user.country.isNotBlank() && user.city.isNotBlank()))
    }
    val next = steps.first { !it.done }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    if (isHost) stringResource(R.string.ready_to_host) else stringResource(R.string.ready_to_book),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.forEach { step ->
                    val color = if (step.done) {
                        MaterialTheme.proColors.onSuccessContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (step.done) Icons.Default.CheckCircle else step.icon,
                            contentDescription = if (step.done) stringResource(R.string.x_done, step.label) else stringResource(R.string.x_to_do, step.label),
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(step.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Text(
                stringResource(R.string.next_x, next.label.lowercase()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                if (isHost) stringResource(R.string.needed_once_before_you_publish_listings) else stringResource(R.string.needed_once_before_your_first_booking_requ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.complete_now), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
