package com.example.ui.components

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
import com.example.shared.resources.Res
import com.example.shared.resources.complete_now
import com.example.shared.resources.needed_once_before_you_publish_listings
import com.example.shared.resources.needed_once_before_your_first_booking_requ
import com.example.shared.resources.next_x
import com.example.shared.resources.ready_to_book
import com.example.shared.resources.ready_to_host
import com.example.shared.resources.x_done
import com.example.shared.resources.x_to_do
import com.example.ui.util.appString
import com.example.ui.util.sharedText

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
        add(Step(Icons.Default.Photo, sharedText("profile_photo"), !user.profilePictureUrl.isNullOrBlank()))
        add(Step(Icons.Default.PhoneAndroid, sharedText("whatsapp_number"), user.hasWhatsAppNumber()))
        if (isHost) add(Step(Icons.Default.LocationOn, sharedText("country_city"), user.country.isNotBlank() && user.city.isNotBlank()))
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    if (isHost) appString(Res.string.ready_to_host) else appString(Res.string.ready_to_book),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                            contentDescription = if (step.done) appString(Res.string.x_done, step.label) else appString(Res.string.x_to_do, step.label),
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(step.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Text(
                appString(Res.string.next_x, next.label.lowercase()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                if (isHost) appString(Res.string.needed_once_before_you_publish_listings) else appString(Res.string.needed_once_before_your_first_booking_requ),
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
                Text(appString(Res.string.complete_now), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
