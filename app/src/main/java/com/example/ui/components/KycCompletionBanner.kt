package com.example.ui.components

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
 * Profile progress card: the same items the app checks in place — photo and verified phone
 * ([AppUser.canTransact]), plus country and city for Pro Hosts ([AppUser.canHost]). Email is
 * not a step: email and Google sign-ups are verified by signing in. [onComplete] opens the
 * shared RequirementsSheet. Hidden once everything is done.
 */
@Composable
fun KycCompletionBanner(
    user: AppUser,
    phoneLinked: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHost = user.role == UserRole.PRO_HOST
    if (if (isHost) user.canHost(phoneLinked) else user.canTransact(phoneLinked)) return

    data class Step(val icon: ImageVector, val label: String, val done: Boolean)

    val steps = buildList {
        add(Step(Icons.Default.Photo, "Profile photo", !user.profilePictureUrl.isNullOrBlank()))
        add(Step(Icons.Default.PhoneAndroid, "Verified phone", user.hasVerifiedPhone(phoneLinked)))
        if (isHost) add(Step(Icons.Default.LocationOn, "Country & city", user.country.isNotBlank() && user.city.isNotBlank()))
    }
    val next = steps.first { !it.done }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                Text(
                    if (isHost) "Ready to host" else "Ready to book",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
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
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (step.done) Icons.Default.CheckCircle else step.icon,
                            contentDescription = if (step.done) "${step.label}: done" else "${step.label}: to do",
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(step.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Text(
                "Next: ${next.label.lowercase()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                if (isHost) "Needed once before you publish listings." else "Needed once before your first booking request.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )
            OutlinedButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Text("Complete now", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
