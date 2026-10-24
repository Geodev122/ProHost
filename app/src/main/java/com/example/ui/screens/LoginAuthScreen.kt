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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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

import androidx.activity.compose.BackHandler
/**
 * Sign in or create an account (1.0.45: staged, "intent first"):
 *  WELCOME (Create an account · I already have one) → PROVIDERS (Google · [Apple] · Email) → EMAIL → PASSWORD,
 *  FORGOT_PASSWORD → RESET_SENT, and after a new sign-up the registration in three steps (REG_PROFILE, REG_PLACE,
 *  REG_WHATSAPP). Every step is one short screen (auth/AuthSteps.kt); this composable keeps the state and the actions.
 * Email enumeration protection: Forgot password never says whether the address has an account.
 * There is no emailed code, magic link or SMS sign-in.
 */
internal enum class AuthStep { WELCOME, PROVIDERS, EMAIL, PASSWORD, FORGOT_PASSWORD, RESET_SENT, REG_PROFILE, REG_PLACE, REG_WHATSAPP }

/** What the person chose on the welcome screen. */
internal enum class AuthIntent { SIGN_IN, CREATE }

/** "Send again" cooldown for the reset email (the server also limits 5 per hour per address). */
private const val EMAIL_RESEND_COOLDOWN_SECONDS = 60

