package com.example.ui.screens

import android.app.Activity
import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.model.findCountryByName
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AuthViewModel
import com.example.util.PhoneCountryDetector
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/**
 * Every ProHost account — new or returning — now goes through email/Google first:
 *  1. EMAIL_ENTRY — enter an email or tap "Continue with Google". The email is looked
 *     up to determine whether it's new, has a magic-link flow, or has Google.
 *  2. EMAIL_OTP — 6-digit code sent; user enters it in-app.
 *  3. REGISTRATION_FORM — shown only when the verified identity turns out to be brand
 *     new (Firebase's own isNewUser decides, never a guess made before verification).
 *
 * Phone OTP is kept as a PARALLEL path for existing phone-only users — reachable via
 * "Use phone number instead" from EMAIL_ENTRY. No forced migration.
 *  PHONE_ENTRY → OTP_ENTRY → (brand-new number only) REGISTRATION_FORM
 */
private enum class AuthStep { EMAIL_ENTRY, EMAIL_OTP, EMAIL_LINK_SENT, PHONE_ENTRY, OTP_ENTRY, REGISTRATION_FORM }

/** Matches Firebase Phone Auth's own typical SMS-resend throttling window. */
private const val OTP_RESEND_COOLDOWN_SECONDS = 30

/** Email resend cooldown: slightly longer than OTP to match email-provider rate limits. */
private const val EMAIL_RESEND_COOLDOWN_SECONDS = 60

// Savers for rememberSaveable — process death would otherwise lose all in-progress state.
private val AuthStepSaver = Saver<AuthStep, String>(
    save = { it.name },
    restore = { name -> AuthStep.entries.firstOrNull { it.name == name } ?: AuthStep.EMAIL_ENTRY }
)

