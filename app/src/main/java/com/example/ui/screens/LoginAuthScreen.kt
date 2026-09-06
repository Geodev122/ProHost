package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.findCountryByName
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProSpaceViewModel

/**
 * Every ProHost account — new or returning — goes through the exact same three steps,
 * phone number first:
 *  1. PHONE_ENTRY — enter a WhatsApp number, request an SMS code.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginAuthScreen(
    viewModel: ProSpaceViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var step by remember { mutableStateOf(AuthStep.PHONE_ENTRY) }

    // --- Step 1: phone entry ---
    var phoneCountry by remember { mutableStateOf(findCountryByName("Lebanon")) }
    var phoneNumber by remember { mutableStateOf("") }

    // --- Step 2: OTP entry ---
    var otpCode by remember { mutableStateOf("") }

    // True once Google Sign-In succeeded but the account still has no verified phone —
    // the phone step below then links that same account rather than resolving/creating
    // a brand-new phone-identified one, and always lands on the registration form after
    // (a Google identity is missing too many required fields to skip it).
    var isLinkingGoogleAccount by remember { mutableStateOf(false) }

    // --- Step 3: registration form (only ever shown for a brand-new phone number) ---
    var regProfilePicUri by remember { mutableStateOf<Uri?>(null) }
    var regFullName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regSpecialty by remember { mutableStateOf("") }
    var regIdDocState by remember { mutableStateOf(DocumentPickerState()) }
    var regCountry by remember { mutableStateOf(findCountryByName("Lebanon")) }
    var regGovernorateArea by remember { mutableStateOf("") }
    var regCity by remember { mutableStateOf("") }

    val isAuthenticating by viewModel.isAuthenticating.collectAsState()
    val authErrorMessage by viewModel.authErrorMessage.collectAsState()
    val authSuccessMessage by viewModel.authSuccessMessage.collectAsState()

    var localErrorMessage by remember { mutableStateOf<String?>(null) }

    val verifiedPhoneE164 = phoneCountry.dialCode + phoneNumber.filter { it.isDigit() }

    fun goToRegistrationForm() {
        localErrorMessage = null
        viewModel.clearAuthMessages()
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
        Spacer(modifier = Modifier.height(16.dp))

        // ProSpace Brand Header
        Surface(
            color = OxfordBlue,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .size(76.dp)
                .border(1.5.dp, CarnationOrange, RoundedCornerShape(20.dp)),
            shadowElevation = 6.dp
        ) {
            Image(
                painter = painterResource(id = com.example.R.drawable.ic_prospace_logo_brand),
                contentDescription = "ProHost Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "ProHost",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Specialist Workspace & Office Rental Exchange",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        AuthStepIndicator(step = step)

        Spacer(modifier = Modifier.height(20.dp))

        // Status or Error Banners
        val displayError = localErrorMessage ?: authErrorMessage
        if (displayError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = LebaneseCedarGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                AuthStepHeader(
                    icon = Icons.Default.Phone,
                    title = if (isLinkingGoogleAccount) "Verify Your WhatsApp Number" else "Continue with Your Phone Number",
                    subtitle = if (isLinkingGoogleAccount) {
                        "Almost done — one real SMS code finishes setting up your account"
                    } else {
                        "One number for sign-in and registration — verified via a one-time SMS code"
                    },
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CountryCodeSelector(
                        selectedCountry = phoneCountry,
                        onCountrySelected = { phoneCountry = it }
                    )
                    InputField(
                        value = phoneNumber,
                        onValueChange = {
                            phoneNumber = it
                            localErrorMessage = null
                        },
                        label = "WhatsApp Phone Number",
                        placeholder = "70 123456",
                        leadingIcon = Icons.Default.Phone,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("auth_phone_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                CustomButton(
                    text = if (isAuthenticating) "Sending Code..." else "Send Verification Code",
                    onClick = {
                        val currentActivity = activity
                        if (currentActivity == null) {
                            localErrorMessage = "Unable to start phone verification right now."
                            return@CustomButton
                        }
                        if (phoneNumber.isBlank() || phoneNumber.filter { it.isDigit() }.length < 6) {
                            localErrorMessage = "Please enter a valid phone number"
                            return@CustomButton
                        }
                        viewModel.startPhoneVerification(
                            activity = currentActivity,
                            e164Phone = verifiedPhoneE164,
                            isLinkingExistingAccount = isLinkingGoogleAccount,
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
                    variant = CustomButtonVariant.PRIMARY,
                    icon = Icons.AutoMirrored.Filled.Login,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_login_button")
                )

                if (!isLinkingGoogleAccount) {
                    Spacer(modifier = Modifier.height(14.dp))
                    AuthDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    CustomButton(
                        text = "Continue with Google",
                        onClick = {
                            val activityCtx = activity ?: context
                            viewModel.signInWithGoogleCredentialManager(
                                activityContext = activityCtx,
                                onSuccess = onLoginSuccess,
                                onNeedsPhoneVerification = { fullName, email ->
                                    isLinkingGoogleAccount = true
                                    regFullName = fullName
                                    regEmail = email
                                    phoneNumber = ""
                                }
                            )
                        },
                        enabled = !isAuthenticating,
                        variant = CustomButtonVariant.OUTLINED,
                        icon = Icons.Default.AccountCircle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_sign_in_button")
                    )
                }
            }

            AuthStep.OTP_ENTRY -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                AuthStepHeader(
                    icon = Icons.Default.Sms,
                    title = "Enter the Verification Code",
                    subtitle = "Sent via SMS to ${phoneCountry.dialCode} $phoneNumber",
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(16.dp))

                InputField(
                    value = otpCode,
                    onValueChange = { otpCode = it.filter { c -> c.isDigit() }.take(6) },
                    label = "6-Digit Code",
                    leadingIcon = Icons.Default.Sms,
                    modifier = Modifier.fillMaxWidth().testTag("otp_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                CustomButton(
                    text = if (isAuthenticating) "Verifying..." else "Verify Code",
                    onClick = {
                        val currentActivity = activity
                        if (currentActivity == null) {
                            localErrorMessage = "Unable to verify right now."
                            return@CustomButton
                        }
                        if (otpCode.length < 6) {
                            localErrorMessage = "Please enter the 6-digit code"
                            return@CustomButton
                        }
                        viewModel.submitPhoneVerificationCode(currentActivity, otpCode) { needsRegistration ->
                            if (needsRegistration) goToRegistrationForm() else onLoginSuccess()
                        }
                    },
                    enabled = !isAuthenticating,
                    variant = CustomButtonVariant.PRIMARY,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.fillMaxWidth().testTag("submit_otp_button")
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = {
                    step = AuthStep.PHONE_ENTRY
                    otpCode = ""
                    localErrorMessage = null
                    viewModel.clearAuthMessages()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Change phone number", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            AuthStep.REGISTRATION_FORM -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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
                    shape = RoundedCornerShape(10.dp),
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Phone verified: ${phoneCountry.dialCode} $phoneNumber",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusOnSuccessContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(20.dp))

                CustomButton(
                    text = if (isAuthenticating) "Creating Account..." else "Create Account",
                    onClick = {
                        val currentActivity = activity
                        if (currentActivity == null) {
                            localErrorMessage = "Unable to create your account right now."
                            return@CustomButton
                        }
                        if (regFullName.isBlank()) {
                            localErrorMessage = "Please enter your full name"
                            return@CustomButton
                        }
                        if (regEmail.isBlank() || !regEmail.contains("@")) {
                            localErrorMessage = "Please enter a valid email"
                            return@CustomButton
                        }
                        if (!regIdDocState.isSelected) {
                            localErrorMessage = "Please upload your ID document"
                            return@CustomButton
                        }
                        if (regGovernorateArea.isBlank()) {
                            localErrorMessage = "Please enter your governorate / area"
                            return@CustomButton
                        }
                        if (regCity.isBlank()) {
                            localErrorMessage = "Please enter your city"
                            return@CustomButton
                        }
                        viewModel.completePendingRegistration(
                            activity = currentActivity,
                            registration = ProSpaceViewModel.PendingPhoneRegistration(
                                fullName = regFullName,
                                email = regEmail,
                                phoneE164 = verifiedPhoneE164,
                                specialty = regSpecialty,
                                country = regCountry.name,
                                governorate = regGovernorateArea,
                                city = regCity,
                                profilePictureUri = regProfilePicUri,
                                idDocumentUri = regIdDocState.uri
                            ),
                            onSuccess = onLoginSuccess
                        )
                    },
                    enabled = !isAuthenticating,
                    variant = CustomButtonVariant.PRIMARY,
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
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
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
                    text = "Every ProHost account is verified via Firebase Phone Auth SMS — the phone number you enter above is checked first, before anything else, and is your identity on the platform.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AuthStepHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
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
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun AuthDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = "  OR  ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
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
                Spacer(modifier = Modifier.height(4.dp))
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
                        .padding(horizontal = 4.dp)
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
