package com.example.ui.screens

import com.example.ui.util.localizedLabel
import com.example.ui.util.UiMessages
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.findCountryByName
import com.example.ui.components.*
import com.example.ui.components.OutlinedButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

import com.example.shared.resources.Res
import com.example.shared.resources.city
import com.example.shared.resources.e_g_beirut
import com.example.shared.resources.e_g_dermatologist_architect_coworking_mana
import com.example.shared.resources.e_g_maya_haddad
import com.example.shared.resources.e_g_mount_lebanon
import com.example.shared.resources.full_name
import com.example.shared.resources.governorate_area
import com.example.shared.resources.hosts_and_guests_reach_you_here_never_show
import com.example.shared.resources.next
import com.example.shared.resources.optional
import com.example.shared.resources.privacy_policy
import com.example.shared.resources.profession_job_title_optional
import com.example.shared.resources.profile_picture_2
import com.example.shared.resources.reg_place_subtitle
import com.example.shared.resources.reg_place_title
import com.example.shared.resources.reg_profile_subtitle
import com.example.shared.resources.reg_profile_title
import com.example.shared.resources.reg_terms_agree
import com.example.shared.resources.reg_whatsapp_title
import com.example.shared.resources.start_over_with_a_different_account
import com.example.shared.resources.terms_of_use
import com.example.shared.resources.use_this_phone_s_number
import com.example.shared.resources.whatsapp_number_2
import com.example.ui.util.appString
import com.example.ui.util.sharedText
import com.example.shared.resources.creating_account
import com.example.shared.resources.finish
/**
 * Sign in or create an account (1.0.45: staged, "intent first"):
 *  WELCOME (Create an account · I already have one) → PROVIDERS (Google · [Apple] · Email) → EMAIL → PASSWORD,
 *  FORGOT_PASSWORD → RESET_SENT, and after a new sign-up the registration in three steps (REG_PROFILE, REG_PLACE,
 *  REG_WHATSAPP). Every step is one short screen (auth/AuthSteps.kt); this composable keeps the state and the actions.
 * Email enumeration protection: Forgot password never says whether the address has an account.
 * There is no emailed code, magic link or SMS sign-in.
 */
enum class AuthStep { WELCOME, PROVIDERS, EMAIL, PASSWORD, FORGOT_PASSWORD, RESET_SENT, REG_PROFILE, REG_PLACE, REG_WHATSAPP }

