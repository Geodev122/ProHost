package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.analytics.AnalyticsTracker
import com.example.data.auth.AccountSuspendedException
import com.example.data.auth.AuthDefaults
import com.example.data.auth.AuthError
import com.example.data.auth.messageKey
import com.example.data.repository.RepositoryDefaults
import com.example.util.PushTokens
import com.example.data.auth.AuthGateway
import com.example.data.auth.AuthOutcome
import com.example.data.auth.RegistrationDetails
import com.example.data.auth.SessionFinisher
import com.example.data.auth.isCallableUnavailable
import com.example.data.auth.toUserMessage
import com.example.data.model.isProfileComplete
import com.example.data.repository.ProHostRepository
import com.example.util.PasswordRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.ui.util.sharedText

/**
 * Sign-in and registration for LoginAuthScreen: email + password (sign in, create account,
 * Forgot password) and Google. The 6-digit email code and the magic link were retired in 1.0.44.
 *
 * Role is NEVER taken from the client here: it comes from the ID token's custom claim, set
 * server-side by assignInitialRole (see [SessionFinisher] → completeVerifiedLogin /
 * completeVerifiedRegistration). New vs. returning is decided from the stored profile
 * ([isProfileComplete]), never from Firebase's isNewUser alone.
 */
class AuthViewModel(
    private val repository: ProHostRepository = ProHostRepository.getInstance(),
    authGateway: AuthGateway? = null,
    private val functionsClient: com.example.data.gateway.FunctionsGateway = RepositoryDefaults.functions(),
    sessionFinisher: SessionFinisher? = null
) : ViewModel() {

    private val auth: AuthGateway by lazy { authGateway ?: AuthDefaults.gateway() }
    private val session: SessionFinisher by lazy {
        sessionFinisher ?: AuthDefaults.sessionFinisher(repository, functionsClient)
    }

    // Which sign-in path is in flight, for analytics login/sign_up "method": "password" or "google".
    private var authMethod = METHOD_PASSWORD

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    private val _authSuccessMessage = MutableStateFlow<String?>(null)
    val authSuccessMessage: StateFlow<String?> = _authSuccessMessage.asStateFlow()

    /** The [AuthError] behind the current error message (the screen offers "Forgot password?" for wrong credentials). */
    private val _lastAuthError = MutableStateFlow<AuthError?>(null)
    val lastAuthError: StateFlow<AuthError?> = _lastAuthError.asStateFlow()

    fun clearAuthMessages() {
        _authErrorMessage.value = null
        _authSuccessMessage.value = null
        _lastAuthError.value = null
    }

    /** On entering the login screen: clears leftovers from an earlier visit. */
    fun clearStaleAuthMessages() = clearAuthMessages()

    /**
     * Everything the registration form collects, submitted only AFTER the user is already
     * authenticated (email + password or Google). Registration never collects a government ID.
     */
    data class PendingRegistration(
        val fullName: String,
        val email: String,
        val phoneE164: String = "",
        val specialty: String,
        val country: String,
        val governorate: String,
        val city: String,
        val profilePicture: com.example.util.PickedFile?,
        // Enforced client-side by the form's submit gate and again server-side by
        // assignInitialRole.ts, which rejects registration unless this is literally true.
        val tosAccepted: Boolean
    )

    @Suppress("DEPRECATION")
    private fun registerFcmTokenForCurrentUser(uid: String) {
        viewModelScope.launch {
            runCatching {
                val token = PushTokens.current() ?: return@runCatching
                repository.registerFcmToken(uid, token)
            }
        }
    }

    private fun fail(error: AuthError, analyticsCode: String = error.name.lowercase()) {
        AnalyticsTracker.authError(authMethod, analyticsCode)
        _isAuthenticating.value = false
        _lastAuthError.value = error
        _authErrorMessage.value = sharedText(error.messageKey)
    }

    /** Runs an auth step; anything thrown (the gateways normally return outcomes) still ends in a message. */
    private fun launchAuth(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(AuthDefaults.classify(e), AnalyticsTracker.errorCode(e))
        }
    }

    private fun begin(method: String) {
        authMethod = method
        AnalyticsTracker.authStart(method)
        _isAuthenticating.value = true
        clearAuthMessages()
    }

    // --- Email + password ---

    /** Signs in an existing account. Wrong email or password both read the same (enumeration protection). */
    fun signIn(email: String, password: String, onVerified: (needsRegistration: Boolean) -> Unit) {
        val address = email.trim().lowercase()
        if (!isPlausibleEmail(address)) return fail(AuthError.INVALID_EMAIL)
        if (password.isEmpty()) {
            _authErrorMessage.value = sharedText("enter_your_password")
            return
        }
        begin(METHOD_PASSWORD)
        launchAuth {
            when (val r = auth.signInWithPassword(address, password)) {
                is AuthOutcome.Success -> finishVerification(isNewUser = false, onVerified)
                is AuthOutcome.Failure -> fail(r.error, r.code)
            }
        }
    }

    /** Creates an email + password account, then sends the person to the registration form. */
    fun createAccount(email: String, password: String, onVerified: (needsRegistration: Boolean) -> Unit) {
        val address = email.trim().lowercase()
        if (!isPlausibleEmail(address)) return fail(AuthError.INVALID_EMAIL)
        if (!PasswordRules.isValid(password)) return fail(AuthError.WEAK_PASSWORD)
        begin(METHOD_PASSWORD)
        launchAuth {
            when (val r = auth.createAccount(address, password)) {
                is AuthOutcome.Success -> finishVerification(isNewUser = true, onVerified)
                is AuthOutcome.Failure -> fail(r.error, r.code)
            }
        }
    }

    /**
     * "Forgot password": our branded email (sendPasswordResetLink callable), falling back to
     * Firebase's own mailer when the callable is unreachable. The answer never says whether an
     * account exists — the screen always shows the same "if an account exists" message.
     */
    fun requestPasswordReset(email: String, onSent: () -> Unit) {
        val address = email.trim().lowercase()
        if (!isPlausibleEmail(address)) return fail(AuthError.INVALID_EMAIL)
        authMethod = METHOD_PASSWORD
        AnalyticsTracker.authStart("password_reset")
        _isAuthenticating.value = true
        clearAuthMessages()
        launchAuth {
            val branded = functionsClient.sendPasswordResetLink(address)
            if (branded.isSuccess) return@launchAuth sent(onSent)
            val e = branded.exceptionOrNull()
            if (e != null && !e.isCallableUnavailable()) {
                AnalyticsTracker.authError("password_reset", AnalyticsTracker.errorCode(e))
                _isAuthenticating.value = false
                _authErrorMessage.value = e.toUserMessage(sharedText("couldnt_send_reset_email"))
                return@launchAuth
            }
            when (val r = auth.sendPasswordResetEmail(address)) {
                is AuthOutcome.Success -> sent(onSent)
                // No such account looks exactly like success.
                is AuthOutcome.Failure -> if (r.error == AuthError.INVALID_CREDENTIALS) sent(onSent) else fail(r.error, r.code)
            }
        }
    }

    private fun sent(onSent: () -> Unit) {
        _isAuthenticating.value = false
        AnalyticsTracker.authFallback("password_reset", "sent")
        onSent()
    }

    // --- Google ---

    fun startGoogleSignIn(googleIdToken: String, onVerified: (needsRegistration: Boolean) -> Unit) {
        begin(METHOD_GOOGLE)
        launchAuth {
            when (val r = auth.signInWithGoogle(googleIdToken)) {
                is AuthOutcome.Success -> finishVerification(r.isNewUser, onVerified)
                is AuthOutcome.Failure -> fail(r.error, r.code)
            }
        }
    }

    /**
     * After Firebase accepted the credential: a brand-new account goes to the registration
     * form; otherwise the server resolves the role and the stored profile decides (an account
     * without a completed profile still registers first).
     */
    private suspend fun finishVerification(isNewUser: Boolean, onVerified: (needsRegistration: Boolean) -> Unit) {
        if (auth.signedInUid == null) {
            _isAuthenticating.value = false
            _authErrorMessage.value = sharedText("verification_did_not_return_a_valid_sessio")
            return
        }
        if (isNewUser) return requestRegistration(onVerified)
        try {
            val user = session.completeLogin()
            if (!user.isProfileComplete()) {
                session.discardIncompleteSession()
                requestRegistration(onVerified)
            } else {
                registerFcmTokenForCurrentUser(user.id)
                AnalyticsTracker.login(authMethod)
                _isAuthenticating.value = false
                _authSuccessMessage.value = sharedText("welcome_back_x_2", user.fullName)
                onVerified(false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountSuspendedException) {
            AnalyticsTracker.authError(authMethod, "suspended")
            _isAuthenticating.value = false
            auth.signOut()
            _authErrorMessage.value = e.message
        } catch (e: Exception) {
            AnalyticsTracker.authError(authMethod, AnalyticsTracker.errorCode(e))
            _isAuthenticating.value = false
            _authErrorMessage.value = e.toUserMessage(sharedText("sign_in_failed_please_try_again"))
        }
    }

    private fun requestRegistration(onVerified: (needsRegistration: Boolean) -> Unit) {
        AnalyticsTracker.registrationStart(authMethod)
        _isAuthenticating.value = false
        onVerified(true)
    }

    // --- Registration (brand-new accounts) ---

    fun completePendingRegistration(registration: PendingRegistration, onSuccess: () -> Unit) {
        if (auth.signedInUid == null) {
            _authErrorMessage.value = sharedText("your_verified_session_expired_please_sign")
            return
        }
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        viewModelScope.launch {
            try {
                // Idempotency guard: a double-tap or a process-death resume finds the profile already written.
                if (session.existingProfileComplete()) {
                    _isAuthenticating.value = false
                    onSuccess()
                    return@launch
                }
                val profilePictureUrl = registration.profilePicture?.let { session.uploadProfilePicture(it) }
                val user = session.completeRegistration(
                    RegistrationDetails(
                        fullName = registration.fullName,
                        email = registration.email,
                        phone = registration.phoneE164,
                        specialty = registration.specialty,
                        profilePictureUrl = profilePictureUrl,
                        country = registration.country,
                        governorate = registration.governorate,
                        city = registration.city,
                        tosAccepted = registration.tosAccepted
                    )
                )
                _isAuthenticating.value = false
                registerFcmTokenForCurrentUser(user.id)
                AnalyticsTracker.signUp(authMethod, user.role)
                val photoMissing = registration.profilePicture != null && profilePictureUrl == null
                _authSuccessMessage.value = if (!photoMissing) {
                    sharedText("account_created_successfully_for_x", user.fullName)
                } else {
                    sharedText("account_created_photo_missing_x", user.fullName)
                }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountSuspendedException) {
                auth.signOut()
                _isAuthenticating.value = false
                _authErrorMessage.value = e.message
            } catch (e: Exception) {
                AnalyticsTracker.authError("registration", AnalyticsTracker.errorCode(e))
                _isAuthenticating.value = false
                _authErrorMessage.value = e.toUserMessage(sharedText("registration_failed_please_try_again"))
            }
        }
    }

    /** "Start over with a different account" from the registration form. */
    fun signOut() = auth.signOut()

    companion object {
        const val METHOD_PASSWORD: String = "password"
        const val METHOD_GOOGLE = "google"
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
        fun isPlausibleEmail(email: String): Boolean = email.length <= 254 && EMAIL.matches(email)
    }
}
