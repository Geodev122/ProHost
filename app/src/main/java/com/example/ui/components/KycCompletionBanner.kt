package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppUser
import com.example.ui.theme.CarnationOrange
import com.example.ui.theme.FreshGreen
import com.example.ui.theme.OxfordBlue

/**
 * Inline KYC progress card for SpecialistProfileScreen.
 *
 * Shows the user's current level (0–3) and the single next action they need
 * to take to advance. Hidden entirely at Level 3 (fully verified).
 */
@Composable
fun KycCompletionBanner(
    user: AppUser,
    onResendVerificationEmail: () -> Unit,
    onNavigateToIdUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (user.kycLevel >= 3) return

    data class KycStep(val icon: ImageVector, val label: String, val done: Boolean)

    val steps = listOf(
        KycStep(Icons.Default.Person, "Registered", true),
        KycStep(Icons.Default.Photo, "Profile picture", user.kycLevel >= 1),
        KycStep(Icons.Default.Email, "Email verified", user.kycLevel >= 2),
        KycStep(Icons.Default.Badge, "ID verified", user.kycLevel >= 3)
    )

    val (nextTitle, nextDesc, nextAction) = when (user.kycLevel) {
        0 -> Triple(
            "Add a profile picture",
            "Upload a photo to reach Basic level and unlock booking features.",
            null as (() -> Unit)?
        )
        1 -> Triple(
            "Verify your email",
            if (!user.emailVerified) "Check your inbox for a verification link. Tap below to resend if needed."
            else "Your email is set but the verification link hasn't been clicked yet.",
            onResendVerificationEmail
        )
        2 -> Triple(
            "Upload your ID document",
            "Submit a government-issued ID to reach ID Verified level and unlock all features.",
            onNavigateToIdUpload
        )
        else -> Triple("", "", null)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = CarnationOrange, modifier = Modifier.size(20.dp))
                Text(
                    "KYC Level ${user.kycLevel} of 3",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // Step dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.forEachIndexed { index, step ->
                    val isCurrent = index == user.kycLevel
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
                        Spacer(
                            modifier = Modifier
                                .weight(0.3f)
                                .height(1.dp)
                        )
                    }
                }
            }

            // Next action
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
                val btnLabel = when (user.kycLevel) {
                    1 -> "Resend Verification Email"
                    2 -> "Upload ID Document"
                    else -> "Continue"
                }
                OutlinedButton(
                    onClick = nextAction,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CarnationOrange)
                ) {
                    Text(btnLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
