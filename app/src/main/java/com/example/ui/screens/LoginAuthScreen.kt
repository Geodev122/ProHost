package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.findCountryByName
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AuthViewModel
import com.example.util.PhoneCountryDetector
import kotlinx.coroutines.launch

/**
 * Every ProHost account — new or returning — goes through the exact same three steps,
 * phone number first:
 *  1. PHONE_ENTRY — enter a phone number, request an SMS code. The dial code is
 *     auto-detected (SIM, then last known location, then locale — see
 *     [PhoneCountryDetector]) and shown as a fixed prefix inside the same field, so
 *     there is nothing to pick from a separate dropdown; a "Change" action is still
 *     there in case auto-detection picked the wrong country.
 *  2. OTP_ENTRY — enter the 6-digit code.
 *  3. REGISTRATION_FORM — shown ONLY when the verified number turns out to be brand new
 *     (Firebase's own isNewUser signal decides this, never a guess made before
 *     verification). A returning number skips straight past this and into the app.
 *
 * There is no "Sign In" vs "Register" choice anywhere in this screen anymore — asking
 * the user to declare that up front, before their number is even checked, was the bug:
 * it meant collecting a whole registration form before knowing whether the number
 * already had an account. Verifying first and branching after fixes that.
 */
private enum class AuthStep { PHONE_ENTRY, OTP_ENTRY, REGISTRATION_FORM }

