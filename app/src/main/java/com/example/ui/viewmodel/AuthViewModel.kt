package com.example.ui.viewmodel

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

    /**
     * Everything the registration form collects, submitted only AFTER the user is
     * already authenticated (phone OTP, email link, or Google). [phoneE164] defaults
     * to "" for email/Google-auth users — phone verification moves to the KYC gate.
     * Registration never collects a government ID document — that's the KYC flow's
     * job (KycScreen.kt / KycVerificationDialog.kt), required before a Specialist can
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

    private val _pendingVerificationId = MutableStateFlow<String?>(null)

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
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authErrorMessage.value = null
            _pendingEmail.value = email
            savePendingEmailLink(email)
            try {
                // Layer 1: Try Cloud Function (Hostinger SMTP with custom template)
                val link = functionsClient.sendSignInEmailLink(email)
                if (link.isSuccess) {
                    onDelivered(EmailDelivery.LINK)
                    return@launch
                }

                // Layer 2: Native Firebase Auth sendSignInLinkToEmail (Google official mailer — 100% deliverability)
                val continueUrl = "https://prohost-f766f.web.app/emaillink"
                val nativeSent = FirebaseAuthService(firebaseAppContext())
                    .sendSignInLinkToEmail(email, continueUrl)
                if (nativeSent) {
                    onDelivered(EmailDelivery.LINK)
                    return@launch
                }

                // Layer 3: Email OTP code fallback
                val code = functionsClient.sendEmailOtp(email)
                if (code.isSuccess) {
                    onDelivered(EmailDelivery.CODE)
                } else {
                    val linkError = link.exceptionOrNull()
                    _authErrorMessage.value = (code.exceptionOrNull() ?: linkError)
                        ?.toUserMessage("Couldn't send the sign-in email. Please try again.")
                        ?: "Couldn't send the sign-in email. Please try again."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _authErrorMessage.value = e.toUserMessage("Couldn't send the sign-in email. Please try again.")
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    /** Re-sends the magic link (used by the "Resend link" button). */
    fun sendEmailLinkViaFunction(email: String, onSent: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                val result = functionsClient.sendSignInEmailLink(email)
                if (result.isSuccess) {
                    _isAuthenticating.value = false
                    onSent(true)
                    return@launch
                }
                // Fallback to native Firebase Auth sendSignInLinkToEmail
                val continueUrl = "https://prohost-f766f.web.app/emaillink"
                val nativeSent = FirebaseAuthService(firebaseAppContext())
                    .sendSignInLinkToEmail(email, continueUrl)
                _isAuthenticating.value = false
                onSent(nativeSent)
                if (!nativeSent) {
                    _authErrorMessage.value = result.exceptionOrNull()
                        ?.toUserMessage("Failed to send sign-in link. Try \"Use a code instead\".")
                        ?: "Failed to send sign-in link."
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                _isAuthenticating.value = false
                _authErrorMessage.value = e.localizedMessage ?: "An error occurred"
                onSent(false)
            }
        }
    }

    fun savePendingEmailLink(email: String) {
        firebaseAppContext().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("pending_email_link", email).apply()
    }

    fun consumePendingEmailLink(): String? {
        val prefs = firebaseAppContext().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
        val email = prefs.getString("pending_email_link", null)
        prefs.edit().remove("pending_email_link").apply()
        return email
    }

    fun handleEmailLink(activity: Activity, email: String, link: String, onVerified: (needsRegistration: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                _authErrorMessage.value = null
                val authService = com.example.data.auth.FirebaseAuthService(activity)
                when (val result = authService.signInWithEmailLink(email, link)) {
                    is AuthResult.Success -> finishVerification(activity, result.isNewUser, onVerified)
                    is AuthResult.Failure -> {
                        _authErrorMessage.value = result.message
                        _isAuthenticating.value = false
                    }
                    else -> _isAuthenticating.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _isAuthenticating.value = false
                _authErrorMessage.value = e.localizedMessage ?: "Sign-in link verification failed. Try again."
            }
        }
    }

    /** Sends a 6-digit OTP to [email] via Cloud Function → Hostinger SMTP. */
    fun sendEmailOtp(email: String, onSent: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                val result = functionsClient.sendEmailOtp(email)
                _isAuthenticating.value = false
                if (result.isFailure) {
                    _authErrorMessage.value = result.exceptionOrNull()
                        ?.toUserMessage("Failed to send code. Please try again.")
                        ?: "Failed to send code. Please try again."
                }
                onSent(result.isSuccess)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _isAuthenticating.value = false
                _authErrorMessage.value = e.localizedMessage ?: "Failed to send code. Please check your connection."
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
                val tokenResult = functionsClient.verifyEmailOtp(email, code)
                if (tokenResult.isFailure) {
                    _authErrorMessage.value = tokenResult.exceptionOrNull()
                        ?.toUserMessage("Invalid or expired code. Please try again.")
                        ?: "Invalid or expired code. Please try again."
                    _isAuthenticating.value = false
                    return@launch
                }
                val authService = com.example.data.auth.FirebaseAuthService(activity)
                when (val result = authService.signInWithCustomToken(tokenResult.getOrThrow())) {
                    is AuthResult.Success -> finishVerification(activity, result.isNewUser, onVerified)
                    is AuthResult.Failure -> {
                        _authErrorMessage.value = result.message
                        _isAuthenticating.value = false
                    }
                    else -> _isAuthenticating.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _isAuthenticating.value = false
                _authErrorMessage.value = e.localizedMessage ?: "Code verification failed. Please try again."
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
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                _authErrorMessage.value = null
                val authService = com.example.data.auth.FirebaseAuthService(activity)
                when (val result = authService.signInWithCustomToken(customToken)) {
                    is AuthResult.Success -> finishVerification(activity, result.isNewUser, onVerified)
                    is AuthResult.Failure -> {
                        _authErrorMessage.value = result.message
                        _isAuthenticating.value = false
                    }
                    else -> _isAuthenticating.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _authErrorMessage.value = e.localizedMessage ?: "An error occurred"
                _isAuthenticating.value = false
            }
        }
    }

    fun startGoogleSignIn(activity: Activity, googleIdToken: String, onVerified: (needsRegistration: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                _authErrorMessage.value = null
                val authService = com.example.data.auth.FirebaseAuthService(activity)
                when (val result = authService.signInWithGoogleCredential(googleIdToken)) {
                    is AuthResult.Success -> finishVerification(activity, result.isNewUser, onVerified)
                    is AuthResult.Failure -> {
                        _authErrorMessage.value = result.message
                        _isAuthenticating.value = false
                    }
                    else -> _isAuthenticating.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _isAuthenticating.value = false
                _authErrorMessage.value = e.localizedMessage ?: "Google sign-in failed. Please try again."
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
            _authErrorMessage.value = "Verification did not return a valid session. Please try again."
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
                _isAuthenticating.value = false
                _authSuccessMessage.value = "Welcome back, ${user.fullName}!"
                onVerified(false)
            }
        } catch (e: com.example.data.auth.AccountSuspendedException) {
            _isAuthenticating.value = false
            authService.signOut()
            _authErrorMessage.value = e.message
        } catch (e: Exception) {
            _isAuthenticating.value = false
            _authErrorMessage.value = e.toUserMessage("Sign-in failed. Please try again.")
        }
    }

    private fun requestRegistration(onVerified: (needsRegistration: Boolean) -> Unit) {
        _isAuthenticating.value = false
        _registrationRequested.value = true
        onVerified(true)
    }

    // --- Phone OTP (legacy parallel path for existing phone-only users) ---

    /**
     * Step 1 of phone-OTP sign-in: send an SMS OTP to [e164Phone].
     */
    fun startPhoneVerification(
        activity: Activity,
        e164Phone: String,
        onCodeSent: () -> Unit,
        onVerified: (needsRegistration: Boolean) -> Unit
    ) {
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        authService.sendPhoneVerificationCode(
            activity = activity,
            e164PhoneNumber = e164Phone,
            onCodeSent = { verificationId ->
                _pendingVerificationId.value = verificationId
                _isAuthenticating.value = false
                onCodeSent()
            },
            onAutoVerified = { credential ->
                viewModelScope.launch {
                    try {
                        finishPhoneVerification(activity, credential, onVerified)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _authErrorMessage.value = e.localizedMessage ?: "An error occurred"
                    }
                }
            },
            onError = { message ->
                _isAuthenticating.value = false
                _authErrorMessage.value = message
            }
        )
    }

    /** Step 2: verifies the SMS code the user typed in. */
    fun submitPhoneVerificationCode(activity: Activity, smsCode: String, onVerified: (needsRegistration: Boolean) -> Unit) {
        val verificationId = _pendingVerificationId.value
        if (verificationId == null) {
            _authErrorMessage.value = "Please request a verification code first."
            return
        }
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        val credential = authService.buildPhoneAuthCredential(verificationId, smsCode)
        viewModelScope.launch {
            try {
                finishPhoneVerification(activity, credential, onVerified, verificationId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _authErrorMessage.value = e.localizedMessage ?: "An error occurred"
            }
        }
    }

    private suspend fun finishPhoneVerification(
        activity: Activity,
        credential: com.google.firebase.auth.PhoneAuthCredential,
        onVerified: (needsRegistration: Boolean) -> Unit,
        verificationId: String? = null
    ) {
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        val result = authService.signInWithPhoneCredential(credential, verificationId)
        when (result) {
            is com.example.data.auth.AuthResult.Success -> {
                _pendingVerificationId.value = null
                finishVerification(activity, result.isNewUser, onVerified)
            }
            is com.example.data.auth.AuthResult.Error -> {
                _isAuthenticating.value = false
                _authErrorMessage.value = result.message
            }
            com.example.data.auth.AuthResult.Cancelled -> {
                _isAuthenticating.value = false
            }
            else -> _isAuthenticating.value = false
        }
    }

    // --- KYC phone linking (for email/Google users doing phone verification post-auth) ---

    /**
     * Sends an SMS OTP for phone KYC — same underlying call as [startPhoneVerification]
     * but semantically separate (the user is already authenticated; this links a phone
     * credential to the existing Firebase Auth account).
     */
    fun startKycPhoneVerification(
        activity: Activity,
        e164Phone: String,
        onCodeSent: () -> Unit,
        onError: (String) -> Unit
    ) {
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        authService.sendPhoneVerificationCode(
            activity = activity,
            e164PhoneNumber = e164Phone,
            onCodeSent = { verificationId ->
                _pendingVerificationId.value = verificationId
                _isAuthenticating.value = false
                onCodeSent()
            },
            onAutoVerified = { credential ->
                // Auto-verification during KYC: link directly
                viewModelScope.launch {
                    try {
                        val linkResult = authService.linkPhoneCredentialToCurrentUser(credential)
                        _isAuthenticating.value = false
                        when (linkResult) {
                            is AuthResult.Success -> onCodeSent() // treat as success
                            is AuthResult.Failure -> onError(linkResult.message)
                            else -> {}
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _isAuthenticating.value = false
                        _authErrorMessage.value = e.localizedMessage ?: "Phone linking failed. Please try again."
                    }
                }
            },
            onError = { message ->
                _isAuthenticating.value = false
                onError(message)
            }
        )
    }

    /**
     * Submits the KYC OTP and links the phone number to the currently signed-in
     * Firebase Auth account (does NOT sign in — that already happened via email/Google).
     */
    fun linkKycPhone(
        activity: Activity,
        e164Phone: String,
        smsCode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val verificationId = _pendingVerificationId.value
                if (verificationId == null) {
                    onError("Please request a verification code first.")
                    return@launch
                }
                _isAuthenticating.value = true
                val authService = com.example.data.auth.FirebaseAuthService(activity)
                val credential = authService.buildPhoneAuthCredential(verificationId, smsCode)
                when (val result = authService.linkPhoneCredentialToCurrentUser(credential)) {
                    is AuthResult.Success -> {
                        _pendingVerificationId.value = null
                        // linkPhoneCredentialToCurrentUser only links the credential at
                        // the Firebase Auth level — persist it to Firestore too, or the
                        // KYC gate and the "needs registration" check both keep treating
                        // this account as if phone was never verified.
                        repository.updatePhoneAfterKycLink(e164Phone)
                        _isAuthenticating.value = false
                        onSuccess()
                    }
                    is AuthResult.Failure -> {
                        _isAuthenticating.value = false
                        onError(result.message)
                    }
                    else -> _isAuthenticating.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _isAuthenticating.value = false
                onError(e.localizedMessage ?: "Phone verification failed. Please try again.")
            }
        }
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
            _authErrorMessage.value = "Your verified session expired — please sign in again."
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
                val missedUploads = buildList {
                    if (registration.profilePictureUri != null && profilePictureUrl == null) add("profile photo")
                }
                _authSuccessMessage.value = if (missedUploads.isEmpty()) {
                    "Account created successfully for ${user.fullName}!"
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
                _isAuthenticating.value = false
                _authErrorMessage.value = e.toUserMessage("Registration failed. Please try again.")
            }
        }
    }

}