/** What the person chose on the welcome screen. */
enum class AuthIntent { SIGN_IN, CREATE }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginAuthScreen(
    onLoginSuccess: () -> Unit,
    resumeAtRegistration: Boolean = false,
    resumePhoneE164: String? = null,
    onCancelResume: (() -> Unit)? = null,
    authViewModel: AuthViewModel = viewModel { AuthViewModel() },
    /** Screenshot tests only: open on a given step (AuthStep name) and intent (AuthIntent name) with an email filled in. */
    initialStepName: String? = null,
    initialEmail: String? = null,
    initialIntentName: String? = null
) {
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

    // Credential Manager (Android) / platform sign-in for Google One Tap and saved passwords
    val authCredentials = rememberAuthCredentials()

    // Pre-fill values extracted from Google credential — applied to the registration form
    // via LaunchedEffect(step) below, after regFullName/regProfilePic are initialized.
    var prefillGoogleName by rememberSaveable { mutableStateOf("") }
    var prefillGooglePictureUri by rememberSaveable { mutableStateOf<String?>(null) }

    // Launches Google sign-in. Tries One Tap (GetGoogleIdOption) first; if that
    // produces NoCredentialException (can happen on fresh devices or after the user
    // previously dismissed One Tap too many times), falls back to the standard
    // Sign In With Google bottom-sheet (GetSignInWithGoogleOption), which is always
    // available when Play Services is present.
    fun launchAppleSignIn() {
        coroutineScope.launch {
            val creds = authCredentials ?: return@launch
            val rawNonce = com.example.data.auth.GoogleNonce.newNonce()
            when (val result = creds.requestApple(rawNonce)) {
                is com.example.ui.components.AppleCredentialResult.Token -> {
                    val appleName = "${result.givenName.orEmpty()} ${result.familyName.orEmpty()}".trim()
                    authViewModel.startAppleSignIn(result.idToken, rawNonce, result.givenName, result.familyName) { needsRegistration ->
                        if (needsRegistration) {
                            if (appleName.isNotEmpty()) prefillGoogleName = appleName
                            step = AuthStep.REG_PROFILE
                        } else {
                            onLoginSuccess()
                        }
                    }
                }
                com.example.ui.components.AppleCredentialResult.Cancelled ->
                    com.example.analytics.AnalyticsTracker.authError("apple", "cancelled")
                com.example.ui.components.AppleCredentialResult.Error -> {
                    com.example.analytics.AnalyticsTracker.authError("apple", "credential_error")
                    UiMessages.show(sharedText("an_error_occurred_please_try_again"), long = true)
                }
            }
        }
    }

    fun launchGoogleSignIn() {
        coroutineScope.launch {
            val creds = authCredentials ?: return@launch
            // One nonce per attempt, in every request below; the ID token must carry it back (see GoogleNonce).
            val nonce = com.example.data.auth.GoogleNonce.newNonce()
            when (val result = creds.requestGoogle(nonce)) {
                is GoogleCredentialResult.Token -> {
                    if (!com.example.data.auth.GoogleNonce.idTokenMatchesNonce(result.idToken, nonce)) {
                        com.example.analytics.AnalyticsTracker.authError("google", "nonce_mismatch")
                        UiMessages.show(sharedText("google_sign_in_isn_t_available_right_now_p"),
                            long = true)
                        return@launch
                    }
                    val googleName = result.displayName
                        ?: "${result.givenName.orEmpty()} ${result.familyName.orEmpty()}".trim()
                    val googlePictureUri = result.pictureUri
                    authViewModel.startGoogleSignIn(googleIdToken = result.idToken) { needsRegistration ->
                        if (needsRegistration) {
                            prefillGoogleName = googleName
                            prefillGooglePictureUri = googlePictureUri
                            step = AuthStep.REG_PROFILE
                        } else {
                            onLoginSuccess()
                        }
                    }
                }
                GoogleCredentialResult.Cancelled -> com.example.analytics.AnalyticsTracker.authError("google", "cancelled")
                GoogleCredentialResult.Unavailable -> {
                    com.example.analytics.AnalyticsTracker.authError("google", "unavailable")
                    UiMessages.show(sharedText("google_sign_in_isn_t_available_on_this_dev"),
                        long = true)
                }
                GoogleCredentialResult.Error -> {
                    com.example.analytics.AnalyticsTracker.authError("google", "credential_error")
                    UiMessages.show(sharedText("google_sign_in_isn_t_available_right_now_p"),
                        long = true)
                }
                GoogleCredentialResult.NotGoogle -> Unit
            }
        }
    }

    var regWhatsAppCountry by rememberSaveable(stateSaver = CountrySaver) { mutableStateOf(findCountryByName("Lebanon")) }

    LaunchedEffect(Unit) {
        if (isPreview) return@LaunchedEffect
        authViewModel.clearStaleAuthMessages()
        // MainActivity already requests all permissions; detect country using available signals
        // (SIM/locale — no duplicate permission request here).
        com.example.util.CountryLookup.device()?.let { regWhatsAppCountry = it }
    }

    // --- Registration form state ---
    var regProfilePic by rememberSaveable(stateSaver = com.example.util.PickedFileSaver) { mutableStateOf<com.example.util.PickedFile?>(null) }
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
    }, onUnavailable = {
        // No SIM number to offer (or Play services can't show the sheet): say so instead of doing nothing.
        com.example.analytics.AnalyticsTracker.phoneHint("unavailable", "registration")
        com.example.ui.util.UiMessages.show(sharedText("phone_hint_unavailable"))
    })
    var phoneHintOffered by rememberSaveable { mutableStateOf(false) }

    // Registration step 2: "Use my current location" → platform location (permission through rememberLocationAccess) →
    // English reverse geocode → country, area (matched to the dropdown's list when it can be) and city.
    var locatingPlace by remember { mutableStateOf(false) }
    val requestPlaceLocation = com.example.ui.components.rememberLocationAccess(onReady = {
        coroutineScope.launch {
            locatingPlace = true
            val place = detectRegistrationPlace()
            locatingPlace = false
            if (place == null) {
                com.example.ui.util.UiMessages.show(sharedText("reg_location_failed"))
            } else {
                place.country?.let { regCountry = it }
                regGovernorateArea = place.area
                regCity = place.city
                localErrorMessage = null
            }
        }
    })

    // Google pre-fills the first registration step; the SIM-number picker opens once on the WhatsApp step.
    LaunchedEffect(step) {
        if (step == AuthStep.REG_PROFILE) {
            if (prefillGoogleName.isNotBlank() && regFullName.isBlank()) regFullName = prefillGoogleName
            if (prefillGooglePictureUri != null && regProfilePic == null) regProfilePic = prefillGooglePictureUri?.let { com.example.util.PickedFile(it) }
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
            authCredentials?.savePassword(email, password)
            if (needsRegistration) goToRegistrationForm() else onLoginSuccess()
        }
    }

    // Returning people: offer a saved password or an already-used Google account once when they pick
    // "I already have an account" (Credential Manager). Nothing saved → nothing shows.
    var savedCredentialOffered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(step, intent) {
        if (isPreview || savedCredentialOffered || step != AuthStep.PROVIDERS || creating) return@LaunchedEffect
        savedCredentialOffered = true
        val creds = authCredentials ?: return@LaunchedEffect
        val returningNonce = com.example.data.auth.GoogleNonce.newNonce()
        when (val credential = creds.offerSaved(returningNonce) ?: return@LaunchedEffect) {
            is SavedCredential.Password -> {
                emailInput = credential.id
                authViewModel.signIn(credential.id, credential.password) { needsRegistration ->
                    if (needsRegistration) goToRegistrationForm() else onLoginSuccess()
                }
            }
            is SavedCredential.Google -> {
                if (!com.example.data.auth.GoogleNonce.idTokenMatchesNonce(credential.idToken, returningNonce)) {
                    com.example.analytics.AnalyticsTracker.authError("google", "nonce_mismatch")
                    return@LaunchedEffect
                }
                authViewModel.startGoogleSignIn(credential.idToken) { needsRegistration ->
                    if (needsRegistration) {
                        prefillGoogleName = credential.displayName.orEmpty()
                        prefillGooglePictureUri = credential.pictureUri
                        step = AuthStep.REG_PROFILE
                    } else onLoginSuccess()
                }
            }
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
    PlatformBackHandler(enabled = previous != null && !isAuthenticating) { previous?.let { goTo(it) } }

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
    val firebaseUser = if (isPreview) null else runCatching { com.example.data.auth.SessionAuth.currentUser() }.getOrNull()
    val firebaseEmail = firebaseUser?.email ?: ""

    fun finishRegistration() {
        val registrationPhoneE164 = resumePhoneE164?.takeIf { it.isNotBlank() }
            ?: com.example.data.model.formatToE164(regWhatsAppCountry, regPhoneNumber)
        if (regPhoneNumber.filter { it.isDigit() }.length < 6 && resumePhoneE164.isNullOrBlank() ||
            !com.example.data.model.isPlausibleE164(registrationPhoneE164)
        ) {
            localErrorMessage = sharedText("please_enter_your_whatsapp_number_with_its")
            return
        }
        if (!tosAccepted) {
            localErrorMessage = sharedText("please_agree_to_the_terms_of_use_and_priva")
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
                city = regCity.trim().ifBlank { regGovernorateArea.trim() },
                profilePicture = regProfilePic,
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
                    AuthProvider.APPLE -> launchAppleSignIn()
                    AuthProvider.EMAIL -> goTo(AuthStep.EMAIL)
                }
            },
            onSwitchIntent = { intent = if (creating) AuthIntent.SIGN_IN else AuthIntent.CREATE; goTo(AuthStep.PROVIDERS) },
            appleSupported = authCredentials?.supportsApple == true
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
            title = appString(Res.string.reg_profile_title),
            subtitle = appString(Res.string.reg_profile_subtitle),
            isBusy = isAuthenticating,
            error = error,
            primaryText = appString(Res.string.next),
            primaryEnabled = regFullName.isNotBlank(),
            onPrimary = { goTo(AuthStep.REG_PLACE) },
            onBack = { startOver() },
            secondary = {
                TextButton(onClick = { startOver() }, enabled = !isAuthenticating) {
                    Text(appString(Res.string.start_over_with_a_different_account), style = MaterialTheme.typography.labelLarge)
                }
            }
        ) {
            if (firebaseEmail.isNotBlank()) AuthInfoLine(Icons.Default.VerifiedUser, sharedText("signed_in_as_x", firebaseEmail))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ProfilePicturePickerField(pictureFile = regProfilePic, onPictureSelected = { regProfilePic = it })
                Column {
                    Text(appString(Res.string.profile_picture_2), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(appString(Res.string.optional), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            InputField(
                value = regFullName,
                onValueChange = { regFullName = it; localErrorMessage = null },
                label = appString(Res.string.full_name),
                placeholder = appString(Res.string.e_g_maya_haddad),
                leadingIcon = Icons.Default.Person,
                modifier = Modifier.fillMaxWidth().testTag("registration_full_name"),
                singleLine = true
            )
            InputField(
                value = regSpecialty,
                onValueChange = { regSpecialty = it },
                label = appString(Res.string.profession_job_title_optional),
                placeholder = appString(Res.string.e_g_dermatologist_architect_coworking_mana),
                leadingIcon = Icons.Default.Badge,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        AuthStep.REG_PLACE -> RegistrationStepScaffold(
            step = 2,
            title = appString(Res.string.reg_place_title),
            subtitle = appString(Res.string.reg_place_subtitle),
            isBusy = isAuthenticating,
            error = error,
            primaryText = appString(Res.string.next),
            primaryEnabled = regGovernorateArea.isNotBlank() && !locatingPlace,
            onPrimary = { goTo(AuthStep.REG_WHATSAPP) },
            onBack = { goTo(AuthStep.REG_PROFILE) }
        ) {
            // Two ways in: the device's position (country, area and city filled by the platform geocoder), or the country and
            // area dropdowns. The city is never typed: it comes from the detected position, else the area stands for it.
            OutlinedButton(
                onClick = { if (!locatingPlace) requestPlaceLocation() },
                enabled = !isAuthenticating && !locatingPlace,
                modifier = Modifier.fillMaxWidth().testTag("registration_use_location")
            ) {
                if (locatingPlace) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(appString(if (locatingPlace) Res.string.reg_locating else Res.string.reg_use_my_location))
            }
            CountryDropdownField(
                selectedCountry = regCountry,
                onCountrySelected = {
                    if (it.isoCode != regCountry.isoCode) { regGovernorateArea = ""; regCity = "" }
                    regCountry = it
                    localErrorMessage = null
                },
                modifier = Modifier.fillMaxWidth()
            )
            val areas = remember(regCountry.isoCode) { com.example.data.model.Regions.of(regCountry.isoCode) }
            if (areas.isNotEmpty()) {
                com.example.ui.components.SelectField(
                    label = appString(Res.string.reg_area),
                    options = areas,
                    selected = regGovernorateArea.takeIf { it in areas },
                    optionLabel = { com.example.data.model.Regions.label(regCountry.isoCode, it, com.example.ui.util.AppLanguage.current()) },
                    onSelect = { regGovernorateArea = it; regCity = ""; localErrorMessage = null },
                    placeholder = appString(Res.string.reg_area_choose),
                    modifier = Modifier.fillMaxWidth().testTag("registration_area")
                )
            } else {
                InputField(
                    value = regGovernorateArea,
                    onValueChange = { regGovernorateArea = it; regCity = ""; localErrorMessage = null },
                    label = appString(Res.string.reg_area),
                    placeholder = appString(Res.string.reg_area_choose),
                    leadingIcon = Icons.Default.LocationOn,
                    modifier = Modifier.fillMaxWidth().testTag("registration_area"),
                    singleLine = true
                )
            }
            if (regCity.isNotBlank() && regCity != regGovernorateArea) {
                AuthInfoLine(Icons.Default.LocationCity, regCity)
            }
        }

        AuthStep.REG_WHATSAPP -> RegistrationStepScaffold(
            step = 3,
            title = appString(Res.string.reg_whatsapp_title),
            subtitle = appString(Res.string.hosts_and_guests_reach_you_here_never_show),
            isBusy = isAuthenticating,
            error = error,
            primaryText = appString(if (isAuthenticating) Res.string.creating_account else Res.string.finish),
            primaryEnabled = tosAccepted && (regPhoneNumber.isNotBlank() || !resumePhoneE164.isNullOrBlank()),
            onPrimary = { finishRegistration() },
            onBack = { goTo(AuthStep.REG_PLACE) }
        ) {
            PhoneNumberField(
                country = regWhatsAppCountry,
                onCountryChange = { regWhatsAppCountry = it },
                number = regPhoneNumber,
                onNumberChange = { regPhoneNumber = it; localErrorMessage = null },
                label = appString(Res.string.whatsapp_number_2),
                placeholder = "71 234 567",
                modifier = Modifier.fillMaxWidth()
            )
            // iOS gives apps no access to the SIM number: there the keyboard's AutoFill offers the person's own number
            // (PhoneNumberField's phone content type), so the button is shown only where the system picker exists.
            if (com.example.ui.components.phoneNumberHintAvailable) TextButton(onClick = pickDeviceNumber) {
                Icon(Icons.Default.SimCard, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(appString(Res.string.use_this_phone_s_number), style = MaterialTheme.typography.labelLarge)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { tosAccepted = !tosAccepted }.testTag("registration_terms"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = tosAccepted, onCheckedChange = { tosAccepted = it })
                Column(modifier = Modifier.weight(1f)) {
                    Text(appString(Res.string.reg_terms_agree), style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = showTerms, contentPadding = PaddingValues(horizontal = Spacing.xs)) {
                            Text(appString(Res.string.terms_of_use), style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = showPrivacy, contentPadding = PaddingValues(horizontal = Spacing.xs)) {
                            Text(appString(Res.string.privacy_policy), style = MaterialTheme.typography.labelMedium)
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


/** What "Use my current location" found on registration step 2. */
private data class RegistrationPlace(val country: com.example.data.model.Country?, val area: String, val city: String)

/** Device position → English address → country, area and city; null when there is no fix or no usable address. */
private suspend fun detectRegistrationPlace(): RegistrationPlace? {
    val provider = com.example.util.LocationProvider.current ?: return null
    val geocoder = com.example.util.Geocoding.current ?: return null
    val point = (com.example.util.locate(provider, fallbackToLast = true) as? com.example.util.LocateResult.Found)?.point ?: return null
    val hit = geocoder.reverse(point.latitude, point.longitude, "en") ?: return null
    val code = hit.countryCode?.uppercase().orEmpty()
    val country = com.example.data.model.COUNTRIES.firstOrNull { it.isoCode.equals(code, ignoreCase = true) }
    val rawArea = hit.adminArea ?: hit.subAdminArea
    val area = com.example.data.model.Regions.match(code, rawArea) ?: rawArea.orEmpty()
    val city = (hit.locality ?: hit.subAdminArea).orEmpty()
    if (area.isBlank() && city.isBlank()) return null
    return RegistrationPlace(country, area.ifBlank { city }, city)
}