private fun android.content.Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginAuthScreen(
    onLoginSuccess: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()
    var step by remember { mutableStateOf(AuthStep.PHONE_ENTRY) }

    // --- Step 1: phone entry ---
    var phoneCountry by remember { mutableStateOf(findCountryByName("Lebanon")) }
    var phoneNumber by remember { mutableStateOf("") }
    // Only true until the very first auto-detect pass finishes, so it never overwrites
    // a country the user has since changed themselves via the field's "Change" action.
    var hasAutoDetectedCountry by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Whether granted or denied, re-run detection — SIM/locale fallbacks inside
        // PhoneCountryDetector work with no permission at all, and a grant just makes
        // the location-based fallback available too.
        coroutineScope.launch {
            if (!hasAutoDetectedCountry) {
                phoneCountry = PhoneCountryDetector.detectCountry(context)
                hasAutoDetectedCountry = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!PhoneCountryDetector.hasLocationPermission(context)) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        if (!hasAutoDetectedCountry) {
            phoneCountry = PhoneCountryDetector.detectCountry(context)
            hasAutoDetectedCountry = true
        }
    }

    // --- Step 2: OTP entry ---
    var otpCode by remember { mutableStateOf("") }

    // --- Step 3: registration form (only ever shown for a brand-new phone number) ---
    var regProfilePicUri by remember { mutableStateOf<Uri?>(null) }
    var regFullName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regSpecialty by remember { mutableStateOf("") }
    var regIdDocState by remember { mutableStateOf(DocumentPickerState()) }
    var regCountry by remember { mutableStateOf(findCountryByName("Lebanon")) }
    var regGovernorateArea by remember { mutableStateOf("") }
    var regCity by remember { mutableStateOf("") }
    var tosAccepted by remember { mutableStateOf(false) }

    val isAuthenticating by authViewModel.isAuthenticating.collectAsState()
    val authErrorMessage by authViewModel.authErrorMessage.collectAsState()
    val authSuccessMessage by authViewModel.authSuccessMessage.collectAsState()

    var localErrorMessage by remember { mutableStateOf<String?>(null) }
    var showLegalDocument by remember { mutableStateOf<com.example.legal.LegalDocument?>(null) }

    val verifiedPhoneE164 = com.example.data.model.formatToE164(phoneCountry, phoneNumber)

    fun goToRegistrationForm() {
        localErrorMessage = null
        authViewModel.clearAuthMessages()
        step = AuthStep.REGISTRATION_FORM
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBackgroundGradient)
            .verticalScroll(scrollState)
            .padding(20.dp)
            .testTag("login_auth_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(Spacing.lg))

        // ProHost login lockup (checkmark + wordmark)
        Image(
            painter = painterResource(id = com.example.R.drawable.prohost_login_lockup),
            contentDescription = "ProHost Login",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 220.dp, height = 144.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        AuthStepIndicator(step = step)

        Spacer(modifier = Modifier.height(20.dp))

        // Status or Error Banners
        val displayError = localErrorMessage ?: authErrorMessage
        if (displayError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (authSuccessMessage != null) {
            Surface(
                color = StatusSuccessContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = LebaneseCedarGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = authSuccessMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusOnSuccessContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        when (step) {
            AuthStep.PHONE_ENTRY -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                AuthStepHeader(
                    icon = Icons.Default.Phone,
                    title = "Use your Phone Number",
                    subtitle = null,
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                PhoneNumberField(
                    country = phoneCountry,
                    onCountryChange = { phoneCountry = it },
                    number = phoneNumber,
                    onNumberChange = {
                        phoneNumber = it
                        localErrorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth().testTag("auth_phone_input")
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                ProPrimaryButton(
                    text = if (isAuthenticating) "Sending Code..." else "Send Verification Code",
                    onClick = {
                        val currentActivity = activity
                        if (currentActivity == null) {
                            localErrorMessage = "Unable to start phone verification right now."
                            return@ProPrimaryButton
                        }
                        if (phoneNumber.isBlank() || phoneNumber.filter { it.isDigit() }.length < 6) {
                            localErrorMessage = "Please enter a valid phone number"
                            return@ProPrimaryButton
                        }
                        authViewModel.startPhoneVerification(
                            activity = currentActivity,
                            e164Phone = verifiedPhoneE164,
                            onCodeSent = {
                                otpCode = ""
                                localErrorMessage = null
                                step = AuthStep.OTP_ENTRY
                            },
                            onVerified = { needsRegistration ->
                                if (needsRegistration) goToRegistrationForm() else onLoginSuccess()
                            }
                        )
                    },
                    enabled = !isAuthenticating,
                    icon = Icons.AutoMirrored.Filled.Login,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_login_button")
                )
            }

            AuthStep.OTP_ENTRY -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                AuthStepHeader(
                    icon = Icons.Default.Sms,
                    title = "Enter the Verification Code",
                    subtitle = "Sent via SMS to ${phoneCountry.dialCode} $phoneNumber",
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                InputField(
                    value = otpCode,
                    onValueChange = { otpCode = it.filter { c -> c.isDigit() }.take(6) },
                    label = "6-Digit Code",
                    leadingIcon = Icons.Default.Sms,
                    modifier = Modifier.fillMaxWidth().testTag("otp_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                ProPrimaryButton(
                    text = if (isAuthenticating) "Verifying..." else "Verify Code",
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
                        authViewModel.submitPhoneVerificationCode(currentActivity, otpCode) { needsRegistration ->
                            if (needsRegistration) goToRegistrationForm() else onLoginSuccess()
                        }
                    },
                    enabled = !isAuthenticating,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.fillMaxWidth().testTag("submit_otp_button")
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                TextButton(onClick = {
                    step = AuthStep.PHONE_ENTRY
                    otpCode = ""
                    localErrorMessage = null
                    authViewModel.clearAuthMessages()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Change phone number", fontSize = MaterialTheme.typography.labelMedium.fontSize, fontWeight = FontWeight.SemiBold)
                }
            }

            AuthStep.REGISTRATION_FORM -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                AuthStepHeader(
                    icon = Icons.Default.AppRegistration,
                    title = "Complete Your Profile",
                    subtitle = "Your phone number is verified — just a few more details to join ProHost",
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = StatusSuccessContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = LebaneseCedarGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = "Phone verified: ${phoneCountry.dialCode} $phoneNumber",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusOnSuccessContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfilePicturePickerField(
                        pictureUri = regProfilePicUri,
                        onPictureSelected = { regProfilePicUri = it }
                    )
                    Column {
                        Text("Profile Picture", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Optional", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                InputField(
                    value = regFullName,
                    onValueChange = {
                        regFullName = it
                        localErrorMessage = null
                    },
                    label = "Full Name",
                    placeholder = "e.g. Maya Haddad",
                    leadingIcon = Icons.Default.Person,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                InputField(
                    value = regEmail,
                    onValueChange = {
                        regEmail = it
                        localErrorMessage = null
                    },
                    label = "Email",
                    placeholder = "specialist@organization.lb",
                    leadingIcon = Icons.Default.Email,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                InputField(
                    value = regSpecialty,
                    onValueChange = {
                        regSpecialty = it
                        localErrorMessage = null
                    },
                    label = "Profession / Job Title (Optional)",
                    placeholder = "e.g. Dermatologist, Architect, Coworking Manager",
                    leadingIcon = Icons.Default.Badge,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                DocumentPickerField(
                    label = "ID Document (National ID / Passport)",
                    helperText = "Kept on file to verify your identity — PDF, JPG, or PNG",
                    state = regIdDocState,
                    onStateChanged = { regIdDocState = it },
                    modifier = Modifier.fillMaxWidth(),
                    required = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                CountryDropdownField(
                    selectedCountry = regCountry,
                    onCountrySelected = { regCountry = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                InputField(
                    value = regGovernorateArea,
                    onValueChange = {
                        regGovernorateArea = it
                        localErrorMessage = null
                    },
                    label = "Governorate / Area",
                    placeholder = "e.g. Mount Lebanon",
                    leadingIcon = Icons.Default.LocationOn,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                InputField(
                    value = regCity,
                    onValueChange = {
                        regCity = it
                        localErrorMessage = null
                    },
                    label = "City",
                    placeholder = "e.g. Beirut",
                    leadingIcon = Icons.Default.LocationCity,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // A real, required acknowledgement — this used to be a passive line of
                // text under the button with no checkbox and nothing recorded, so
                // "agreement" was never actually collected or gated on anything. Now a
                // genuine tap is required to proceed, and that acceptance is recorded
                // server-side (assignInitialRole.ts stamps tosAcceptedAtMillis/
                // consentVersion on the account — see AppUser's doc comment).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { tosAccepted = !tosAccepted },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = tosAccepted, onCheckedChange = { tosAccepted = it })
                    Column {
                        Row {
                            Text(
                                text = "I agree to the ",
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Terms of Use",
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showLegalDocument = com.example.legal.LegalContent.termsOfUse }
                            )
                        }
                        Row {
                            Text(text = "and ", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "Privacy Policy",
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showLegalDocument = com.example.legal.LegalContent.privacyPolicy }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                ProPrimaryButton(
                    text = if (isAuthenticating) "Creating Account..." else "Create Account",
                    onClick = {
                        val currentActivity = activity
                        if (currentActivity == null) {
                            localErrorMessage = "Unable to create your account right now."
                            return@ProPrimaryButton
                        }
                        if (regFullName.isBlank()) {
                            localErrorMessage = "Please enter your full name"
                            return@ProPrimaryButton
                        }
                        if (regEmail.isBlank() || !regEmail.contains("@")) {
                            localErrorMessage = "Please enter a valid email"
                            return@ProPrimaryButton
                        }
                        if (!regIdDocState.isSelected) {
                            localErrorMessage = "Please upload your ID document"
                            return@ProPrimaryButton
                        }
                        if (regGovernorateArea.isBlank()) {
                            localErrorMessage = "Please enter your governorate / area"
                            return@ProPrimaryButton
                        }
                        if (regCity.isBlank()) {
                            localErrorMessage = "Please enter your city"
                            return@ProPrimaryButton
                        }
                        if (!tosAccepted) {
                            localErrorMessage = "Please agree to the Terms of Use and Privacy Policy to continue"
                            return@ProPrimaryButton
                        }
                        authViewModel.completePendingRegistration(
                            activity = currentActivity,
                            registration = AuthViewModel.PendingPhoneRegistration(
                                fullName = regFullName,
                                email = regEmail,
                                phoneE164 = verifiedPhoneE164,
                                specialty = regSpecialty,
                                country = regCountry.name,
                                governorate = regGovernorateArea,
                                city = regCity,
                                profilePictureUri = regProfilePicUri,
                                idDocumentUri = regIdDocState.uri,
                                tosAccepted = tosAccepted
                            ),
                            onSuccess = onLoginSuccess
                        )
                    },
                    enabled = !isAuthenticating,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_registration_button")
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Security Notice Box
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = LebaneseCedarGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Your Phone Number is your gateway to the app, verified via one-time SMS code.",
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xl))
    }

    showLegalDocument?.let { doc ->
        com.example.ui.components.LegalDocumentDialog(document = doc, onDismiss = { showLegalDocument = null })
    }
}

@Composable
private fun AuthStepHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    isBusy: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Surface(
                color = OxfordBlue.copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = OxfordBlue, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun AuthStepIndicator(step: AuthStep) {
    val steps = listOf(
        Triple(AuthStep.PHONE_ENTRY, "Phone", Icons.Default.Phone),
        Triple(AuthStep.OTP_ENTRY, "Verify", Icons.Default.Sms),
        Triple(AuthStep.REGISTRATION_FORM, "Profile", Icons.Default.Person)
    )
    val currentIndex = steps.indexOfFirst { it.first == step }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (_, label, icon) ->
            val isDone = index < currentIndex
            val isCurrent = index == currentIndex
            val dotColor = when {
                isDone -> LebaneseCedarGreen
                isCurrent -> CarnationOrange
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = dotColor,
                    shape = CircleShape,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isDone) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
                        } else {
                            Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (index < steps.size - 1) {
                HorizontalDivider(
                    modifier = Modifier
                        .width(28.dp)
                        .padding(horizontal = Spacing.xs)
                        .padding(bottom = 14.dp),
                    color = if (index < currentIndex) LebaneseCedarGreen else MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

// ForgotPasswordDialog used to live here: password-based sign-in no longer exists —
// every account is phone-verified, so there is no password to reset.

// RoleSelectionCard used to live here: the 3-card "Select Access Clearance" picker on
// the login screen, orphaned once that picker itself was removed (zero remaining
// callers) — deleted rather than left as dead code.

// GoogleChooserDialog / GoogleAccountRow used to live here: a fake "account chooser"
// hardcoding the developer's own identity as an instant, password-free tap-to-become
// Admin/Owner/Professional shortcut, entirely disconnected from real Google/Firebase
// auth. Removed for the same reason as the "Quick Verified Profile Selector" button
// that opened it.
