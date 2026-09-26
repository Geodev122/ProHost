package com.example.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.findCountryByName
import com.example.data.model.formatToE164
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AuthViewModel

private enum class KycStep { PHONE_ENTRY, OTP_ENTRY }

private val KycStepSaver = Saver<KycStep, String>(
    save = { it.name },
    restore = { KycStep.valueOf(it) }
)

/**
 * KYC (Know Your Customer) screen that gates booking and listing features.
 *
 * The user (already Firebase Auth-authenticated via email/Google/phone) adds a
 * verified phone number that is linked to their Firebase Auth account.
 * On completion, [onKycComplete] is called so the navigation graph can dismiss
 * this screen and return the user to the feature they were trying to access.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KycScreen(
    onKycComplete: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity: Activity? = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }

    var step by rememberSaveable(stateSaver = KycStepSaver) { mutableStateOf(KycStep.PHONE_ENTRY) }
    var phoneCountry by rememberSaveable(
        stateSaver = Saver(save = { it.name }, restore = { findCountryByName(it) })
    ) { mutableStateOf(findCountryByName("Lebanon")) }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var resendCountdownSeconds by rememberSaveable { mutableStateOf(0) }
    var localErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(resendCountdownSeconds) {
        if (resendCountdownSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            resendCountdownSeconds -= 1
        }
    }

    val isAuthenticating by authViewModel.isAuthenticating.collectAsState()
    val authErrorMessage by authViewModel.authErrorMessage.collectAsState()
    val e164Phone = formatToE164(phoneCountry, phoneNumber)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone Verification", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onDismiss != null) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step progress
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val steps = listOf("Phone" to Icons.Default.Phone, "Verify" to Icons.Default.Sms)
                steps.forEachIndexed { index, (label, icon) ->
                    val isCurrent = (index == 0 && step == KycStep.PHONE_ENTRY) ||
                            (index == 1 && step == KycStep.OTP_ENTRY)
                    val color = if (isCurrent) CarnationOrange else MaterialTheme.colorScheme.outlineVariant
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(color = color, shape = CircleShape, modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index == 0) {
                        HorizontalDivider(
                            modifier = Modifier.width(32.dp).padding(horizontal = Spacing.xs).padding(bottom = 16.dp)
                        )
                    }
                }
            }

            val isLoading = isAuthenticating
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }

            // Error banner
            val errorMessage = localErrorMessage ?: authErrorMessage
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            when (step) {
                KycStep.PHONE_ENTRY -> ModernCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(20.dp),
                    elevation = 2.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                        Surface(
                            color = OxfordBlue.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = OxfordBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Add Your Phone Number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Required to access booking and listing features",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "We verify your phone number once to prevent fraud and enable booking notifications.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    PhoneNumberField(
                        country = phoneCountry,
                        onCountryChange = { phoneCountry = it },
                        number = phoneNumber,
                        onNumberChange = { phoneNumber = it; localErrorMessage = null },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(Spacing.lg))

                    ProPrimaryButton(
                        text = if (isAuthenticating) "Sending Code..." else "Send Verification Code",
                        onClick = {
                            val currentActivity = activity
                            if (currentActivity == null) {
                                localErrorMessage = "Unable to start verification right now."
                                return@ProPrimaryButton
                            }
                            if (phoneNumber.isBlank() || phoneNumber.filter { it.isDigit() }.length < 6) {
                                localErrorMessage = "Please enter a valid phone number"
                                return@ProPrimaryButton
                            }
                            authViewModel.startKycPhoneVerification(
                                activity = currentActivity,
                                e164Phone = e164Phone,
                                onCodeSent = {
                                    otpCode = ""
                                    localErrorMessage = null
                                    resendCountdownSeconds = 30
                                    step = KycStep.OTP_ENTRY
                                },
                                onError = { msg -> localErrorMessage = msg }
                            )
                        },
                        enabled = !isAuthenticating,
                        icon = Icons.Default.Sms,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                KycStep.OTP_ENTRY -> ModernCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(20.dp),
                    elevation = 2.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                        Surface(
                            color = OxfordBlue.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Sms, contentDescription = null, tint = OxfordBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Enter Verification Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Code sent to ${phoneCountry.dialCode} $phoneNumber",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    InputField(
                        value = otpCode,
                        onValueChange = { otpCode = it.filter { c -> c.isDigit() }.take(6) },
                        label = "6-Digit Code",
                        leadingIcon = Icons.Default.Sms,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(Spacing.lg))

                    ProPrimaryButton(
                        text = if (isAuthenticating) "Verifying..." else "Verify & Link Phone",
                        onClick = {
                            val currentActivity = activity
                            if (currentActivity == null) {
                                localErrorMessage = "Unable to verify right now."
                                return@ProPrimaryButton
                            }
                            if (otpCode.length < 6) {
                                localErrorMessage = "Please enter the 6-digit code"
                                return@ProPrimaryButton
                            }
                            authViewModel.linkKycPhone(
                                activity = currentActivity,
                                smsCode = otpCode,
                                onSuccess = {
                                    authViewModel.clearAuthMessages()
                                    onKycComplete()
                                },
                                onError = { msg -> localErrorMessage = msg }
                            )
                        },
                        enabled = !isAuthenticating,
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    if (resendCountdownSeconds > 0) {
                        Text(
                            text = "Resend code in ${resendCountdownSeconds}s",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        TextButton(
                            onClick = {
                                val currentActivity = activity
                                if (currentActivity == null) return@TextButton
                                localErrorMessage = null
                                authViewModel.startKycPhoneVerification(
                                    activity = currentActivity,
                                    e164Phone = e164Phone,
                                    onCodeSent = {
                                        otpCode = ""
                                        resendCountdownSeconds = 30
                                    },
                                    onError = { msg -> localErrorMessage = msg }
                                )
                            },
                            enabled = !isAuthenticating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Resend Code", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    TextButton(
                        onClick = {
                            step = KycStep.PHONE_ENTRY
                            otpCode = ""
                            localErrorMessage = null
                            authViewModel.clearAuthMessages()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Change phone number", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