// Savers for rememberSaveable — process death would otherwise lose all in-progress state.
private val AuthStepSaver = Saver<AuthStep, String>(
    save = { it.name },
    restore = { name -> AuthStep.entries.firstOrNull { it.name == name } ?: AuthStep.WELCOME }
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
    /** Screenshot tests only: open on a given step (AuthStep name) and intent (AuthIntent name) with an email filled in. */
    initialStepName: String? = null,
    initialEmail: String? = null,
    initialIntentName: String? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()
    var step by rememberSaveable(stateSaver = AuthStepSaver) {
        mutableStateOf(
            when {
                resumeAtRegistration -> AuthStep.REG_PROFILE
                else -> AuthStep.entries.firstOrNull { it.name == initialStepName } ?: AuthStep.WELCOME
            }
        )
    }

    var intent by rememberSaveable { mutableStateOf(AuthIntent.entries.firstOrNull { it.name == initialIntentName } ?: AuthIntent.SIGN_IN) }
    val creating = intent == AuthIntent.CREATE
    // Each step is its own GA4 screen (auth_welcome, auth_providers, auth_email, auth_password, …, registration_profile/place/whatsapp).
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
                            step = AuthStep.REG_PROFILE
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

    // Loading state: isAuthenticating drives each step's CircularProgressIndicator (AuthStepScaffold) and disables its actions.
    val isAuthenticating by authViewModel.isAuthenticating.collectAsState()
    val authErrorMessage by authViewModel.authErrorMessage.collectAsState()

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

    // Google pre-fills the first registration step; the SIM-number picker opens once on the WhatsApp step.
    LaunchedEffect(step) {
        if (step == AuthStep.REG_PROFILE) {
            if (prefillGoogleName.isNotBlank() && regFullName.isBlank()) regFullName = prefillGoogleName
            if (prefillGooglePictureUri != null && regProfilePicUri == null) regProfilePicUri = prefillGooglePictureUri
        }
        if (step == AuthStep.REG_WHATSAPP) {
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
        step = AuthStep.REG_PROFILE
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

    // Returning people: offer a saved password or an already-used Google account once when they pick
    // "I already have an account" (Credential Manager). Nothing saved → nothing shows.
    var savedCredentialOffered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(step, intent) {
        if (isPreview || savedCredentialOffered || step != AuthStep.PROVIDERS || creating) return@LaunchedEffect
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
                        step = AuthStep.REG_PROFILE
                    } else onLoginSuccess()
                }
            }
            else -> Unit
        }
    }

    fun goTo(next: AuthStep) {
        localErrorMessage = null
        authViewModel.clearAuthMessages()
        if (next != AuthStep.PASSWORD) passwordInput = ""
        showPassword = false
        step = next
    }

    fun startOver() {
        authViewModel.signOut()
        emailInput = ""
        intent = AuthIntent.SIGN_IN
        goTo(AuthStep.WELCOME)
        onCancelResume?.invoke()
    }

    // System Back walks the path backwards; on the welcome screen it leaves the app as usual.
    val previous: AuthStep? = when (step) {
        AuthStep.WELCOME -> null
        AuthStep.PROVIDERS -> AuthStep.WELCOME
        AuthStep.EMAIL -> AuthStep.PROVIDERS
        AuthStep.PASSWORD -> AuthStep.EMAIL
        AuthStep.FORGOT_PASSWORD -> AuthStep.PASSWORD
        AuthStep.RESET_SENT -> AuthStep.PASSWORD
        AuthStep.REG_PROFILE -> null
        AuthStep.REG_PLACE -> AuthStep.REG_PROFILE
        AuthStep.REG_WHATSAPP -> AuthStep.REG_PLACE
    }
    BackHandler(enabled = previous != null && !isAuthenticating) { previous?.let { goTo(it) } }

    val error = localErrorMessage ?: authErrorMessage
    val showTerms = { showLegalDocument = com.example.legal.LegalContent.termsOfUse }
    val showPrivacy = { showLegalDocument = com.example.legal.LegalContent.privacyPolicy }

    fun submitPassword() {
        localErrorMessage = null
        val email = emailInput.trim().lowercase()
        val password = passwordInput
        if (creating) authViewModel.createAccount(email, password, afterPasswordAuth(email, password))
        else authViewModel.signIn(email, password, afterPasswordAuth(email, password))
    }

    fun sendReset(next: Boolean) {
        localErrorMessage = null
        authViewModel.requestPasswordReset(emailInput) {
            emailResendCountdownSeconds = EMAIL_RESEND_COOLDOWN_SECONDS
            if (next) step = AuthStep.RESET_SENT
        }
    }

    // Email comes from Firebase Auth (email or Google sign-in); a legacy phone-auth account may have none.
    val firebaseUser = if (isPreview) null else runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }.getOrNull()
    val firebaseEmail = firebaseUser?.email ?: ""

    fun finishRegistration() {
        val registrationPhoneE164 = resumePhoneE164?.takeIf { it.isNotBlank() }
            ?: com.example.data.model.formatToE164(regWhatsAppCountry, regPhoneNumber)
        if (regPhoneNumber.filter { it.isDigit() }.length < 6 && resumePhoneE164.isNullOrBlank() ||
            !com.example.data.model.isPlausibleE164(registrationPhoneE164)
        ) {
            localErrorMessage = uiText(R.string.please_enter_your_whatsapp_number_with_its)
            return
        }
        if (!tosAccepted) {
            localErrorMessage = uiText(R.string.please_agree_to_the_terms_of_use_and_priva)
            return
        }
        authViewModel.completePendingRegistration(
            registration = AuthViewModel.PendingRegistration(
                fullName = regFullName.trim(),
                email = firebaseEmail,
                phoneE164 = registrationPhoneE164,
                specialty = regSpecialty.trim(),
                country = regCountry.name,
                governorate = regGovernorateArea.trim(),
                city = regCity.trim(),
                profilePictureUri = regProfilePicUri,
                tosAccepted = tosAccepted
            ),
            onSuccess = onLoginSuccess
        )
    }

    when (step) {
        AuthStep.WELCOME -> WelcomeStep(
            onCreate = { intent = AuthIntent.CREATE; goTo(AuthStep.PROVIDERS) },
            onSignIn = { intent = AuthIntent.SIGN_IN; goTo(AuthStep.PROVIDERS) },
            onTerms = showTerms,
            onPrivacy = showPrivacy
        )

        AuthStep.PROVIDERS -> ProvidersStep(
            creating = creating,
            isBusy = isAuthenticating,
            error = error,
            onBack = { goTo(AuthStep.WELCOME) },
            onProvider = { provider ->
                when (provider) {
                    AuthProvider.GOOGLE -> launchGoogleSignIn()
                    AuthProvider.APPLE -> Unit // enabled together with its Firebase provider (AuthProviders.APPLE_ENABLED)
                    AuthProvider.EMAIL -> goTo(AuthStep.EMAIL)
                }
            },
            onSwitchIntent = { intent = if (creating) AuthIntent.SIGN_IN else AuthIntent.CREATE; goTo(AuthStep.PROVIDERS) }
        )

        AuthStep.EMAIL -> EmailStep(
            email = emailInput,
            onEmailChange = { emailInput = it; localErrorMessage = null },
            error = error,
            onBack = { goTo(AuthStep.PROVIDERS) },
            onContinue = { goTo(AuthStep.PASSWORD) }
        )

        AuthStep.PASSWORD -> PasswordStep(
            creating = creating,
            email = emailInput,
            password = passwordInput,
            onPasswordChange = { passwordInput = it; localErrorMessage = null },
            showPassword = showPassword,
            onToggleShow = { showPassword = !showPassword },
            isBusy = isAuthenticating,
            error = error,
            onBack = { goTo(AuthStep.EMAIL) },
            onChangeEmail = { goTo(AuthStep.EMAIL) },
            onForgot = { goTo(AuthStep.FORGOT_PASSWORD) },
            onSubmit = { submitPassword() }
        )

        AuthStep.FORGOT_PASSWORD -> ForgotStep(
            email = emailInput,
            onEmailChange = { emailInput = it; localErrorMessage = null },
            isBusy = isAuthenticating,
            error = error,
            onBack = { goTo(AuthStep.PASSWORD) },
            onSend = { sendReset(next = true) }
        )

        AuthStep.RESET_SENT -> ResetSentStep(
            email = emailInput,
            countdownSeconds = emailResendCountdownSeconds,
            isBusy = isAuthenticating,
            onResend = { sendReset(next = false) },
            onBackToSignIn = { intent = AuthIntent.SIGN_IN; goTo(AuthStep.PASSWORD) }
        )

        AuthStep.REG_PROFILE -> RegistrationStepScaffold(
            step = 1,
            title = stringResource(R.string.reg_profile_title),
            subtitle = stringResource(R.string.reg_profile_subtitle),
            isBusy = isAuthenticating,
            error = error,
            primaryText = stringResource(R.string.next),
            primaryEnabled = regFullName.isNotBlank(),
            onPrimary = { goTo(AuthStep.REG_PLACE) },
            onBack = { startOver() },
            secondary = {
                TextButton(onClick = { startOver() }, enabled = !isAuthenticating) {
                    Text(stringResource(R.string.start_over_with_a_different_account), style = MaterialTheme.typography.labelLarge)
                }
            }
        ) {
            if (firebaseEmail.isNotBlank()) AuthInfoLine(Icons.Default.VerifiedUser, uiText(R.string.signed_in_as_x, firebaseEmail))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ProfilePicturePickerField(pictureUri = regProfilePicUri, onPictureSelected = { regProfilePicUri = it })
                Column {
                    Text(stringResource(R.string.profile_picture_2), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.optional), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            InputField(
                value = regFullName,
                onValueChange = { regFullName = it; localErrorMessage = null },
                label = stringResource(R.string.full_name),
                placeholder = stringResource(R.string.e_g_maya_haddad),
                leadingIcon = Icons.Default.Person,
                modifier = Modifier.fillMaxWidth().testTag("registration_full_name"),
                singleLine = true
            )
            InputField(
                value = regSpecialty,
                onValueChange = { regSpecialty = it },
                label = stringResource(R.string.profession_job_title_optional),
                placeholder = stringResource(R.string.e_g_dermatologist_architect_coworking_mana),
                leadingIcon = Icons.Default.Badge,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        AuthStep.REG_PLACE -> RegistrationStepScaffold(
            step = 2,
            title = stringResource(R.string.reg_place_title),
            subtitle = stringResource(R.string.reg_place_subtitle),
            isBusy = isAuthenticating,
            error = error,
            primaryText = stringResource(R.string.next),
            primaryEnabled = regGovernorateArea.isNotBlank() && regCity.isNotBlank(),
            onPrimary = { goTo(AuthStep.REG_WHATSAPP) },
            onBack = { goTo(AuthStep.REG_PROFILE) }
        ) {
            CountryDropdownField(selectedCountry = regCountry, onCountrySelected = { regCountry = it }, modifier = Modifier.fillMaxWidth())
            InputField(
                value = regGovernorateArea,
                onValueChange = { regGovernorateArea = it; localErrorMessage = null },
                label = stringResource(R.string.governorate_area),
                placeholder = stringResource(R.string.e_g_mount_lebanon),
                leadingIcon = Icons.Default.LocationOn,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            InputField(
                value = regCity,
                onValueChange = { regCity = it; localErrorMessage = null },
                label = stringResource(R.string.city),
                placeholder = stringResource(R.string.e_g_beirut),
                leadingIcon = Icons.Default.LocationCity,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        AuthStep.REG_WHATSAPP -> RegistrationStepScaffold(
            step = 3,
            title = stringResource(R.string.reg_whatsapp_title),
            subtitle = stringResource(R.string.hosts_and_guests_reach_you_here_never_show),
            isBusy = isAuthenticating,
            error = error,
            primaryText = stringResource(if (isAuthenticating) R.string.creating_account else R.string.finish),
            primaryEnabled = tosAccepted && (regPhoneNumber.isNotBlank() || !resumePhoneE164.isNullOrBlank()),
            onPrimary = { finishRegistration() },
            onBack = { goTo(AuthStep.REG_PLACE) }
        ) {
            PhoneNumberField(
                country = regWhatsAppCountry,
                onCountryChange = { regWhatsAppCountry = it },
                number = regPhoneNumber,
                onNumberChange = { regPhoneNumber = it; localErrorMessage = null },
                label = stringResource(R.string.whatsapp_number_2),
                placeholder = "71 234 567",
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = pickDeviceNumber) {
                Icon(Icons.Default.SimCard, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(stringResource(R.string.use_this_phone_s_number), style = MaterialTheme.typography.labelLarge)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { tosAccepted = !tosAccepted }.testTag("registration_terms"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = tosAccepted, onCheckedChange = { tosAccepted = it })
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.reg_terms_agree), style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = showTerms, contentPadding = PaddingValues(horizontal = Spacing.xs)) {
                            Text(stringResource(R.string.terms_of_use), style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = showPrivacy, contentPadding = PaddingValues(horizontal = Spacing.xs)) {
                            Text(stringResource(R.string.privacy_policy), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    showLegalDocument?.let { doc ->
        com.example.ui.components.LegalDocumentDialog(document = doc, onDismiss = { showLegalDocument = null })
    }
}