private val CountrySaver = Saver<com.example.data.model.Country, String>(
    save = { it.name },
    restore = { findCountryByName(it) }
)

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
    resumeAtRegistration: Boolean = false,
    resumePhoneE164: String? = null,
    onCancelResume: (() -> Unit)? = null,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()
    var step by rememberSaveable(stateSaver = AuthStepSaver) {
        mutableStateOf(if (resumeAtRegistration) AuthStep.REGISTRATION_FORM else AuthStep.EMAIL_ENTRY)
    }

    // --- Email entry state ---
    var emailInput by rememberSaveable { mutableStateOf("") }
    var emailResendCountdownSeconds by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(emailResendCountdownSeconds) {
        if (emailResendCountdownSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            emailResendCountdownSeconds -= 1
        }
    }

    val isPreview = LocalInspectionMode.current
    val pendingEmail = if (!isPreview) authViewModel.pendingEmail.collectAsState().value else ""

    // Credential Manager for Google One Tap
    val credentialManager = remember(context, isPreview) { if (!isPreview) CredentialManager.create(context) else null }

    // Pre-fill values extracted from Google credential — applied to the registration form
    // via LaunchedEffect(step) below, after regFullName/regProfilePicUri are initialized.
    var prefillGoogleName by rememberSaveable { mutableStateOf("") }
    var prefillGooglePictureUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // Launches Google sign-in. Tries One Tap (GetGoogleIdOption) first; if that
    // produces NoCredentialException (can happen on fresh devices or after the user
    // previously dismissed One Tap too many times), falls back to the standard
    // Sign In With Google bottom-sheet (GetSignInWithGoogleOption), which is always
    // available when Play Services is present.
    fun launchGoogleSignIn() {
        coroutineScope.launch {
            val cm = credentialManager ?: return@launch
            val webClientId = context.getString(R.string.default_web_client_id)

            fun handleCredentialResult(result: androidx.credentials.GetCredentialResponse) {
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val googleName = googleIdTokenCredential.displayName
                        ?: "${googleIdTokenCredential.givenName.orEmpty()} ${googleIdTokenCredential.familyName.orEmpty()}".trim()
                    val googlePictureUri = googleIdTokenCredential.profilePictureUri
                    val currentActivity = activity ?: return
                    authViewModel.startGoogleSignIn(
                        activity = currentActivity,
                        googleIdToken = googleIdTokenCredential.idToken
                    ) { needsRegistration ->
                        if (needsRegistration) {
                            prefillGoogleName = googleName
                            prefillGooglePictureUri = googlePictureUri
                            step = AuthStep.REGISTRATION_FORM
                        } else {
                            onLoginSuccess()
                        }
                    }
                }
            }

            try {
                val oneTapOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .build()
                val result = cm.getCredential(
                    context,
                    GetCredentialRequest.Builder().addCredentialOption(oneTapOption).build()
                )
                handleCredentialResult(result)
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                // User dismissed the account picker — no action needed.
            } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                // One Tap could not find a matching credential (no accounts, or the flow
                // was suppressed). Fall back to the standard Sign In With Google sheet,
                // which works whenever Google Play Services is present.
                try {
                    val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                    val result = cm.getCredential(
                        context,
                        GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
                    )
                    handleCredentialResult(result)
                } catch (e2: androidx.credentials.exceptions.GetCredentialCancellationException) {
                    // Dismissed
                } catch (e2: GetCredentialException) {
                    android.util.Log.w("LoginAuthScreen", "Google sign-in unavailable: ${e2.type}")
                    android.widget.Toast.makeText(
                        context,
                        "Google sign-in isn't available on this device. Please sign in with email.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: GetCredentialException) {
                android.util.Log.w("LoginAuthScreen", "Google sign-in error: ${e.type}")
                android.widget.Toast.makeText(
                    context,
                    "Google sign-in isn't available right now. Please sign in with email.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // --- Phone entry state (legacy parallel path) ---
    var phoneCountry by rememberSaveable(stateSaver = CountrySaver) { mutableStateOf(findCountryByName("Lebanon")) }
    var phoneNumber by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (isPreview) return@LaunchedEffect
        authViewModel.clearAuthMessages()
        // MainActivity already requests all permissions; detect country using available signals
        // (SIM/locale — no duplicate permission request here).
        val detected = PhoneCountryDetector.detectCountry(context)
        phoneCountry = detected
        regWhatsAppCountry = detected
    }

    // --- OTP entry state (phone SMS) ---
    var otpCode by rememberSaveable { mutableStateOf("") }
    var resendCountdownSeconds by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(resendCountdownSeconds) {
        if (resendCountdownSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            resendCountdownSeconds -= 1
        }
    }

    // --- Email OTP entry state ---
    var emailOtpCode by rememberSaveable { mutableStateOf("") }

    // --- Registration form state ---
    var regProfilePicUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var regFullName by rememberSaveable { mutableStateOf("") }
    var regSpecialty by rememberSaveable { mutableStateOf("") }
    var regPhoneNumber by rememberSaveable { mutableStateOf("") }
    var regWhatsAppCountry by rememberSaveable(stateSaver = CountrySaver) { mutableStateOf(findCountryByName("Lebanon")) }
    var regCountry by rememberSaveable(stateSaver = CountrySaver) { mutableStateOf(findCountryByName("Lebanon")) }
    var regGovernorateArea by rememberSaveable { mutableStateOf("") }
    var regCity by rememberSaveable { mutableStateOf("") }
    var tosAccepted by rememberSaveable { mutableStateOf(false) }

    val isAuthenticating by authViewModel.isAuthenticating.collectAsState()
    val authErrorMessage by authViewModel.authErrorMessage.collectAsState()
    val authSuccessMessage by authViewModel.authSuccessMessage.collectAsState()

    var localErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showLegalDocument by remember { mutableStateOf<com.example.legal.LegalDocument?>(null) }

    val verifiedPhoneE164 = resumePhoneE164 ?: com.example.data.model.formatToE164(phoneCountry, phoneNumber)

    // Apply Google One Tap pre-fill when the registration form opens after Google sign-in.
    LaunchedEffect(step) {
        if (step == AuthStep.REGISTRATION_FORM) {
            if (prefillGoogleName.isNotBlank() && regFullName.isBlank()) regFullName = prefillGoogleName
            if (prefillGooglePictureUri != null && regProfilePicUri == null) regProfilePicUri = prefillGooglePictureUri
        }
    }

    fun goToRegistrationForm() {
        localErrorMessage = null
        authViewModel.clearAuthMessages()
        step = AuthStep.REGISTRATION_FORM
    }

    // Email-link / one-click-code sign-ins finish in ProHostAppRoot, outside this
    // screen's callbacks — follow the ViewModel's signal so a new user lands on the form.
    val registrationRequested = if (!isPreview) authViewModel.registrationRequested.collectAsState().value else false
    LaunchedEffect(registrationRequested) {
        if (registrationRequested) {
            if (step != AuthStep.REGISTRATION_FORM) goToRegistrationForm()
            authViewModel.consumeRegistrationRequest()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumBackgroundBrush())
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

        // Step indicator only for the phone-OTP flow
        if (step == AuthStep.PHONE_ENTRY || step == AuthStep.OTP_ENTRY || step == AuthStep.REGISTRATION_FORM) {
            AuthStepIndicator(step = step)
            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

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
            AuthStep.EMAIL_ENTRY -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                AuthStepHeader(
                    icon = Icons.Default.Email,
                    title = "Welcome to ProHost",
                    subtitle = "Sign in or create your account",
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                InputField(
                    value = emailInput,
                    onValueChange = {
                        emailInput = it
                        localErrorMessage = null
                    },
                    label = "Email address",
                    placeholder = "you@example.com",
                    leadingIcon = Icons.Default.Email,
                    modifier = Modifier.fillMaxWidth().testTag("auth_email_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                ProPrimaryButton(
                    text = if (isAuthenticating) "Sending..." else "Continue",
                    onClick = {
                        val trimmedEmail = emailInput.trim().lowercase()
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                            localErrorMessage = "Please enter a valid email address"
                            return@ProPrimaryButton
                        }
                        localErrorMessage = null
                        authViewModel.startEmailSignIn(trimmedEmail) { delivery ->
                            emailResendCountdownSeconds = EMAIL_RESEND_COOLDOWN_SECONDS
                            step = when (delivery) {
                                AuthViewModel.EmailDelivery.LINK -> AuthStep.EMAIL_LINK_SENT
                                AuthViewModel.EmailDelivery.CODE -> {
                                    emailOtpCode = ""
                                    AuthStep.EMAIL_OTP
                                }
                            }
                        }
                    },
                    enabled = !isAuthenticating,
                    icon = Icons.AutoMirrored.Filled.Login,
                    modifier = Modifier.fillMaxWidth().testTag("submit_email_button")
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // OR divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "  OR  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                OutlinedButton(
                    onClick = { launchGoogleSignIn() },
                    enabled = !isAuthenticating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_google),
                        contentDescription = "Google",
                        modifier = Modifier.size(18.dp),
                        tint = androidx.compose.ui.graphics.Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Continue with Google",
                        fontWeight = FontWeight.SemiBold
                    )
                }

            }

            AuthStep.EMAIL_OTP -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                AuthStepHeader(
                    icon = Icons.Default.MarkEmailRead,
                    title = "Check your email",
                    subtitle = "We sent a 6-digit code to $pendingEmail. Enter it below.",
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                InputField(
                    value = emailOtpCode,
                    onValueChange = { emailOtpCode = it.filter { c -> c.isDigit() }.take(6) },
                    label = "6-Digit Code",
                    leadingIcon = Icons.Default.Sms,
                    modifier = Modifier.fillMaxWidth().testTag("email_otp_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = LebaneseCedarGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Do not share this code with anyone.",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                ProPrimaryButton(
                    text = if (isAuthenticating) "Verifying..." else "Verify Code",
                    onClick = {
                        val currentActivity = activity
                        if (currentActivity == null) {
                            localErrorMessage = "Unable to verify right now."
                            return@ProPrimaryButton
                        }
                        if (emailOtpCode.length < 6) {
                            localErrorMessage = "Please enter the 6-digit code"
                            return@ProPrimaryButton
                        }
                        authViewModel.verifyEmailOtpCode(
                            activity = currentActivity,
                            email = pendingEmail,
                            code = emailOtpCode
                        ) { needsRegistration ->
                            if (needsRegistration) goToRegistrationForm() else onLoginSuccess()
                        }
                    },
                    enabled = !isAuthenticating,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.fillMaxWidth().testTag("submit_email_otp_button")
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                if (emailResendCountdownSeconds > 0) {
                    Text(
                        text = "Resend code in ${emailResendCountdownSeconds}s",
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    TextButton(
                        onClick = {
                            localErrorMessage = null
                            authViewModel.sendEmailOtp(email = pendingEmail) { sent ->
                                if (sent) {
                                    emailOtpCode = ""
                                    emailResendCountdownSeconds = EMAIL_RESEND_COOLDOWN_SECONDS
                                }
                            }
                        },
                        enabled = !isAuthenticating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Resend Code", fontSize = MaterialTheme.typography.labelMedium.fontSize, fontWeight = FontWeight.SemiBold)
                    }
                }

                TextButton(
                    onClick = {
                        localErrorMessage = null
                        authViewModel.clearAuthMessages()
                        emailOtpCode = ""
                        step = AuthStep.EMAIL_ENTRY
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        "Use a different email",
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AuthStep.EMAIL_LINK_SENT -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                AuthStepHeader(
                    icon = Icons.Default.MarkEmailRead,
                    title = "Check your email",
                    subtitle = "We sent a sign-in link to $pendingEmail. Tap the link to sign in automatically.",
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "The link expires in 60 minutes. Check your spam folder if it doesn't arrive.",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                if (emailResendCountdownSeconds > 0) {
                    Text(
                        text = "Resend link in ${emailResendCountdownSeconds}s",
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            localErrorMessage = null
                            authViewModel.savePendingEmailLink(pendingEmail)
                            authViewModel.sendEmailLinkViaFunction(email = pendingEmail) { sent ->
                                if (sent) emailResendCountdownSeconds = EMAIL_RESEND_COOLDOWN_SECONDS
                            }
                        },
                        enabled = !isAuthenticating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Resend link", fontSize = MaterialTheme.typography.labelMedium.fontSize, fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        localErrorMessage = null
                        authViewModel.clearAuthMessages()
                        authViewModel.sendEmailOtp(email = pendingEmail) { sent ->
                            if (sent) {
                                emailOtpCode = ""
                                emailResendCountdownSeconds = EMAIL_RESEND_COOLDOWN_SECONDS
                                step = AuthStep.EMAIL_OTP
                            }
                        }
                    },
                    enabled = !isAuthenticating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        "Use a code instead",
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(
                    onClick = {
                        localErrorMessage = null
                        authViewModel.clearAuthMessages()
                        step = AuthStep.EMAIL_ENTRY
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        "Use a different email",
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AuthStep.PHONE_ENTRY -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                AuthStepHeader(
                    icon = Icons.Default.Phone,
                    title = "Login/Signup",
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
                                resendCountdownSeconds = OTP_RESEND_COOLDOWN_SECONDS
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

                Spacer(modifier = Modifier.height(Spacing.sm))

                TextButton(
                    onClick = {
                        localErrorMessage = null
                        authViewModel.clearAuthMessages()
                        step = AuthStep.EMAIL_ENTRY
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        "Back to email sign-in",
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AuthStep.OTP_ENTRY -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp),
                elevation = 3.dp
            ) {
                AuthStepHeader(
                    icon = Icons.Default.Sms,
                    title = "Enter Verification Code",
                    subtitle = "ProHost mobile app code sent to ${phoneCountry.dialCode} $phoneNumber",
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

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = LebaneseCedarGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Do not share this code with anyone.",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

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

                if (resendCountdownSeconds > 0) {
                    Text(
                        text = "Resend code in ${resendCountdownSeconds}s",
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    TextButton(
                        onClick = {
                            val currentActivity = activity
                            if (currentActivity == null) {
                                localErrorMessage = "Unable to resend the code right now."
                                return@TextButton
                            }
                            localErrorMessage = null
                            authViewModel.startPhoneVerification(
                                activity = currentActivity,
                                e164Phone = verifiedPhoneE164,
                                onCodeSent = {
                                    otpCode = ""
                                    resendCountdownSeconds = OTP_RESEND_COOLDOWN_SECONDS
                                },
                                onVerified = { needsRegistration ->
                                    if (needsRegistration) goToRegistrationForm() else onLoginSuccess()
                                }
                            )
                        },
                        enabled = !isAuthenticating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Resend Code", fontSize = MaterialTheme.typography.labelMedium.fontSize, fontWeight = FontWeight.SemiBold)
                    }
                }

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
                    subtitle = "Your identity is verified — just a few more details to join ProHost",
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Show the verified identity (email or phone) as a non-editable badge
                val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val firebaseEmail = firebaseUser?.email ?: ""
                val firebasePhone = firebaseUser?.phoneNumber ?: resumePhoneE164 ?: ""

                if (firebaseEmail.isNotBlank()) {
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
                                text = "Signed in as: $firebaseEmail",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusOnSuccessContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else if (firebasePhone.isNotBlank()) {
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
                                text = "Phone verified: $firebasePhone",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusOnSuccessContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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

                // Email is read-only from Firebase Auth (populated via email/Google sign-in).
                // For legacy phone-auth users, this chip will be empty — they can add
                // their email from profile settings after signing up.
                if (firebaseEmail.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = firebaseEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

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

                Spacer(modifier = Modifier.height(10.dp))

                PhoneNumberField(
                    country = regWhatsAppCountry,
                    onCountryChange = { regWhatsAppCountry = it },
                    number = regPhoneNumber,
                    onNumberChange = { regPhoneNumber = it },
                    label = "WhatsApp Number (Optional)",
                    placeholder = "71 234 567",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

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
                        // Email comes from Firebase Auth for email/Google users.
                        // For phone-auth users, Firebase email is blank — registration proceeds without email
                        // (they can add it from profile settings later).
                        val registrationEmail = firebaseEmail.ifBlank {
                            // Phone-auth new user: validate email would be blank; skip email validation
                            // since assignInitialRole requires email — this path is a legacy edge case.
                            // The server will reject with an error message if email is truly required.
                            ""
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
                        // A phone-OTP sign-in already verified a number; otherwise use the
                        // WhatsApp number typed on this form (blank when left empty — never
                        // a bare dial code).
                        val registrationPhoneE164 = when {
                            !resumePhoneE164.isNullOrBlank() -> resumePhoneE164
                            phoneNumber.isNotBlank() -> verifiedPhoneE164
                            regPhoneNumber.isNotBlank() ->
                                com.example.data.model.formatToE164(regWhatsAppCountry, regPhoneNumber)
                            else -> ""
                        }
                        authViewModel.completePendingRegistration(
                            activity = currentActivity,
                            registration = AuthViewModel.PendingRegistration(
                                fullName = regFullName,
                                email = registrationEmail,
                                phoneE164 = registrationPhoneE164,
                                specialty = regSpecialty,
                                country = regCountry.name,
                                governorate = regGovernorateArea,
                                city = regCity,
                                profilePictureUri = regProfilePicUri,
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

                Spacer(modifier = Modifier.height(Spacing.sm))

                TextButton(
                    onClick = {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                        phoneNumber = ""
                        otpCode = ""
                        emailInput = ""
                        localErrorMessage = null
                        authViewModel.clearAuthMessages()
                        step = AuthStep.EMAIL_ENTRY
                        onCancelResume?.invoke()
                    },
                    enabled = !isAuthenticating
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Start over with a different account", fontSize = MaterialTheme.typography.labelMedium.fontSize, fontWeight = FontWeight.SemiBold)
                }
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
                    text = "Sign in securely with a magic email link or your Google account.",
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
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
    // Only renders for the phone-OTP flow steps
    val steps = listOf(
        Triple(AuthStep.PHONE_ENTRY, "Phone", Icons.Default.Phone),
        Triple(AuthStep.OTP_ENTRY, "Verify", Icons.Default.Sms),
        Triple(AuthStep.REGISTRATION_FORM, "Profile", Icons.Default.Person)
    )
    val currentIndex = steps.indexOfFirst { it.first == step }
    if (currentIndex < 0) return // not in phone flow — don't render

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
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(14.dp)
                            )
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
