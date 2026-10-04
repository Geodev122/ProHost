package com.example.ui.components

import com.example.ui.theme.proColors
import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppUser
import com.example.ui.theme.FreshGreen
import com.example.ui.theme.Spacing
import com.example.ui.viewmodel.AuthViewModel

/**
 * KYC Verification Dialog shown when a Specialist attempts to upgrade to ProHost.
 * Prompts the user to complete all missing KYC requirements:
 *  1. Phone SMS OTP Verification (if phone is blank)
 *  2. Email OTP Code Verification (if emailVerified == false)
 *  3. Address Input — Country & City (if country or city is blank)
 */
@Composable
fun KycVerificationDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onKycCompleted: () -> Unit,
    // Sends the real Firebase verification email (ProHostViewModel.resendEmailVerification).
    onResendVerificationEmail: () -> Unit = {},
    authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { (context as? Activity) }
    // A typed profile phone isn't proof: only a phone linked in Firebase Auth counts.
    var isPhoneVerified by remember { mutableStateOf(user.hasVerifiedPhone(com.example.data.auth.PhoneLink.isLinked())) }

    var emailInput by remember { mutableStateOf(user.email) }
    var isEmailSent by remember { mutableStateOf(false) }
    // Follows the live profile, so clicking the email link elsewhere ticks this off.
    var isEmailVerified by remember(user.emailVerified) { mutableStateOf(user.emailVerified) }

    var countryInput by remember { mutableStateOf(user.country.ifBlank { "Lebanon" }) }
    var cityInput by remember { mutableStateOf(user.city.ifBlank { "Beirut" }) }

    var localError by remember { mutableStateOf<String?>(null) }

    val allComplete = isPhoneVerified && isEmailVerified && countryInput.isNotBlank() && cityInput.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("ProHost Identity Verification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Complete verification to unlock hosting privileges",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                localError?.let { msg ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            msg,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Requirement 1: Phone Verification
                KycRequirementCard(
                    title = "1. Mobile Phone Verification",
                    isComplete = isPhoneVerified,
                    subtitle = if (isPhoneVerified) "Verified (${user.phone})" else "SMS OTP verification required"
                ) {
                    if (!isPhoneVerified) {
                        // The shared phone flow (E.164 formatting, SMS code, linking to the
                        // Firebase Auth account) — the same one the booking sheet uses.
                        com.example.ui.screens.PhoneVerificationSection(
                            onVerified = {
                                isPhoneVerified = true
                                Toast.makeText(context, "Phone verified!", Toast.LENGTH_SHORT).show()
                            },
                            authViewModel = authViewModel
                        )
                    }
                }

                // Requirement 2: Email Verification
                KycRequirementCard(
                    title = "2. Email Address Verification",
                    isComplete = isEmailVerified,
                    subtitle = if (isEmailVerified) "Verified (${user.email})" else "Verification link sent to email"
                ) {
                    if (!isEmailVerified) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Email Address") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    // Used to only show a toast and never send anything.
                                    isEmailSent = true
                                    onResendVerificationEmail()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (isEmailSent) "Resend Verification Email" else "Send Verification Email") }
                        }
                    }
                }

                // Requirement 3: Address Details
                KycRequirementCard(
                    title = "3. Residence Address Details",
                    isComplete = countryInput.isNotBlank() && cityInput.isNotBlank(),
                    subtitle = "$countryInput, $cityInput"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = countryInput,
                            onValueChange = { countryInput = it },
                            label = { Text("Country") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = cityInput,
                            onValueChange = { cityInput = it },
                            label = { Text("City") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (allComplete) {
                            onKycCompleted()
                            onDismiss()
                        } else {
                            localError = "Please complete all verification steps above to proceed."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Complete Verification & Proceed to Upgrade")
                }
            }
        }
    }
}

@Composable
private fun KycRequirementCard(
    title: String,
    isComplete: Boolean,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = if (isComplete) MaterialTheme.proColors.success.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isComplete) MaterialTheme.proColors.success else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Icon(
                    imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isComplete) MaterialTheme.proColors.success else MaterialTheme.colorScheme.error
                )
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
