package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Country
import com.example.data.model.findCountryByName
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProSpaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginAuthScreen(
    viewModel: ProSpaceViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isSignUpMode by remember { mutableStateOf(false) }

    // --- Sign In (returning member) state ---
    var signInCountry by remember { mutableStateOf(findCountryByName("Lebanon")) }
    var signInPhone by remember { mutableStateOf("") }
    var signInOtpSent by remember { mutableStateOf(false) }
    var signInOtpCode by remember { mutableStateOf("") }

    // --- Registration state ---
    var regProfilePicUri by remember { mutableStateOf<Uri?>(null) }
    var regFullName by remember { mutableStateOf("") }
    var regPhoneCountry by remember { mutableStateOf(findCountryByName("Lebanon")) }
    var regPhone by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regSpecialty by remember { mutableStateOf("") }
    var regIdDocState by remember { mutableStateOf(DocumentPickerState()) }
    var regCountry by remember { mutableStateOf(findCountryByName("Lebanon")) }
    var regGovernorateArea by remember { mutableStateOf("") }
    var regCity by remember { mutableStateOf("") }
    var regOtpSent by remember { mutableStateOf(false) }
    var regOtpCode by remember { mutableStateOf("") }
    // True once Google Sign-In succeeded but the account still has no verified phone —
    // the registration form below is then completing/linking that same account rather
    // than creating a brand-new phone-identified one.
    var isLinkingGoogleAccount by remember { mutableStateOf(false) }

    // State from ViewModel
    val isAuthenticating by viewModel.isAuthenticating.collectAsState()
    val authErrorMessage by viewModel.authErrorMessage.collectAsState()
    val authSuccessMessage by viewModel.authSuccessMessage.collectAsState()

    var localErrorMessage by remember { mutableStateOf<String?>(null) }

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

        Spacer(modifier = Modifier.height(16.dp))

        // Mode Selector: Sign In vs Register New Member
        TabRow(
            selectedTabIndex = if (isSignUpMode) 1 else 0,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = !isSignUpMode,
                onClick = {
                    isSignUpMode = false
                    localErrorMessage = null
                    viewModel.clearAuthMessages()
                },
                text = { Text("Sign In", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = isSignUpMode,
                onClick = {
                    isSignUpMode = true
                    localErrorMessage = null
                    viewModel.clearAuthMessages()
                },
                text = { Text("Register Member", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.AppRegistration, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        if (!isSignUpMode) {
            // ==================== SIGN IN FLOW (phone + SMS OTP) ====================
            ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sign In with Your Phone Number",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Verified via a one-time SMS code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isAuthenticating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!signInOtpSent) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CountryCodeSelector(
                            selectedCountry = signInCountry,
                            onCountrySelected = { signInCountry = it }
                        )
                        InputField(
                            value = signInPhone,
                            onValueChange = {
                                signInPhone = it
                                localErrorMessage = null
                            },
                            label = "Phone Number",
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
                            if (signInPhone.isBlank()) {
                                localErrorMessage = "Please enter your phone number"
                                return@CustomButton
                            }
                            val e164 = signInCountry.dialCode + signInPhone.filter { it.isDigit() }
                            viewModel.startPhoneSignIn(
                                activity = currentActivity,
                                e164Phone = e164,
                                onCodeSent = { signInOtpSent = true },
                                onAutoVerified = onLoginSuccess
                            )
                        },
                        enabled = !isAuthenticating,
                        variant = CustomButtonVariant.PRIMARY,
                        icon = Icons.AutoMirrored.Filled.Login,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_login_button")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    CustomButton(
                        text = "Sign In with Google Credential Manager",
                        onClick = {
                            val activityCtx = activity ?: context
                            viewModel.signInWithGoogleCredentialManager(
                                activityContext = activityCtx,
                                onSuccess = onLoginSuccess,
                                onNeedsPhoneVerification = { fullName, email ->
                                    isLinkingGoogleAccount = true
                                    regFullName = fullName
                                    regEmail = email
                                    isSignUpMode = true
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
                } else {
                    Text(
                        text = "Enter the 6-digit code sent to ${signInCountry.dialCode} $signInPhone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    InputField(
                        value = signInOtpCode,
                        onValueChange = { signInOtpCode = it.filter { c -> c.isDigit() }.take(6) },
                        label = "Verification Code",
                        leadingIcon = Icons.Default.Sms,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    CustomButton(
                        text = if (isAuthenticating) "Verifying..." else "Verify & Sign In",
                        onClick = {
                            val currentActivity = activity
                            if (currentActivity == null) {
                                localErrorMessage = "Unable to verify right now."
                                return@CustomButton
                            }
                            if (signInOtpCode.length < 6) {
                                localErrorMessage = "Please enter the 6-digit code"
                                return@CustomButton
                            }
                            viewModel.submitPhoneSignInCode(currentActivity, signInOtpCode, onLoginSuccess)
                        },
                        enabled = !isAuthenticating,
                        variant = CustomButtonVariant.PRIMARY,
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { signInOtpSent = false; signInOtpCode = "" }) {
                        Text("Change phone number", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            // ==================== MEMBER REGISTRATION FLOW ====================
            ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLinkingGoogleAccount) "Complete Your Profile" else "Register with Your Phone Number",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Join ProHost's verified workspace exchange network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isAuthenticating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!regOtpSent) {
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CountryCodeSelector(
                            selectedCountry = regPhoneCountry,
                            onCountrySelected = { regPhoneCountry = it }
                        )
                        InputField(
                            value = regPhone,
                            onValueChange = {
                                regPhone = it
                                localErrorMessage = null
                            },
                            label = "WhatsApp Phone Number",
                            placeholder = "70 123456",
                            leadingIcon = Icons.Default.Phone,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            enabled = !isLinkingGoogleAccount
                        )
                    }

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
                        text = if (isAuthenticating) "Sending Code..." else "Send Verification Code",
                        onClick = {
                            val currentActivity = activity
                            if (currentActivity == null) {
                                localErrorMessage = "Unable to start phone verification right now."
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
                            if (regPhone.isBlank() || regPhone.length < 6) {
                                localErrorMessage = "Please enter a valid phone number"
                                return@CustomButton
                            }
                            if (!regIdDocState.isSelected) {
                                localErrorMessage = "Please upload your ID document"
                                return@CustomButton
                            }
                            val e164 = regPhoneCountry.dialCode + regPhone.filter { it.isDigit() }
                            viewModel.startPhoneRegistration(
                                activity = currentActivity,
                                registration = ProSpaceViewModel.PendingPhoneRegistration(
                                    fullName = regFullName,
                                    email = regEmail,
                                    phoneE164 = e164,
                                    specialty = regSpecialty,
                                    country = regCountry.name,
                                    governorate = regGovernorateArea,
                                    city = regCity,
                                    profilePictureUri = regProfilePicUri,
                                    idDocumentUri = regIdDocState.uri
                                ),
                                isLinkingExistingAccount = isLinkingGoogleAccount,
                                onCodeSent = { regOtpSent = true },
                                onAutoVerified = onLoginSuccess
                            )
                        },
                        enabled = !isAuthenticating,
                        variant = CustomButtonVariant.PRIMARY,
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_registration_button")
                    )

                    if (!isLinkingGoogleAccount) {
                        Spacer(modifier = Modifier.height(10.dp))

                        CustomButton(
                            text = "Sign Up with Google Credential Manager",
                            onClick = {
                                val activityCtx = activity ?: context
                                viewModel.signInWithGoogleCredentialManager(
                                    activityContext = activityCtx,
                                    onSuccess = onLoginSuccess,
                                    onNeedsPhoneVerification = { fullName, email ->
                                        isLinkingGoogleAccount = true
                                        regFullName = fullName
                                        regEmail = email
                                    }
                                )
                            },
                            enabled = !isAuthenticating,
                            variant = CustomButtonVariant.OUTLINED,
                            icon = Icons.Default.AccountCircle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Text(
                        text = "Enter the 6-digit code sent to ${regPhoneCountry.dialCode} $regPhone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    InputField(
                        value = regOtpCode,
                        onValueChange = { regOtpCode = it.filter { c -> c.isDigit() }.take(6) },
                        label = "Verification Code",
                        leadingIcon = Icons.Default.Sms,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    CustomButton(
                        text = if (isAuthenticating) "Verifying..." else "Verify & Create Account",
                        onClick = {
                            val currentActivity = activity
                            if (currentActivity == null) {
                                localErrorMessage = "Unable to verify right now."
                                return@CustomButton
                            }
                            if (regOtpCode.length < 6) {
                                localErrorMessage = "Please enter the 6-digit code"
                                return@CustomButton
                            }
                            viewModel.submitPhoneRegistrationCode(currentActivity, regOtpCode, onLoginSuccess)
                        },
                        enabled = !isAuthenticating,
                        variant = CustomButtonVariant.PRIMARY,
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { regOtpSent = false; regOtpCode = "" }) {
                        Text("Change phone number", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
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
                    text = "Every ProHost account is verified via Firebase Phone Auth SMS — the phone number you register with is your identity on the platform.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
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
