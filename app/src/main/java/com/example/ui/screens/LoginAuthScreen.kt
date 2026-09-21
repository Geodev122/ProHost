package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.util.findActivity
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.auth.GoogleSignInHelper
import com.example.data.model.findCountryByName
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AuthViewModel
import com.example.util.PhoneCountryDetector
import kotlinx.coroutines.launch

/**
 * Unified auth screen covering all three flows:
 *
 * LOGIN (returning user with PIN):
 *   PHONE_ENTRY → PIN_ENTRY → done
 *
 * SIGNUP (new phone number):
 *   PHONE_ENTRY → OTP_ENTRY → REGISTRATION_FORM → SET_PIN → done
 *
 * FORGOT PIN:
 *   PIN_ENTRY → (Forgot PIN?) → OTP_ENTRY → SET_PIN → done
 *
 * The phone number is the only identifier — there is no username/password.
 */
private enum class AuthStep {
    PHONE_ENTRY,
    OTP_ENTRY,
    PIN_ENTRY,
    REGISTRATION_FORM,
    SET_PIN
}

private const val OTP_RESEND_COOLDOWN_SECONDS = 30

private val AuthStepSaver = Saver<AuthStep, String>(
    save = { it.name },
    restore = { AuthStep.valueOf(it) }
)

private val CountrySaver = Saver<com.example.data.model.Country, String>(
    save = { it.name },
    restore = { findCountryByName(it) }
)

