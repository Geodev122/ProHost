package com.example.ui.viewmodel

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthResult
import com.example.data.repository.ProHostRepository
import com.example.util.guessFileExtension
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
/** Kept as a typealias so any remaining callers of the old name still compile. */
@Suppress("unused")
@Deprecated("Renamed to PendingRegistration", ReplaceWith("PendingRegistration"))
typealias PendingPhoneRegistration = AuthViewModel.PendingRegistration

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
        val idDocumentUri: Uri? = null,
        // The registration form's Terms of Use / Privacy Policy checkbox must have
        // actually been checked before this reaches here — enforced client-side by
        // the form's own submit gate, and again server-side by assignInitialRole.ts,
        // which rejects registration outright if this isn't literally true.
        val tosAccepted: Boolean
    )

    // --- Email lookup state (EMAIL_ENTRY step) ---

    enum class EmailLookupResult { UNKNOWN, NEW_USER, HAS_EMAIL, HAS_GOOGLE }

    private val _emailLookupResult = MutableStateFlow(EmailLookupResult.UNKNOWN)
    val emailLookupResult: StateFlow<EmailLookupResult> = _emailLookupResult.asStateFlow()

    private val _pendingEmail = MutableStateFlow("")
    val pendingEmail: StateFlow<String> = _pendingEmail.asStateFlow()

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

    fun lookupEmail(email: String) {
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                val authService = com.example.data.auth.FirebaseAuthService(firebaseAppContext())
                val methods = authService.fetchSignInMethodsForEmail(email)
                _pendingEmail.value = email
                _emailLookupResult.value = when {
                    methods.isEmpty() -> EmailLookupResult.NEW_USER
                    "google.com" in methods -> EmailLookupResult.HAS_GOOGLE
                    else -> EmailLookupResult.HAS_EMAIL
                }
                _isAuthenticating.value = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _authErrorMessage.value = e.localizedMessage ?: "An error occurred"
            }
        }
    }

    fun sendEmailSignInLink(email: String, continueUrl: String, onSent: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                _isAuthenticating.value = true
                val authService = com.example.data.auth.FirebaseAuthService(firebaseAppContext())
                val sent = authService.sendSignInLinkToEmail(email, continueUrl)
                _isAuthenticating.value = false
                onSent(sent)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _authErrorMessage.value = e.localizedMessage ?: "An error occurred"
            }
        }
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
                _authErrorMessage.value = e.localizedMessage ?: "An error occurred"
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
                    _authErrorMessage.value = "Failed to send code. Please try again."
                }
                onSent(result.isSuccess)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _authErrorMessage.value = e.localizedMessage ?: "An error occurred"
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
                    _authErrorMessage.value = "Invalid or expired code. Please try again."
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
                _authErrorMessage.value = e.localizedMessage ?: "An error occurred"
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
                _authErrorMessage.value = e.localizedMessage ?: "An error occurred"
            }
        }
    }

    /**
     * Common post-authentication logic shared by phone, email, and Google sign-in paths.
     * After any credential is verified by Firebase Auth, this decides whether the caller
     * needs to complete registration (brand-new account) or can go straight into the app.
     *
     * Stranded-account recovery (a phone-auth user who got interrupted between OTP and
     * the profile form) is scoped to phone-auth only — email/Google users always have
     * phone blank by design until they complete the KYC step.
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
            _authErrorMessage.value = "Phone verification did not return a valid session. Please try again."
            return
        }
        _isAuthenticating.value = false
        if (!isNewUser) {
            try {
                val integrityToken = com.example.util.PlayIntegrityManager(activity)
                    .requestIntegrityToken().getOrNull()
                val user = com.example.data.auth.completeVerifiedLogin(repository, functionsClient, firebaseUser, integrityToken)
                // Stranded-account recovery: only applies when the user's Firebase Auth
                // account was authenticated via phone (phone-auth users with a blank phone
                // in Firestore means the registration form was never submitted). Email/Google
                // users have phone blank by design until KYC; they must not be re-routed
                // to the registration form on every subsequent sign-in.
                val isPhoneAuth = firebaseUser.providerData.any { it.providerId == "phone" }
                if (isPhoneAuth && user.role != com.example.data.model.UserRole.ADMIN && user.phone.isBlank()) {
                    repository.discardIncompleteSession()
                    onVerified(true)
                } else {
                    registerFcmTokenForCurrentUser(user.id)
                    _authSuccessMessage.value = "Welcome back, ${user.fullName}!"
                    onVerified(false)
                }
            } catch (e: com.example.data.auth.AccountSuspendedException) {
                authService.signOut()
                _authErrorMessage.value = e.message
            }
        } else {
            // Brand-new account — the caller needs to complete their profile.
            onVerified(true)
        }
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
                        _authErrorMessage.value = e.localizedMessage ?: "An error occurred"
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
                _authErrorMessage.value = e.localizedMessage ?: "An error occurred"
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
                val storageService = com.example.data.storage.FirebaseStorageService.getInstance()
                val profilePictureUrl = registration.profilePictureUri?.let { uri ->
                    storageService.uploadProfilePicture(firebaseUser.uid, uri, guessFileExtension(activity, uri, "jpg"))
                }
                val idDocumentUrl = registration.idDocumentUri?.let { uri ->
                    storageService.uploadIdDocument(firebaseUser.uid, uri, guessFileExtension(activity, uri, "pdf"))
                }
                val integrityToken = com.example.util.PlayIntegrityManager(activity)
                    .requestIntegrityToken().getOrNull()
                val user = com.example.data.auth.completeVerifiedRegistration(
                    repository = repository,
                    functionsClient = functionsClient,
                    firebaseUser = firebaseUser,
                    fullName = registration.fullName,
                    email = registration.email,
                    phone = registration.phoneE164,
                    specialty = registration.specialty,
                    profilePictureUrl = profilePictureUrl,
                    idDocumentUrl = idDocumentUrl,
                    country = registration.country,
                    governorate = registration.governorate,
                    city = registration.city,
                    tosAccepted = registration.tosAccepted,
                    integrityToken = integrityToken
                )
                _isAuthenticating.value = false
                registerFcmTokenForCurrentUser(user.id)
                val missedUploads = buildList {
                    if (registration.profilePictureUri != null && profilePictureUrl == null) add("profile photo")
                    if (registration.idDocumentUri != null && idDocumentUrl == null) add("ID document")
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
                _authErrorMessage.value = friendlyRegistrationErrorMessage(e)
            }
        }
    }

    /**
     * A Cloud Function's own rejection message (e.g. assignInitialRole's format
     * validation) is already written for end users and safe to show as-is. Raw
     * SDK/network errors are never shown verbatim.
     */
    private fun friendlyRegistrationErrorMessage(e: Exception): String {
        val msg = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message
            ?: e.message ?: ""
        // Pass through server-supplied messages — they're already user-friendly
        // (assignInitialRole.ts's HttpsError messages, account suspension, etc.)
        if (e is com.google.firebase.functions.FirebaseFunctionsException && msg.isNotBlank()) {
            return msg
        }
        return when {
            msg.contains("network", ignoreCase = true) -> "Network error. Please check your connection."
            msg.contains("too-many-requests", ignoreCase = true) -> "Too many attempts. Please try again later."
            else -> "Registration failed. Please try again."
        }
    }
}
