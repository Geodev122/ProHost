package com.example.ui.components

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
import com.example.ui.theme.CarnationOrange
import com.example.ui.theme.FreshGreen
import com.example.ui.theme.Spacing

/**
 * Inline KYC progress card for SpecialistProfileScreen.
 * Shows the user's progress through the 3 verification steps and the next action.
 * Hidden entirely once all steps are complete.
 */
@Composable
fun KycCompletionBanner(
    user: AppUser,
    onResendVerificationEmail: () -> Unit,
    onNavigateToIdUpload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (user.isKycComplete) return

    val hasProfilePic = !user.profilePictureUrl.isNullOrBlank()
    val emailVerified = user.emailVerified
    val hasAddress = user.country.isNotBlank() && user.city.isNotBlank()

    data class KycStep(val icon: ImageVector, val label: String, val done: Boolean)

    val steps = listOf(
        KycStep(Icons.Default.Person, "Registered", true),
        KycStep(Icons.Default.Photo, "Profile photo", hasProfilePic),
        KycStep(Icons.Default.Email, "Email verified", emailVerified),
        KycStep(Icons.Default.LocationOn, "Address set", hasAddress)
    )

    val (nextTitle, nextDesc, nextAction) = when {
        !hasProfilePic -> Triple(
            "Add a profile picture",
            "Upload a photo to unlock booking features.",
            null as (() -> Unit)?
        )
        !emailVerified -> Triple(
            "Verify your email",
            "Check your inbox for a verification link. Tap below to resend if needed.",
            onResendVerificationEmail
        )
        !hasAddress -> Triple(
            "Add your address",
            "Set your country and city in the profile tab.",
            null as (() -> Unit)?
        )
        else -> Triple("", "", null)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, CarnationOrange.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = CarnationOrange, modifier = Modifier.size(22.dp))
                Text(
                    "Profile Verification",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.forEachIndexed { index, step ->
                    val isCurrent = !step.done && steps.take(index).all { it.done }
                    val color = when {
                        step.done -> FreshGreen
                        isCurrent -> CarnationOrange
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (step.done) Icons.Default.CheckCircle else step.icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            step.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            maxLines = 1
                        )
                    }
                    if (index < steps.lastIndex) {
                        Spacer(modifier = Modifier.weight(0.3f).height(1.dp))
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Text(
                "Next: $nextTitle",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                nextDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )
            if (nextAction != null) {
                OutlinedButton(
                    onClick = nextAction,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, CarnationOrange),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CarnationOrange)
                ) {
                    Text("Resend Verification Email", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
