package com.example.ui.screens

import com.example.ui.util.localizedLabel
import com.example.ui.util.uiText
import androidx.compose.ui.res.stringResource
import com.example.ui.util.UiMessages
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 * Sign in or create an account with email + password, or continue with Google (1.0.44):
 *  - SIGN_IN — email, password, "Forgot password?", "Create an account", Google.
 *  - CREATE_ACCOUNT — email and a new password checked live against the server policy.
 *  - FORGOT_PASSWORD → RESET_SENT — branded reset email; the answer never says whether the
 *    address has an account (email enumeration protection).
 *  - REGISTRATION_FORM — shown only when the signed-in account has no completed profile.
 *
 * There is no emailed code, magic link or SMS sign-in.
 */
internal enum class AuthStep { SIGN_IN, CREATE_ACCOUNT, FORGOT_PASSWORD, RESET_SENT, REGISTRATION_FORM }

/** "Send again" cooldown for the reset email (the server also limits 5 per hour per address). */
private const val EMAIL_RESEND_COOLDOWN_SECONDS = 60

// Savers for rememberSaveable — process death would otherwise lose all in-progress state.
private val AuthStepSaver = Saver<AuthStep, String>(
    save = { it.name },
    restore = { name -> AuthStep.entries.firstOrNull { it.name == name } ?: AuthStep.SIGN_IN }
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
    authViewModel: AuthViewModel = viewModel(),
    /** Screenshot tests only: open on a given step ("CREATE_ACCOUNT", "FORGOT_PASSWORD", "RESET_SENT") with an email filled in. */
    initialStepName: String? = null,
    initialEmail: String? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()
    var step by rememberSaveable(stateSaver = AuthStepSaver) {
        mutableStateOf(
            when {
                resumeAtRegistration -> AuthStep.REGISTRATION_FORM
                else -> AuthStep.entries.firstOrNull { it.name == initialStepName } ?: AuthStep.SIGN_IN
            }
        )
    }

    // Each sign-in step is its own GA4 screen (auth_sign_in, auth_create_account, auth_forgot_password, auth_reset_sent, registration).
    if (!LocalInspectionMode.current) com.example.ui.components.TrackScreen(com.example.analytics.ScreenNames.forAuthStep(step.name), "LoginAuthScreen")

    // --- Email + password state (the password is never saved across process death) ---
    var emailInput by rememberSaveable { mutableStateOf(initialEmail.orEmpty()) }
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var emailResendCountdownSeconds by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(emailResendCountdownSeconds) {
        if (emailResendCountdownSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            emailResendCountdownSeconds -= 1
        }
    }

    val isPreview = LocalInspectionMode.current

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
                    authViewModel.startGoogleSignIn(googleIdToken = googleIdTokenCredential.idToken) { needsRegistration ->
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
                com.example.analytics.AnalyticsTracker.authError("google", "cancelled")
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
                    com.example.analytics.AnalyticsTracker.authError("google", "cancelled")
                } catch (e2: GetCredentialException) {
                    android.util.Log.w("LoginAuthScreen", "Google sign-in unavailable: ${e2.type}")
                    com.example.analytics.AnalyticsTracker.authError("google", "unavailable")
                    UiMessages.show(context,
                        context.getString(R.string.google_sign_in_isn_t_available_on_this_dev),
                        long = true)
                }
            } catch (e: GetCredentialException) {
                android.util.Log.w("LoginAuthScreen", "Google sign-in error: ${e.type}")
                com.example.analytics.AnalyticsTracker.authError("google", "credential_error")
                UiMessages.show(context,
                    context.getString(R.string.google_sign_in_isn_t_available_right_now_p),
                    long = true)
            }
        }
    }

    var regWhatsAppCountry by rememberSaveable(stateSaver = CountrySaver) { mutableStateOf(findCountryByName("Lebanon")) }

    LaunchedEffect(Unit) {
        if (isPreview) return@LaunchedEffect
        authViewModel.clearStaleAuthMessages()
        // MainActivity already requests all permissions; detect country using available signals
        // (SIM/locale — no duplicate permission request here).
        regWhatsAppCountry = PhoneCountryDetector.detectCountry(context)
    }

    // --- Registration form state ---
    var regProfilePicUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var regFullName by rememberSaveable { mutableStateOf("") }
    var regSpecialty by rememberSaveable { mutableStateOf("") }
    var regPhoneNumber by rememberSaveable { mutableStateOf("") }
    var regCountry by rememberSaveable(stateSaver = CountrySaver) { mutableStateOf(findCountryByName("Lebanon")) }
    var regGovernorateArea by rememberSaveable { mutableStateOf("") }
    var regCity by rememberSaveable { mutableStateOf("") }
    var tosAccepted by rememberSaveable { mutableStateOf(false) }

    val isAuthenticating by authViewModel.isAuthenticating.collectAsState()
    val authErrorMessage by authViewModel.authErrorMessage.collectAsState()
    val authSuccessMessage by authViewModel.authSuccessMessage.collectAsState()

    var localErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showLegalDocument by remember { mutableStateOf<com.example.legal.LegalDocument?>(null) }

    // The WhatsApp number from Android's own number picker (Phone Number Hint): the person picks
    // their SIM number in a system sheet or dismisses it and types. Offered once when the form opens.
    val pickDeviceNumber = rememberPhoneNumberHint(onNumber = { e164 ->
        com.example.analytics.AnalyticsTracker.phoneHint("used", "registration")
        val (country, local) = com.example.data.model.splitE164(e164, regWhatsAppCountry)
        regWhatsAppCountry = country
        regPhoneNumber = local
        localErrorMessage = null
    })
    var phoneHintOffered by rememberSaveable { mutableStateOf(false) }

    // Apply Google One Tap pre-fill when the registration form opens after Google sign-in.
    LaunchedEffect(step) {
        if (step == AuthStep.REGISTRATION_FORM) {
            if (prefillGoogleName.isNotBlank() && regFullName.isBlank()) regFullName = prefillGoogleName
            if (prefillGooglePictureUri != null && regProfilePicUri == null) regProfilePicUri = prefillGooglePictureUri
            if (!isPreview && !phoneHintOffered && regPhoneNumber.isBlank() && resumePhoneE164.isNullOrBlank()) {
                phoneHintOffered = true
                com.example.analytics.AnalyticsTracker.phoneHint("offered", "registration")
                pickDeviceNumber()
            }
        }
    }

    fun goToRegistrationForm() {
        localErrorMessage = null
        authViewModel.clearAuthMessages()
        step = AuthStep.REGISTRATION_FORM
    }

    // Offers the password manager to save what the person just signed in / signed up with
    // (Credential Manager; they can dismiss it). Never blocks the sign-in.
    // Offers to save the password (Credential Manager), then continues. The offer is awaited first:
    // leaving the screen cancels this composable's scope, which would close the prompt.
    fun afterPasswordAuth(email: String, password: String): (Boolean) -> Unit = { needsRegistration ->
        passwordInput = ""
        coroutineScope.launch {
            val cm = credentialManager
            val act = activity
            if (cm != null && act != null) {
                try {
                    cm.createCredential(act, androidx.credentials.CreatePasswordRequest(email, password))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Dismissed, or no password manager on this device: nothing to save.
                }
            }
            if (needsRegistration) goToRegistrationForm() else onLoginSuccess()
        }
    }

    // Returning people: offer a saved password or an already-used Google account once when the
    // sign-in step opens (Credential Manager). Nothing saved → nothing shows.
    var savedCredentialOffered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(step) {
        if (isPreview || savedCredentialOffered || step != AuthStep.SIGN_IN || emailInput.isNotBlank()) return@LaunchedEffect
        savedCredentialOffered = true
        val cm = credentialManager ?: return@LaunchedEffect
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(androidx.credentials.GetPasswordOption())
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(context.getString(R.string.default_web_client_id))
                    .build()
            )
            .build()
        val response = try {
            cm.getCredential(context, request)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null // nothing saved, dismissed, or no credential provider on this device
        } ?: return@LaunchedEffect
        when (val credential = response.credential) {
            is androidx.credentials.PasswordCredential -> {
                emailInput = credential.id
                authViewModel.signIn(credential.id, credential.password) { needsRegistration ->
                    if (needsRegistration) goToRegistrationForm() else onLoginSuccess()
                }
            }
            is CustomCredential -> if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                authViewModel.startGoogleSignIn(google.idToken) { needsRegistration ->
                    if (needsRegistration) {
                        prefillGoogleName = google.displayName.orEmpty()
                        prefillGooglePictureUri = google.profilePictureUri
                        step = AuthStep.REGISTRATION_FORM
                    } else onLoginSuccess()
                }
            }
            else -> Unit
        }
    }

    fun goTo(next: AuthStep) {
        localErrorMessage = null
        authViewModel.clearAuthMessages()
        passwordInput = ""
        showPassword = false
        step = next
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .proHostScreenBackground()
            // Keep the fields clear of the status bar and the keyboard.
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xl)
            .testTag("login_auth_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Image(
            painter = painterResource(id = com.example.R.drawable.prohost_login_lockup),
            contentDescription = stringResource(R.string.prohost_login),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 220.dp, height = 144.dp)
        )

        Spacer(modifier = Modifier.height(Spacing.xxl))

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Status or Error Banners
        val displayError = localErrorMessage ?: authErrorMessage
        if (displayError != null) {
            ProHostAlertBanner(message = displayError, severity = ProHostAlertSeverity.ERROR, modifier = Modifier.padding(bottom = 12.dp))
        }

        if (authSuccessMessage != null) {
            ProHostAlertBanner(message = authSuccessMessage ?: "", severity = ProHostAlertSeverity.SUCCESS, modifier = Modifier.padding(bottom = 12.dp))
        }

        when (step) {
            AuthStep.SIGN_IN, AuthStep.CREATE_ACCOUNT -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp),
                elevation = Elevation.level2
            ) {
                val creating = step == AuthStep.CREATE_ACCOUNT
                AuthStepHeader(
                    icon = if (creating) Icons.Default.PersonAdd else Icons.Default.Lock,
                    title = stringResource(if (creating) R.string.auth_create_title else R.string.welcome_to_prohost),
                    subtitle = stringResource(if (creating) R.string.auth_create_subtitle else R.string.auth_sign_in_subtitle),
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                InputField(
                    value = emailInput,
                    onValueChange = {
                        emailInput = it.trim()
                        localErrorMessage = null
                    },
                    label = stringResource(R.string.email_address),
                    placeholder = stringResource(R.string.you_example_com),
                    leadingIcon = Icons.Default.Email,
                    modifier = Modifier
                        .fillMaxWidth()
                        .emailAutofill { emailInput = it.trim() }
                        .testTag("auth_email_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                fun submit() {
                    localErrorMessage = null
                    val email = emailInput.trim().lowercase()
                    val password = passwordInput
                    if (creating) authViewModel.createAccount(email, password, afterPasswordAuth(email, password))
                    else authViewModel.signIn(email, password, afterPasswordAuth(email, password))
                }

                InputField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        localErrorMessage = null
                    },
                    label = stringResource(if (creating) R.string.new_password else R.string.password),
                    leadingIcon = Icons.Default.Key,
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(if (showPassword) R.string.hide_password else R.string.show_password)
                            )
                        }
                    },
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None
                        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .passwordAutofill(newPassword = creating) { passwordInput = it }
                        .testTag("auth_password_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (!isAuthenticating) submit() }),
                    singleLine = true
                )

                if (creating) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    PasswordChecklist(passwordInput)
                } else {
                    TextButton(
                        onClick = { goTo(AuthStep.FORGOT_PASSWORD) },
                        modifier = Modifier.align(Alignment.End).testTag("forgot_password_button")
                    ) {
                        Text(stringResource(R.string.forgot_password), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                ProPrimaryButton(
                    text = when {
                        isAuthenticating && creating -> stringResource(R.string.creating_account)
                        isAuthenticating -> stringResource(R.string.signing_in)
                        creating -> stringResource(R.string.action_create_account)
                        else -> stringResource(R.string.sign_in)
                    },
                    onClick = { submit() },
                    enabled = !isAuthenticating,
                    icon = if (creating) Icons.Default.PersonAdd else Icons.AutoMirrored.Filled.Login,
                    modifier = Modifier.fillMaxWidth().testTag("submit_email_button")
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(if (creating) R.string.have_an_account else R.string.new_to_prohost),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { goTo(if (creating) AuthStep.SIGN_IN else AuthStep.CREATE_ACCOUNT) },
                        modifier = Modifier.testTag("switch_auth_mode_button")
                    ) {
                        Text(
                            stringResource(if (creating) R.string.sign_in else R.string.create_an_account),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                // OR divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.or),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                GoogleSignInButton(
                    onClick = { launchGoogleSignIn() },
                    enabled = !isAuthenticating
                )
            }

            AuthStep.FORGOT_PASSWORD -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp),
                elevation = Elevation.level2
            ) {
                AuthStepHeader(
                    icon = Icons.Default.LockReset,
                    title = stringResource(R.string.reset_title),
                    subtitle = stringResource(R.string.reset_subtitle),
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                InputField(
                    value = emailInput,
                    onValueChange = {
                        emailInput = it.trim()
                        localErrorMessage = null
                    },
                    label = stringResource(R.string.email_address),
                    placeholder = stringResource(R.string.you_example_com),
                    leadingIcon = Icons.Default.Email,
                    modifier = Modifier.fillMaxWidth().testTag("reset_email_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                ProPrimaryButton(
                    text = if (isAuthenticating) stringResource(R.string.sending) else stringResource(R.string.send_reset_link),
                    onClick = {
                        localErrorMessage = null
                        authViewModel.requestPasswordReset(emailInput) {
                            emailResendCountdownSeconds = EMAIL_RESEND_COOLDOWN_SECONDS
                            step = AuthStep.RESET_SENT
                        }
                    },
                    enabled = !isAuthenticating,
                    icon = Icons.AutoMirrored.Filled.Send,
                    modifier = Modifier.fillMaxWidth().testTag("send_reset_button")
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                BackToSignIn { goTo(AuthStep.SIGN_IN) }
            }

            AuthStep.RESET_SENT -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp),
                elevation = Elevation.level2
            ) {
                AuthStepHeader(
                    icon = Icons.Default.MarkEmailRead,
                    title = stringResource(R.string.check_your_email),
                    subtitle = stringResource(R.string.reset_sent_body_x, emailInput),
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                ProHostAlertBanner(message = stringResource(R.string.reset_sent_hint))

                Spacer(modifier = Modifier.height(Spacing.md))

                if (emailResendCountdownSeconds > 0) {
                    Text(
                        text = stringResource(R.string.resend_in_xs, emailResendCountdownSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    TextButton(
                        onClick = {
                            authViewModel.requestPasswordReset(emailInput) {
                                emailResendCountdownSeconds = EMAIL_RESEND_COOLDOWN_SECONDS
                            }
                        },
                        enabled = !isAuthenticating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.resend_reset_link), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }

                BackToSignIn { goTo(AuthStep.SIGN_IN) }
            }

            AuthStep.REGISTRATION_FORM -> ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp),
                elevation = Elevation.level2
            ) {
                AuthStepHeader(
                    icon = Icons.Default.AppRegistration,
                    title = stringResource(R.string.complete_your_profile),
                    subtitle = stringResource(R.string.your_identity_is_verified_just_a_few_more),
                    isBusy = isAuthenticating
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Show the verified identity (email or phone) as a non-editable badge
                val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val firebaseEmail = firebaseUser?.email ?: ""
                val firebasePhone = firebaseUser?.phoneNumber ?: resumePhoneE164 ?: ""

                val signedInAs = when {
                    firebaseEmail.isNotBlank() -> uiText(R.string.signed_in_as_x, firebaseEmail)
                    firebasePhone.isNotBlank() -> uiText(R.string.phone_verified_x, firebasePhone)
                    else -> null
                }
                if (signedInAs != null) {
                    ProHostAlertBanner(message = signedInAs, severity = ProHostAlertSeverity.SUCCESS, icon = Icons.Default.VerifiedUser)
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ProfilePicturePickerField(
                        pictureUri = regProfilePicUri,
                        onPictureSelected = { regProfilePicUri = it }
                    )
                    Column {
                        Text(stringResource(R.string.profile_picture_2), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.optional), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                InputField(
                    value = regFullName,
                    onValueChange = {
                        regFullName = it
                        localErrorMessage = null
                    },
                    label = stringResource(R.string.full_name),
                    placeholder = stringResource(R.string.e_g_maya_haddad),
                    leadingIcon = Icons.Default.Person,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.md))

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
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = firebaseEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                InputField(
                    value = regSpecialty,
                    onValueChange = {
                        regSpecialty = it
                        localErrorMessage = null
                    },
                    label = stringResource(R.string.profession_job_title_optional),
                    placeholder = stringResource(R.string.e_g_dermatologist_architect_coworking_mana),
                    leadingIcon = Icons.Default.Badge,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                CountryDropdownField(
                    selectedCountry = regCountry,
                    onCountrySelected = { regCountry = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                InputField(
                    value = regGovernorateArea,
                    onValueChange = {
                        regGovernorateArea = it
                        localErrorMessage = null
                    },
                    label = stringResource(R.string.governorate_area),
                    placeholder = stringResource(R.string.e_g_mount_lebanon),
                    leadingIcon = Icons.Default.LocationOn,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                InputField(
                    value = regCity,
                    onValueChange = {
                        regCity = it
                        localErrorMessage = null
                    },
                    label = stringResource(R.string.city),
                    placeholder = stringResource(R.string.e_g_beirut),
                    leadingIcon = Icons.Default.LocationCity,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                PhoneNumberField(
                    country = regWhatsAppCountry,
                    onCountryChange = { regWhatsAppCountry = it },
                    number = regPhoneNumber,
                    onNumberChange = { regPhoneNumber = it; localErrorMessage = null },
                    label = stringResource(R.string.whatsapp_number_2),
                    placeholder = "71 234 567",
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.hosts_and_guests_reach_you_here_never_show),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = pickDeviceNumber) {
                        Icon(Icons.Default.SimCard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.use_this_phone_s_number), style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

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
                                text = stringResource(R.string.i_agree_to_the),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.terms_of_use),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showLegalDocument = com.example.legal.LegalContent.termsOfUse }
                            )
                        }
                        Row {
                            Text(text = stringResource(R.string.and), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = stringResource(R.string.privacy_policy),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showLegalDocument = com.example.legal.LegalContent.privacyPolicy }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                ProPrimaryButton(
                    text = if (isAuthenticating) stringResource(R.string.creating_account) else stringResource(R.string.create_account),
                    onClick = {
                        if (regFullName.isBlank()) {
                            localErrorMessage = uiText(R.string.please_enter_your_full_name)
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
                            localErrorMessage = uiText(R.string.please_enter_your_governorate_area)
                            return@ProPrimaryButton
                        }
                        if (regCity.isBlank()) {
                            localErrorMessage = uiText(R.string.please_enter_your_city)
                            return@ProPrimaryButton
                        }
                        // A legacy phone-auth account already has its number; everyone else gives
                        // their WhatsApp number here (required: booking and hosting need it).
                        val registrationPhoneE164 = resumePhoneE164?.takeIf { it.isNotBlank() }
                            ?: com.example.data.model.formatToE164(regWhatsAppCountry, regPhoneNumber)
                        if (regPhoneNumber.filter { it.isDigit() }.length < 6 && resumePhoneE164.isNullOrBlank() ||
                            !com.example.data.model.isPlausibleE164(registrationPhoneE164)
                        ) {
                            localErrorMessage = uiText(R.string.please_enter_your_whatsapp_number_with_its)
                            return@ProPrimaryButton
                        }
                        if (!tosAccepted) {
                            localErrorMessage = uiText(R.string.please_agree_to_the_terms_of_use_and_priva)
                            return@ProPrimaryButton
                        }
                        authViewModel.completePendingRegistration(
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
                        authViewModel.signOut()
                        emailInput = ""
                        goTo(AuthStep.SIGN_IN)
                        onCancelResume?.invoke()
                    },
                    enabled = !isAuthenticating
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.start_over_with_a_different_account), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
                Spacer(modifier = Modifier.width(Spacing.md))
                Text(
                    text = stringResource(R.string.auth_footer_secure),
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

/** Live checklist under the new-password field, mirroring the server's password policy. */
@Composable
private fun PasswordChecklist(password: String) {
    val check = com.example.util.PasswordRules.check(password)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth().testTag("password_rules")) {
        PasswordRule(check.longEnough, stringResource(R.string.password_rule_length))
        PasswordRule(check.hasLowercase, stringResource(R.string.password_rule_lowercase))
        PasswordRule(check.hasNumber, stringResource(R.string.password_rule_number))
    }
}

@Composable
private fun PasswordRule(met: Boolean, label: String) {
    val color = if (met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(
            if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.semantics { stateDescription = if (met) "✓" else "" }
        )
    }
}

@Composable
private fun BackToSignIn(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(stringResource(R.string.back_to_sign_in), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
            Spacer(modifier = Modifier.width(Spacing.md))
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