private val DocumentPickerStateSaver = Saver<DocumentPickerState, List<String?>>(
    save = { listOf(it.uri?.toString(), it.fileName) },
    restore = { DocumentPickerState(it.getOrNull(0)?.let(Uri::parse), it.getOrNull(1)) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginAuthScreen(
    onLoginSuccess: () -> Unit,
    resumeAtRegistration: Boolean = false,
    resumePhoneE164: String? = null,
    onCancelResume: (() -> Unit)? = null,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()

    var step by rememberSaveable(stateSaver = AuthStepSaver) {
        mutableStateOf(if (resumeAtRegistration) AuthStep.REGISTRATION_FORM else AuthStep.PHONE_ENTRY)
    }

    // --- Phone entry ---
    var phoneCountry by rememberSaveable(stateSaver = CountrySaver) { mutableStateOf(findCountryByName("Lebanon")) }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var hasAutoDetectedCountry by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasAutoDetectedCountry) {
            phoneCountry = PhoneCountryDetector.detectCountry(context)
            hasAutoDetectedCountry = true
        }
    }

    val verifiedPhoneE164 = resumePhoneE164 ?: com.example.data.model.formatToE164(phoneCountry, phoneNumber)

    // --- OTP entry ---
    var otpCode by rememberSaveable { mutableStateOf("") }
    var resendCountdownSeconds by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(resendCountdownSeconds) {
        if (resendCountdownSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            resendCountdownSeconds -= 1
        }
    }

    // --- PIN entry (login) ---
    var pinCode by rememberSaveable { mutableStateOf("") }
    var pinVisible by rememberSaveable { mutableStateOf(false) }

    // --- SET_PIN step ---
    var newPin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var newPinVisible by rememberSaveable { mutableStateOf(false) }
    // Whether we entered SET_PIN after a forgot-PIN OTP (vs. fresh signup)
    var isForgotPinReset by rememberSaveable { mutableStateOf(false) }

    // --- Google Sign-In ---
    var isGoogleRegistrationFlow by rememberSaveable { mutableStateOf(false) }
    // These must be declared before googleSignInLauncher because the launcher lambda captures them.
    var regFullName by rememberSaveable { mutableStateOf("") }
    var regEmail by rememberSaveable { mutableStateOf("") }
    var localErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val currentActivity = activity ?: return@rememberLauncherForActivityResult
            authViewModel.handleGoogleSignInResult(
                activity = currentActivity,
                data = result.data,
                onSuccess = onLoginSuccess,
                onNeedsRegistration = {
                    // Pre-fill form from Google profile
                    val profile = authViewModel.pendingGoogleProfile
                    regFullName = profile?.displayName ?: ""
                    regEmail = profile?.email ?: ""
                    isGoogleRegistrationFlow = true
                    step = AuthStep.REGISTRATION_FORM
                },
                onError = { msg -> localErrorMessage = msg }
            )
        }
    }

    // --- Registration form ---
    var regProfilePicUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    // regFullName and regEmail declared above (before googleSignInLauncher)
    var regSpecialty by rememberSaveable { mutableStateOf("") }
    var regIdDocState by rememberSaveable(stateSaver = DocumentPickerStateSaver) { mutableStateOf(DocumentPickerState()) }
    var regCountry by rememberSaveable(stateSaver = CountrySaver) { mutableStateOf(findCountryByName("Lebanon")) }
    var regGovernorateArea by rememberSaveable { mutableStateOf("") }
    var regCity by rememberSaveable { mutableStateOf("") }
    var tosAccepted by rememberSaveable { mutableStateOf(false) }

    val isAuthenticating by authViewModel.isAuthenticating.collectAsState()
    val authErrorMessage by authViewModel.authErrorMessage.collectAsState()
    val authSuccessMessage by authViewModel.authSuccessMessage.collectAsState()
    // localErrorMessage declared above (before googleSignInLauncher)
    var showLegalDocument by remember { mutableStateOf<com.example.legal.LegalDocument?>(null) }

    fun clearErrors() {
        localErrorMessage = null
        authViewModel.clearAuthMessages()
    }

    fun startOtpForSignup() {
        val currentActivity = activity ?: run { localErrorMessage = "Unable to start verification right now."; return }
        authViewModel.startPhoneVerification(
            activity = currentActivity,
            e164Phone = verifiedPhoneE164,
            purpose = AuthViewModel.OtpPurpose.SIGNUP,
            onCodeSent = {
                otpCode = ""
                clearErrors()
                resendCountdownSeconds = OTP_RESEND_COOLDOWN_SECONDS
                step = AuthStep.OTP_ENTRY
            },
            onVerified = { needsRegistration ->
                if (needsRegistration) step = AuthStep.REGISTRATION_FORM else step = AuthStep.SET_PIN
            }
        )
    }

    fun startOtpForPinReset() {
        val currentActivity = activity ?: run { localErrorMessage = "Unable to start verification right now."; return }
        isForgotPinReset = true
        authViewModel.startPhoneVerification(
            activity = currentActivity,
            e164Phone = verifiedPhoneE164,
            purpose = AuthViewModel.OtpPurpose.PIN_RESET,
            onCodeSent = {
                otpCode = ""
                clearErrors()
                resendCountdownSeconds = OTP_RESEND_COOLDOWN_SECONDS
                step = AuthStep.OTP_ENTRY
            },
            onVerified = {
                step = AuthStep.SET_PIN
            }
        )
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
        Spacer(modifier = Modifier.height(40.dp))

        Image(
            painter = painterResource(id = com.example.R.drawable.prohost_login_lockup),
            contentDescription = "ProHost Login",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 220.dp, height = 144.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        AuthStepIndicator(step = step, isForgotPinReset = isForgotPinReset)

        Spacer(modifier = Modifier.height(20.dp))

        // Error / success banners
        val displayError = localErrorMessage ?: authErrorMessage
        if (displayError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(text = displayError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (authSuccessMessage != null) {
            Surface(
                color = StatusSuccessContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = LebaneseCedarGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(text = authSuccessMessage ?: "", style = MaterialTheme.typography.bodySmall, color = StatusOnSuccessContainer, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        when (step) {

            // ------------------------------------------------------------------
            // STEP: Phone entry — determines login vs signup path
            // ------------------------------------------------------------------
            AuthStep.PHONE_ENTRY -> ModernCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(20.dp), elevation = 3.dp) {
                AuthStepHeader(icon = Icons.Default.Phone, title = "Enter Your Number", subtitle = null, isBusy = isAuthenticating)
                Spacer(modifier = Modifier.height(Spacing.lg))
                PhoneNumberField(
                    country = phoneCountry,
                    onCountryChange = { phoneCountry = it },
                    number = phoneNumber,
                    onNumberChange = { phoneNumber = it; localErrorMessage = null },
                    modifier = Modifier.fillMaxWidth().testTag("auth_phone_input")
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                ProPrimaryButton(
                    text = if (isAuthenticating) "Checking..." else "Continue",
                    onClick = {
                        if (phoneNumber.isBlank() || phoneNumber.filter { it.isDigit() }.length < 6) {
                            localErrorMessage = "Please enter a valid phone number"
                            return@ProPrimaryButton
                        }
                        clearErrors()
                        authViewModel.checkPhoneRegistered(
                            e164Phone = verifiedPhoneE164,
                            onHasPinSet = {
                                pinCode = ""
                                step = AuthStep.PIN_ENTRY
                            },
                            onNeedsPinSetup = {
                                // Existing account, no PIN yet — send OTP then go to SET_PIN
                                startOtpForSignup()
                            },
                            onNewUser = {
                                // New account — OTP → registration form → SET_PIN
                                startOtpForSignup()
                            }
                        )
                    },
                    enabled = !isAuthenticating,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    modifier = Modifier.fillMaxWidth().testTag("submit_login_button")
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // ── OR divider ──────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("or", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // ── Google Sign-In button ────────────────────────────
                OutlinedButton(
                    onClick = {
                        clearErrors()
                        val intent = GoogleSignInHelper.getSignInIntent(context)
                        googleSignInLauncher.launch(intent)
                    },
                    enabled = !isAuthenticating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1F1F1F)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(
                        painter = painterResource(id = com.example.R.drawable.ic_google),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        "Continue with Google",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ------------------------------------------------------------------
            // STEP: PIN entry — returning users
            // ------------------------------------------------------------------
            AuthStep.PIN_ENTRY -> ModernCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(20.dp), elevation = 3.dp) {
                AuthStepHeader(
                    icon = Icons.Default.Lock,
                    title = "Enter Your PIN",
                    subtitle = "Enter your 6-digit PIN to sign in as ${phoneCountry.dialCode} $phoneNumber",
                    isBusy = isAuthenticating
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { pinCode = it.filter { c -> c.isDigit() }.take(6); localErrorMessage = null },
                    label = { Text("6-Digit PIN") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (pinVisible) "Hide PIN" else "Show PIN")
                        }
                    },
                    visualTransformation = if (pinVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pin_input")
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                ProPrimaryButton(
                    text = if (isAuthenticating) "Signing in..." else "Sign In",
                    onClick = {
                        val currentActivity = activity
                        if (currentActivity == null) { localErrorMessage = "Unable to sign in right now."; return@ProPrimaryButton }
                        if (pinCode.length < 6) { localErrorMessage = "Please enter your 6-digit PIN"; return@ProPrimaryButton }
                        clearErrors()
                        authViewModel.verifyPinAndLogin(
                            activity = currentActivity,
                            e164Phone = verifiedPhoneE164,
                            pin = pinCode,
                            onVerified = onLoginSuccess
                        )
                    },
                    enabled = !isAuthenticating,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.fillMaxWidth().testTag("submit_pin_button")
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                TextButton(
                    onClick = { clearErrors(); startOtpForPinReset() },
                    enabled = !isAuthenticating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Forgot PIN? Verify via SMS to reset", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = { step = AuthStep.PHONE_ENTRY; pinCode = ""; clearErrors() },
                    enabled = !isAuthenticating
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Change phone number", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            // ------------------------------------------------------------------
            // STEP: OTP entry (signup or forgot-PIN)
            // ------------------------------------------------------------------
            AuthStep.OTP_ENTRY -> ModernCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(20.dp), elevation = 3.dp) {
                AuthStepHeader(
                    icon = Icons.Default.Sms,
                    title = if (isForgotPinReset) "Verify to Reset PIN" else "Enter Verification Code",
                    subtitle = "SMS code sent to ${phoneCountry.dialCode} $phoneNumber",
                    isBusy = isAuthenticating
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                InputField(
                    value = otpCode,
                    onValueChange = { otpCode = it.filter { c -> c.isDigit() }.take(6) },
                    label = "6-Digit SMS Code",
                    leadingIcon = Icons.Default.Sms,
                    modifier = Modifier.fillMaxWidth().testTag("otp_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(modifier = Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = LebaneseCedarGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Do not share this code with anyone.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
                ProPrimaryButton(
                    text = if (isAuthenticating) "Verifying..." else "Verify Code",
                    onClick = {
                        val currentActivity = activity ?: run { localErrorMessage = "Unable to verify right now."; return@ProPrimaryButton }
                        if (otpCode.length < 6) { localErrorMessage = "Please enter the 6-digit code"; return@ProPrimaryButton }
                        authViewModel.submitPhoneVerificationCode(currentActivity, otpCode) { needsRegistration ->
                            if (isForgotPinReset) {
                                // PIN reset — skip registration form
                                newPin = ""; confirmPin = ""
                                step = AuthStep.SET_PIN
                            } else if (needsRegistration) {
                                step = AuthStep.REGISTRATION_FORM
                            } else {
                                // Existing account with no PIN (migration) — go set PIN
                                newPin = ""; confirmPin = ""
                                step = AuthStep.SET_PIN
                            }
                        }
                    },
                    enabled = !isAuthenticating,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.fillMaxWidth().testTag("submit_otp_button")
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                if (resendCountdownSeconds > 0) {
                    Text(
                        "Resend code in ${resendCountdownSeconds}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    TextButton(
                        onClick = {
                            val currentActivity = activity ?: return@TextButton
                            clearErrors()
                            val purpose = if (isForgotPinReset) AuthViewModel.OtpPurpose.PIN_RESET else AuthViewModel.OtpPurpose.SIGNUP
                            authViewModel.startPhoneVerification(
                                activity = currentActivity,
                                e164Phone = verifiedPhoneE164,
                                purpose = purpose,
                                onCodeSent = { otpCode = ""; resendCountdownSeconds = OTP_RESEND_COOLDOWN_SECONDS },
                                onVerified = { needsRegistration ->
                                    if (isForgotPinReset || !needsRegistration) step = AuthStep.SET_PIN
                                    else step = AuthStep.REGISTRATION_FORM
                                }
                            )
                        },
                        enabled = !isAuthenticating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Resend Code", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = {
                    isForgotPinReset = false
                    step = if (authViewModel.pendingOtpPurpose == AuthViewModel.OtpPurpose.PIN_RESET) AuthStep.PIN_ENTRY else AuthStep.PHONE_ENTRY
                    otpCode = ""
                    clearErrors()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Back", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            // ------------------------------------------------------------------
            // STEP: Registration form (new accounts only)
            // ------------------------------------------------------------------
            AuthStep.REGISTRATION_FORM -> ModernCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(20.dp), elevation = 3.dp) {
                AuthStepHeader(icon = Icons.Default.AppRegistration, title = "Complete Your Profile", subtitle = "Phone verified — just a few more details to join ProHost", isBusy = isAuthenticating)
                Spacer(modifier = Modifier.height(14.dp))
                Surface(color = StatusSuccessContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = LebaneseCedarGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Phone verified: ${phoneCountry.dialCode} $phoneNumber", style = MaterialTheme.typography.bodySmall, color = StatusOnSuccessContainer, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfilePicturePickerField(pictureUri = regProfilePicUri, onPictureSelected = { regProfilePicUri = it })
                    Column {
                        Text("Profile Picture", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Optional", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                InputField(value = regFullName, onValueChange = { regFullName = it; localErrorMessage = null }, label = "Full Name", placeholder = "e.g. Maya Haddad", leadingIcon = Icons.Default.Person, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(10.dp))
                InputField(value = regEmail, onValueChange = { regEmail = it; localErrorMessage = null }, label = "Email", placeholder = "specialist@organization.lb", leadingIcon = Icons.Default.Email, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
                Spacer(modifier = Modifier.height(10.dp))
                InputField(value = regSpecialty, onValueChange = { regSpecialty = it; localErrorMessage = null }, label = "Profession / Job Title (Optional)", placeholder = "e.g. Dermatologist, Architect, Coworking Manager", leadingIcon = Icons.Default.Badge, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(10.dp))
                DocumentPickerField(label = "ID Document (National ID / Passport)", helperText = "Kept on file to verify your identity — PDF, JPG, or PNG", state = regIdDocState, onStateChanged = { regIdDocState = it }, modifier = Modifier.fillMaxWidth(), required = true)
                Spacer(modifier = Modifier.height(10.dp))
                CountryDropdownField(selectedCountry = regCountry, onCountrySelected = { regCountry = it }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                InputField(value = regGovernorateArea, onValueChange = { regGovernorateArea = it; localErrorMessage = null }, label = "Governorate / Area", placeholder = "e.g. Mount Lebanon", leadingIcon = Icons.Default.LocationOn, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(10.dp))
                InputField(value = regCity, onValueChange = { regCity = it; localErrorMessage = null }, label = "City", placeholder = "e.g. Beirut", leadingIcon = Icons.Default.LocationCity, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth().clickable { tosAccepted = !tosAccepted }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tosAccepted, onCheckedChange = { tosAccepted = it })
                    Column {
                        Row {
                            Text("I agree to the ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Terms of Use", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { showLegalDocument = com.example.legal.LegalContent.termsOfUse })
                        }
                        Row {
                            Text("and ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Privacy Policy", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { showLegalDocument = com.example.legal.LegalContent.privacyPolicy })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                ProPrimaryButton(
                    text = if (isAuthenticating) "Creating Account..." else "Create Account & Set PIN",
                    onClick = {
                        val currentActivity = activity ?: run { localErrorMessage = "Unable to create your account right now."; return@ProPrimaryButton }
                        if (regFullName.isBlank()) { localErrorMessage = "Please enter your full name"; return@ProPrimaryButton }
                        if (regEmail.isBlank() || !regEmail.contains("@")) { localErrorMessage = "Please enter a valid email"; return@ProPrimaryButton }
                        if (!regIdDocState.isSelected) { localErrorMessage = "Please upload your ID document"; return@ProPrimaryButton }
                        if (regGovernorateArea.isBlank()) { localErrorMessage = "Please enter your governorate / area"; return@ProPrimaryButton }
                        if (regCity.isBlank()) { localErrorMessage = "Please enter your city"; return@ProPrimaryButton }
                        if (!tosAccepted) { localErrorMessage = "Please agree to the Terms of Use and Privacy Policy to continue"; return@ProPrimaryButton }
                        val registration = AuthViewModel.PendingPhoneRegistration(
                            fullName = regFullName, email = regEmail,
                            phoneE164 = if (isGoogleRegistrationFlow) "" else verifiedPhoneE164,
                            specialty = regSpecialty, country = regCountry.name, governorate = regGovernorateArea,
                            city = regCity, profilePictureUri = regProfilePicUri, idDocumentUri = regIdDocState.uri,
                            tosAccepted = tosAccepted
                        )
                        if (isGoogleRegistrationFlow) {
                            authViewModel.completeGoogleRegistration(
                                activity = currentActivity,
                                registration = registration,
                                onSuccess = { newPin = ""; confirmPin = ""; step = AuthStep.SET_PIN }
                            )
                        } else {
                            authViewModel.completePendingRegistration(
                                activity = currentActivity,
                                registration = registration,
                                onSuccess = { newPin = ""; confirmPin = ""; step = AuthStep.SET_PIN }
                            )
                        }
                    },
                    enabled = !isAuthenticating,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.fillMaxWidth().testTag("submit_registration_button")
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                TextButton(
                    onClick = {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                        phoneNumber = ""; otpCode = ""
                        isForgotPinReset = false
                        isGoogleRegistrationFlow = false
                        clearErrors()
                        step = AuthStep.PHONE_ENTRY
                        onCancelResume?.invoke()
                    },
                    enabled = !isAuthenticating
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        if (isGoogleRegistrationFlow) "Cancel Google sign-up" else "Start over with a different number",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ------------------------------------------------------------------
            // STEP: Set PIN (after signup or after forgot-PIN OTP)
            // ------------------------------------------------------------------
            AuthStep.SET_PIN -> ModernCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(20.dp), elevation = 3.dp) {
                AuthStepHeader(
                    icon = Icons.Default.LockReset,
                    title = if (isForgotPinReset) "Set New PIN" else "Create Your PIN",
                    subtitle = "Choose a 6-digit PIN you'll use to log in on this and future sessions",
                    isBusy = isAuthenticating
                )
                Spacer(modifier = Modifier.height(Spacing.lg))

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(6); localErrorMessage = null },
                    label = { Text("6-Digit PIN") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { newPinVisible = !newPinVisible }) {
                            Icon(if (newPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        }
                    },
                    visualTransformation = if (newPinVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter { c -> c.isDigit() }.take(6); localErrorMessage = null },
                    label = { Text("Confirm PIN") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = confirmPin.length == 6 && confirmPin != newPin,
                    modifier = Modifier.fillMaxWidth()
                )

                if (confirmPin.length == 6 && confirmPin != newPin) {
                    Text("PINs do not match", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Do not use a sequence like 123456 or a date. Pick something only you know.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                ProPrimaryButton(
                    text = if (isAuthenticating) "Saving PIN..." else if (isForgotPinReset) "Set New PIN" else "Save PIN & Continue",
                    onClick = {
                        if (newPin.length < 6) { localErrorMessage = "Please enter a 6-digit PIN"; return@ProPrimaryButton }
                        if (confirmPin != newPin) { localErrorMessage = "PINs do not match — please re-enter"; return@ProPrimaryButton }
                        clearErrors()
                        authViewModel.setPin(newPin) {
                            isForgotPinReset = false
                            onLoginSuccess()
                        }
                    },
                    enabled = !isAuthenticating && newPin.length == 6 && confirmPin.length == 6,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.fillMaxWidth().testTag("submit_set_pin_button")
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = LebaneseCedarGreen, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (step == AuthStep.PIN_ENTRY)
                        "Your 6-digit PIN protects your account. Never share it with anyone."
                    else
                        "Your phone number is your account — verified via one-time SMS.",
                    style = MaterialTheme.typography.labelSmall,
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Surface(color = OxfordBlue.copy(alpha = 0.1f), shape = CircleShape, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = OxfordBlue, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun AuthStepIndicator(step: AuthStep, isForgotPinReset: Boolean = false) {
    // Show only the steps relevant to the current auth mode inferred from position.
    // Login: Phone → PIN → Done
    // Signup: Phone → Verify → Profile → PIN
    // Forgot PIN: Phone → Verify → Set PIN
    val (steps, currentIndex) = when (step) {
        AuthStep.PHONE_ENTRY -> listOf(
            Triple(AuthStep.PHONE_ENTRY, "Phone", Icons.Default.Phone),
            Triple(AuthStep.PIN_ENTRY, "PIN", Icons.Default.Lock),
            Triple(AuthStep.SET_PIN, "Done", Icons.Default.CheckCircle)
        ) to 0
        AuthStep.PIN_ENTRY -> listOf(
            Triple(AuthStep.PHONE_ENTRY, "Phone", Icons.Default.Phone),
            Triple(AuthStep.PIN_ENTRY, "PIN", Icons.Default.Lock),
            Triple(AuthStep.SET_PIN, "Done", Icons.Default.CheckCircle)
        ) to 1
        AuthStep.OTP_ENTRY -> listOf(
            Triple(AuthStep.PHONE_ENTRY, "Phone", Icons.Default.Phone),
            Triple(AuthStep.OTP_ENTRY, "Verify", Icons.Default.Sms),
            Triple(AuthStep.SET_PIN, "PIN", Icons.Default.Lock)
        ) to 1
        AuthStep.REGISTRATION_FORM -> listOf(
            Triple(AuthStep.OTP_ENTRY, "Verify", Icons.Default.Sms),
            Triple(AuthStep.REGISTRATION_FORM, "Profile", Icons.Default.Person),
            Triple(AuthStep.SET_PIN, "PIN", Icons.Default.Lock)
        ) to 1
        AuthStep.SET_PIN -> if (isForgotPinReset) listOf(
            Triple(AuthStep.PHONE_ENTRY, "Phone", Icons.Default.Phone),
            Triple(AuthStep.OTP_ENTRY, "Verify", Icons.Default.Sms),
            Triple(AuthStep.SET_PIN, "Set PIN", Icons.Default.Lock)
        ) to 2 else listOf(
            Triple(AuthStep.OTP_ENTRY, "Verify", Icons.Default.Sms),
            Triple(AuthStep.REGISTRATION_FORM, "Profile", Icons.Default.Person),
            Triple(AuthStep.SET_PIN, "Set PIN", Icons.Default.Lock)
        ) to 2
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { index, (_, label, icon) ->
            val isDone = index < currentIndex
            val isCurrent = index == currentIndex
            val dotColor = when {
                isDone -> LebaneseCedarGreen
                isCurrent -> CarnationOrange
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = dotColor, shape = CircleShape, modifier = Modifier.size(26.dp)) {
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
                    modifier = Modifier.width(28.dp).padding(horizontal = Spacing.xs).padding(bottom = 14.dp),
                    color = if (index < currentIndex) LebaneseCedarGreen else MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}
