package com.example.ui.viewmodel

import com.example.R
import com.example.ui.util.uiText
import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthResult
import com.example.data.auth.FirebaseAuthService
import com.example.data.auth.isCallableUnavailable
import com.example.data.auth.toUserMessage
import com.example.data.model.isProfileComplete
import com.example.data.repository.ProHostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for LoginAuthScreen and KycScreen — sign-in/registration flow.
 * Supports Email magic-link, Google One Tap, and phone-OTP (legacy parallel path).
 *
 * Role is NEVER taken from the client here. Sign-in resolves the caller's role from
 * their Firebase Auth ID token's custom claim (assigned server-side by the
 * assignInitialRole/grantAdminRole Cloud Functions, or by grantEntitlement() the
 * moment a package/listing payment settles) — see
 * com.example.data.auth.completeVerifiedLogin / completeVerifiedRegistration.
 */
class AuthViewModel(
    private val repository: ProHostRepository = ProHostRepository.getInstance()
) : ViewModel() {

    private val functionsClient = com.example.data.auth.FirebaseFunctionsClient()

    // Which sign-in path is in flight, for analytics login/sign_up "method".
    private var authMethod = "unknown"

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    private val _authSuccessMessage = MutableStateFlow<String?>(null)
    val authSuccessMessage: StateFlow<String?> = _authSuccessMessage.asStateFlow()

    fun clearAuthMessages() {
        _authErrorMessage.value = null
        _authSuccessMessage.value = null
    }

    // Set when a tapped sign-in link fails, so the login screen opening right after shows it.
    private var linkErrorFresh = false

    /** On entering the login screen: clears leftovers, but not an error a sign-in link just produced. */
    fun clearStaleAuthMessages() {
        if (linkErrorFresh) { linkErrorFresh = false; return }
        clearAuthMessages()
    }

    /**
     * Everything the registration form collects, submitted only AFTER the user is
     * already authenticated (phone OTP, email link, or Google). [phoneE164] defaults
     * to "" for email/Google-auth users — phone verification moves to the KYC gate.
     * Registration never collects a government ID document — that's the KYC flow's
     * job (KycScreen.kt / RequirementsSheet.kt), required before a Specialist can
     * book a space or before upgrading to Pro Host.
     */
    data class PendingRegistration(
        val fullName: String,
        val email: String,
        val phoneE164: String = "",
        val specialty: String,
        val country: String,
        val governorate: String,
        val city: String,
        val profilePictureUri: Uri?,
        // The registration form's Terms of Use / Privacy Policy checkbox must have
        // actually been checked before this reaches here — enforced client-side by
        // the form's own submit gate, and again server-side by assignInitialRole.ts,
        // which rejects registration outright if this isn't literally true.
        val tosAccepted: Boolean
    )

    // --- Email sign-in state ---

    /** How the sign-in email was delivered, so the screen can show the matching step. */
    enum class EmailDelivery { LINK, CODE }

    private val _pendingEmail = MutableStateFlow("")
    val pendingEmail: StateFlow<String> = _pendingEmail.asStateFlow()

    // Raised whenever a verified sign-in turns out to need the registration form. The
    // email-link and one-click-code deep links complete sign-in from ProHostAppRoot, not
    // from LoginAuthScreen, so the screen observes this instead of relying on a callback.
    private val _registrationRequested = MutableStateFlow(false)
    val registrationRequested: StateFlow<Boolean> = _registrationRequested.asStateFlow()

    fun consumeRegistrationRequest() {
        _registrationRequested.value = false
    }

    // ---

    /**
     * Helper to get a Firebase-initialized context without needing an Activity.
     * Safe because Firebase is always initialized before any auth call in this app.
     */
    private fun firebaseAppContext(): android.content.Context =
        com.google.firebase.FirebaseApp.getInstance().applicationContext

    /**
     * Backfills this device's current FCM token onto [uid]'s profile right after a
     * successful sign-in/registration.
     */
    @Suppress("DEPRECATION")
    private fun registerFcmTokenForCurrentUser(uid: String) {
        viewModelScope.launch {
            runCatching {
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                repository.registerFcmToken(uid, token)
            }
        }
    }

    // --- Email / Google auth methods ---

    /**
     * Starts email sign-in/sign-up. There is deliberately no "does this email exist"
     * lookup: Firebase's email-enumeration protection makes that answer always empty,
     * and new vs. returning is decided after verification from the stored profile.
     * Sends a magic link; if the link service is unavailable, falls back to a 6-digit
     * code so a new user is never stuck on a raw backend error.
     */
    fun startEmailSignIn(email: String, onDelivered: (EmailDelivery) -> Unit) {
        authMethod = "email_link"
        com.example.analytics.AnalyticsTracker.authStart("email_link")
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authErrorMessage.value = null
            _pendingEmail.value = email
            savePendingEmailLink(email)
            try {
                // Layer 1: Firebase Auth's own mailer (sendSignInLinkToEmail) — no mail server
                // of ours involved.
                val continueUrl = "https://prohost-f766f.web.app/emaillink"
                val native = FirebaseAuthService(firebaseAppContext())
                    .sendSignInLinkToEmail(email, continueUrl)
                if (native.isSuccess) {
                    onDelivered(EmailDelivery.LINK)
                    return@launch
                }
                val nativeError = native.exceptionOrNull()
                // Record why Firebase's mailer refused (e.g. ERROR_OPERATION_NOT_ALLOWED = email-link
                // sign-in disabled in the console) — the fallbacks below hide it otherwise.
                com.example.analytics.AnalyticsTracker.authError("email_link_native", nativeErrorCode(nativeError))
                if (nativeError.isNetworkFailure()) {
                    _authErrorMessage.value = uiText(R.string.network_error_please_check_your_connection)
                    return@launch
                }

                // Layer 2: the branded link from the sendSignInEmailLink function
                // (delivered by the Trigger Email extension).
                val link = functionsClient.sendSignInEmailLink(email)
                if (link.isSuccess) {
                    onDelivered(EmailDelivery.LINK)
                    return@launch
                }

                // Layer 3: Email OTP code fallback
                val code = functionsClient.sendEmailOtp(email)
                if (code.isSuccess) {
                    authMethod = "email_code"
                    com.example.analytics.AnalyticsTracker.authFallback("magic_link", "email_code")
                    onDelivered(EmailDelivery.CODE)
                } else {
                    com.example.analytics.AnalyticsTracker.authError("email", "send_failed")
                    val linkError = link.exceptionOrNull()
                    _authErrorMessage.value = (code.exceptionOrNull() ?: linkError)
                        ?.toUserMessage(uiText(R.string.couldn_t_send_the_sign_in_email_please_try))
                        ?: uiText(R.string.couldn_t_send_the_sign_in_email_please_try)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.example.analytics.AnalyticsTracker.authError(authMethod, com.example.analytics.AnalyticsTracker.errorCode(e))
                _authErrorMessage.value = e.toUserMessage(uiText(R.string.couldn_t_send_the_sign_in_email_please_try))
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    /** Re-sends the magic link (used by the uiText(R.string.resend_link) button). */
    fun sendEmailLinkViaFunction(emailArg: String, onSent: (Boolean) -> Unit) {
        authMethod = "email_link"
        com.example.analytics.AnalyticsTracker.authStart("email_link_resend")
        val email = emailArg.ifBlank { restorePendingEmail() }
        if (email.isBlank()) {
            _authErrorMessage.value = uiText(R.string.enter_your_email_address_again_to_get_a_ne)
            onSent(false)
            return
        }
        savePendingEmailLink(email)
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                // Firebase Auth's mailer first; the function's branded link as fallback.
                val continueUrl = "https://prohost-f766f.web.app/emaillink"
                val nativeSent = FirebaseAuthService(firebaseAppContext())
                    .sendSignInLinkToEmail(email, continueUrl).isSuccess
                if (nativeSent) {
                    _isAuthenticating.value = false
                    onSent(true)
                    return@launch
                }
                val result = functionsClient.sendSignInEmailLink(email)
                _isAuthenticating.value = false
                onSent(result.isSuccess)
                if (result.isFailure) {
                    _authErrorMessage.value = result.exceptionOrNull()
                        ?.toUserMessage(uiText(R.string.failed_to_send_sign_in_link_try_use_a_code))
                        ?: uiText(R.string.failed_to_send_sign_in_link)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                com.example.analytics.AnalyticsTracker.authError(authMethod, com.example.analytics.AnalyticsTracker.errorCode(e))
                _isAuthenticating.value = false
                _authErrorMessage.value = e.toUserMessage(uiText(R.string.failed_to_send_sign_in_link_please_try_aga))
                onSent(false)
            }
        }
    }

    private fun nativeErrorCode(e: Throwable?): String =
        (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
            ?: e?.let { it::class.simpleName } ?: "unknown"

    private fun Throwable?.isNetworkFailure(): Boolean = this is com.google.firebase.FirebaseNetworkException

    private fun authPrefs() =
        firebaseAppContext().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)

    /** Remembers the address a link/code was sent to, so the tapped link can finish sign-in. Blank is ignored. */
    fun savePendingEmailLink(email: String) {
        if (email.isBlank()) return
        authPrefs().edit().putString("pending_email_link", email.trim()).apply()
    }

    /** The saved address, kept until sign-in succeeds (a failed or repeated tap must still find it). */
    fun peekPendingEmailLink(): String? =
        authPrefs().getString("pending_email_link", null)?.takeIf { it.isNotBlank() }

    private fun clearPendingEmailLink() {
        authPrefs().edit().remove("pending_email_link").apply()
    }

    /**
     * The in-memory address can be lost when Android kills the app while the user is in their
     * mail app; restore it from the saved one so "Resend" / "Use a code instead" never send "".
     */
    fun restorePendingEmail(): String {
        if (_pendingEmail.value.isBlank()) peekPendingEmailLink()?.let { _pendingEmail.value = it }
        return _pendingEmail.value
    }

    // A sign-in link that arrived without a saved address (other device, cleared data):
    // kept here until the user confirms their email.
    private val _linkAwaitingEmail = MutableStateFlow<String?>(null)
    val linkAwaitingEmail: StateFlow<String?> = _linkAwaitingEmail.asStateFlow()
    private var lastHandledLink: String? = null
    private var lastHandledOtpToken: String? = null

    /**
     * Entry point for every tapped sign-in link (any number per app session — a returning
     * user signs in again after signing out). The same link twice is ignored.
     */
    fun onEmailLinkArrived(activity: Activity, link: String, onVerified: (needsRegistration: Boolean) -> Unit) {
        if (link == lastHandledLink) return
        lastHandledLink = link
        val email = peekPendingEmailLink() ?: _pendingEmail.value.takeIf { it.isNotBlank() }
        if (email == null) {
            _linkAwaitingEmail.value = link
            return
        }
        handleEmailLink(activity, email, link, onVerified)
    }

    /** Finishes a link that arrived without a saved address, once the user typed their email. */
    fun completeLinkWithEmail(activity: Activity, email: String, onVerified: (needsRegistration: Boolean) -> Unit) {
        val link = _linkAwaitingEmail.value ?: return
        _linkAwaitingEmail.value = null
        _pendingEmail.value = email.trim()
        handleEmailLink(activity, email.trim(), link, onVerified)
    }

    fun dismissLinkAwaitingEmail() {
        _linkAwaitingEmail.value = null
        lastHandledLink = null
    }

    fun handleEmailLink(activity: Activity, email: String, link: String, onVerified: (needsRegistration: Boolean) -> Unit) {
        authMethod = "email_link"
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                _authErrorMessage.value = null
                val authService = com.example.data.auth.FirebaseAuthService(activity)
                when (val result = authService.signInWithEmailLink(email, link)) {
                    is AuthResult.Success -> {
                        clearPendingEmailLink()
                        finishVerification(activity, result.isNewUser, onVerified)
                    }
                    is AuthResult.Failure -> {
                        com.example.analytics.AnalyticsTracker.authError(authMethod, "credential_failed")
                        lastHandledLink = null // a retry of the same link may succeed
                        linkErrorFresh = true
                        _authErrorMessage.value = result.message
                        _isAuthenticating.value = false
                    }
                    else -> _isAuthenticating.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.example.analytics.AnalyticsTracker.authError(authMethod, com.example.analytics.AnalyticsTracker.errorCode(e))
                lastHandledLink = null
                linkErrorFresh = true
                _isAuthenticating.value = false
                _authErrorMessage.value = e.toUserMessage(uiText(R.string.sign_in_link_verification_failed_try_again))
            }
        }
    }

    /** Sends a 6-digit OTP to [email] via the sendEmailOtp function (Trigger Email extension). */
    fun sendEmailOtp(emailArg: String, onSent: (Boolean) -> Unit) {
        authMethod = "email_code"
        com.example.analytics.AnalyticsTracker.authStart("email_code")
        val email = emailArg.ifBlank { restorePendingEmail() }
        if (email.isBlank()) {
            _authErrorMessage.value = uiText(R.string.enter_your_email_address_again_to_get_a_ne)
            onSent(false)
            return
        }
        savePendingEmailLink(email)
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                val result = functionsClient.sendEmailOtp(email)
                _isAuthenticating.value = false
                if (result.isFailure) {
                    _authErrorMessage.value = result.exceptionOrNull()
                        ?.toUserMessage(uiText(R.string.failed_to_send_code_please_try_again))
                        ?: uiText(R.string.failed_to_send_code_please_try_again)
                }
                onSent(result.isSuccess)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.example.analytics.AnalyticsTracker.authError(authMethod, com.example.analytics.AnalyticsTracker.errorCode(e))
                _isAuthenticating.value = false
                _authErrorMessage.value = e.toUserMessage(uiText(R.string.failed_to_send_code_please_check_your_conn))
                onSent(false)
            }
        }
    }

    /** Verifies the OTP code, exchanges it for a custom token, and signs in. */
    fun verifyEmailOtpCode(
        activity: Activity,
        email: String,
        code: String,
        onVerified: (needsRegistration: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                _authErrorMessage.value = null
                authMethod = "email_code"
                val tokenResult = functionsClient.verifyEmailOtp(email, code)
                if (tokenResult.isFailure) {
                    val code = tokenResult.exceptionOrNull()?.let { com.example.analytics.AnalyticsTracker.errorCode(it) }
                    com.example.analytics.AnalyticsTracker.authError("email_code", code)
                    _authErrorMessage.value = tokenResult.exceptionOrNull()
                        ?.toUserMessage(uiText(R.string.invalid_or_expired_code_please_try_again))
                        ?: uiText(R.string.invalid_or_expired_code_please_try_again)
                    _isAuthenticating.value = false
                    return@launch
                }
                val authService = com.example.data.auth.FirebaseAuthService(activity)
                when (val result = authService.signInWithCustomToken(tokenResult.getOrThrow())) {
                    is AuthResult.Success -> finishVerification(activity, result.isNewUser, onVerified)
                    is AuthResult.Failure -> {
                        com.example.analytics.AnalyticsTracker.authError(authMethod, "credential_failed")
                        _authErrorMessage.value = result.message
                        _isAuthenticating.value = false
                    }
                    else -> _isAuthenticating.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.example.analytics.AnalyticsTracker.authError(authMethod, com.example.analytics.AnalyticsTracker.errorCode(e))
                _isAuthenticating.value = false
                _authErrorMessage.value = e.toUserMessage(uiText(R.string.code_verification_failed_please_try_again))
            }
        }
    }

    /**
     * Signs in with a custom token received via the prohost://emailotp deep link
     * (one-click OTP magic link from the email). Behaves identically to a successful
     * [verifyEmailOtpCode] call, but the token arrives from the deep link rather than
     * the user typing a code.
     */
    fun signInWithOtpToken(
        activity: Activity,
        customToken: String,
        onVerified: (needsRegistration: Boolean) -> Unit
    ) {
        if (customToken == lastHandledOtpToken) return
        lastHandledOtpToken = customToken
        authMethod = "email_code"
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                _authErrorMessage.value = null
                val authService = com.example.data.auth.FirebaseAuthService(activity)
                when (val result = authService.signInWithCustomToken(customToken)) {
                    is AuthResult.Success -> finishVerification(activity, result.isNewUser, onVerified)
                    is AuthResult.Failure -> {
                        com.example.analytics.AnalyticsTracker.authError(authMethod, "credential_failed")
                        _authErrorMessage.value = result.message
                        _isAuthenticating.value = false
                    }
                    else -> _isAuthenticating.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.example.analytics.AnalyticsTracker.authError(authMethod, com.example.analytics.AnalyticsTracker.errorCode(e))
                _authErrorMessage.value = e.toUserMessage(uiText(R.string.an_error_occurred))
                _isAuthenticating.value = false
            }
        }
    }

    fun startGoogleSignIn(activity: Activity, googleIdToken: String, onVerified: (needsRegistration: Boolean) -> Unit) {
        authMethod = "google"
        com.example.analytics.AnalyticsTracker.authStart("google")
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                _authErrorMessage.value = null
                val authService = com.example.data.auth.FirebaseAuthService(activity)
                when (val result = authService.signInWithGoogleCredential(googleIdToken)) {
                    is AuthResult.Success -> finishVerification(activity, result.isNewUser, onVerified)
                    is AuthResult.Failure -> {
                        com.example.analytics.AnalyticsTracker.authError(authMethod, "credential_failed")
                        _authErrorMessage.value = result.message
                        _isAuthenticating.value = false
                    }
                    else -> _isAuthenticating.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.example.analytics.AnalyticsTracker.authError(authMethod, com.example.analytics.AnalyticsTracker.errorCode(e))
                _isAuthenticating.value = false
                _authErrorMessage.value = e.toUserMessage(uiText(R.string.google_sign_in_failed_please_try_again))
            }
        }
    }

    /**
     * Common post-authentication logic shared by phone, email, and Google sign-in paths.
     * After any credential is verified by Firebase Auth, this decides whether the caller
     * needs to complete registration or can go straight into the app — always from the
     * stored profile ([isProfileComplete]), never from Firebase's isNewUser flag alone:
     * the email-code flow creates the Auth user server-side, so a brand-new person
     * arrives here with isNewUser = false.
     */
    private suspend fun finishVerification(
        activity: Activity,
        isNewUser: Boolean,
        onVerified: (needsRegistration: Boolean) -> Unit
    ) {
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            _isAuthenticating.value = false
            _authErrorMessage.value = uiText(R.string.verification_did_not_return_a_valid_sessio)
            return
        }
        if (isNewUser) {
            requestRegistration(onVerified)
            return
        }
        try {
            val integrityToken = com.example.util.PlayIntegrityManager(activity)
                .requestIntegrityToken(firebaseUser.uid)
            val user = com.example.data.auth.completeVerifiedLogin(repository, functionsClient, firebaseUser, integrityToken)
            if (!user.isProfileComplete()) {
                repository.discardIncompleteSession()
                requestRegistration(onVerified)
            } else {
                registerFcmTokenForCurrentUser(user.id)
                com.example.analytics.AnalyticsTracker.login(authMethod)
                _isAuthenticating.value = false
                _authSuccessMessage.value = uiText(R.string.welcome_back_x_2, user.fullName)
                onVerified(false)
            }
        } catch (e: com.example.data.auth.AccountSuspendedException) {
            com.example.analytics.AnalyticsTracker.authError(authMethod, "suspended")
            _isAuthenticating.value = false
            authService.signOut()
            _authErrorMessage.value = e.message
        } catch (e: Exception) {
            com.example.analytics.AnalyticsTracker.authError(authMethod, com.example.analytics.AnalyticsTracker.errorCode(e))
            _isAuthenticating.value = false
            _authErrorMessage.value = e.toUserMessage(uiText(R.string.sign_in_failed_please_try_again))
        }
    }

    private fun requestRegistration(onVerified: (needsRegistration: Boolean) -> Unit) {
        com.example.analytics.AnalyticsTracker.registrationStart(authMethod)
        _isAuthenticating.value = false
        _registrationRequested.value = true
        onVerified(true)
    }

    // --- Registration (Step 3 for brand-new accounts) ---

    /**
     * Step 3 (brand-new accounts only): the user is already authenticated (phone OTP,
     * email link, or Google) — this uploads files and writes the profile.
     */
    fun completePendingRegistration(
        activity: Activity,
        registration: PendingRegistration,
        onSuccess: () -> Unit
    ) {
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            _authErrorMessage.value = uiText(R.string.your_verified_session_expired_please_sign)
            return
        }
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        viewModelScope.launch {
            try {
                // Idempotency guard: if this UID already has a complete profile
                // (e.g. double-tap on submit, or process-death resume), skip the
                // registration write and just complete a normal login.
                val existingProfile = repository.getUserProfile(firebaseUser.uid)
                if (existingProfile != null && existingProfile.fullName.isNotBlank()) {
                    _isAuthenticating.value = false
                    onSuccess()
                    return@launch
                }
                val storageService = com.example.data.storage.FirebaseStorageService.getInstance()
                val profilePictureUrl = registration.profilePictureUri?.let { uri ->
                    storageService.uploadProfilePicture(firebaseUser.uid, uri)
                }
                val integrityToken = com.example.util.PlayIntegrityManager(activity)
                    .requestIntegrityToken(firebaseUser.uid)
                val user = com.example.data.auth.completeVerifiedRegistration(
                    repository = repository,
                    functionsClient = functionsClient,
                    firebaseUser = firebaseUser,
                    details = com.example.data.auth.RegistrationDetails(
                        fullName = registration.fullName,
                        email = registration.email,
                        phone = registration.phoneE164,
                        specialty = registration.specialty,
                        profilePictureUrl = profilePictureUrl,
                        country = registration.country,
                        governorate = registration.governorate,
                        city = registration.city,
                        tosAccepted = registration.tosAccepted
                    ),
                    integrityToken = integrityToken
                )
                _isAuthenticating.value = false
                registerFcmTokenForCurrentUser(user.id)
                com.example.analytics.AnalyticsTracker.signUp(authMethod, user.role)
                val missedUploads = buildList {
                    if (registration.profilePictureUri != null && profilePictureUrl == null) add(uiText(R.string.profile_photo_2))
                }
                _authSuccessMessage.value = if (missedUploads.isEmpty()) {
                    uiText(R.string.account_created_successfully_for_x, user.fullName)
                } else {
                    "Account created for ${user.fullName}! Your ${missedUploads.joinToString(" and ")} " +
                        "didn't upload — add ${if (missedUploads.size > 1) "them" else "it"} from your profile."
                }
                onSuccess()
            } catch (e: com.example.data.auth.AccountSuspendedException) {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                _isAuthenticating.value = false
                _authErrorMessage.value = e.message
            } catch (e: Exception) {
                com.example.analytics.AnalyticsTracker.authError("registration", com.example.analytics.AnalyticsTracker.errorCode(e))
                _isAuthenticating.value = false
                _authErrorMessage.value = e.toUserMessage(uiText(R.string.registration_failed_please_try_again))
            }
        }
    }

}
