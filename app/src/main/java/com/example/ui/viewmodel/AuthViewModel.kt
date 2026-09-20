package com.example.ui.viewmodel

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ProHostRepository
import com.example.util.guessFileExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for LoginAuthScreen.
 *
 * Auth strategy:
 *  - Signup: Phone → OTP (Firebase Phone Auth) → REGISTRATION_FORM → set 6-digit PIN
 *  - Login: Phone → 6-digit PIN (verified server-side, Firebase custom token returned)
 *  - Forgot PIN: Phone → OTP → set new PIN (skips registration form)
 *
 * Role is NEVER taken from the client. Sign-in resolves the caller's role from
 * their Firebase Auth ID token's custom claim (assigned server-side by
 * assignInitialRole/grantAdminRole Cloud Functions).
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

    /** Purpose of a pending OTP verification, used to decide what step comes after. */
    enum class OtpPurpose { SIGNUP, PIN_RESET }

    private var pendingVerificationId: String? = null

    /** The purpose of the current OTP — drives routing after OTP success. */
    var pendingOtpPurpose: OtpPurpose = OtpPurpose.SIGNUP
        private set

    fun clearAuthMessages() {
        _authErrorMessage.value = null
        _authSuccessMessage.value = null
    }

    /**
     * Registration form data — collected only AFTER phone OTP is verified for
     * a brand-new number. Passed to [completePendingRegistration].
     */
    data class PendingPhoneRegistration(
        val fullName: String,
        val email: String,
        val phoneE164: String,
        val specialty: String,
        val country: String,
        val governorate: String,
        val city: String,
        val profilePictureUri: Uri?,
        val idDocumentUri: Uri?,
        val tosAccepted: Boolean
    )

    @Suppress("DEPRECATION")
    private fun registerFcmTokenForCurrentUser(uid: String) {
        viewModelScope.launch {
            runCatching {
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                repository.registerFcmToken(uid, token)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Step 0: Phone lookup — decides which path to show
    // -------------------------------------------------------------------------

    /**
     * Checks whether [e164Phone] has a registered account and a PIN set.
     * Routes the UI to:
     *  - [onHasPinSet]: existing user with PIN → show PIN entry
     *  - [onNeedsPinSetup]: existing user without PIN (migrated/incomplete) → send OTP then set PIN
     *  - [onNewUser]: no account → send OTP then registration form + set PIN
     */
    fun checkPhoneRegistered(
        e164Phone: String,
        onHasPinSet: () -> Unit,
        onNeedsPinSetup: () -> Unit,
        onNewUser: () -> Unit
    ) {
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        viewModelScope.launch {
            val result = functionsClient.checkPhoneRegistered(e164Phone)
            _isAuthenticating.value = false
            result.fold(
                onSuccess = { check ->
                    when {
                        !check.isRegistered -> onNewUser()
                        check.hasPinSet -> onHasPinSet()
                        else -> onNeedsPinSetup()
                    }
                },
                onFailure = { e ->
                    val debugMsg = if (com.example.BuildConfig.DEBUG) " (${e.message})" else ""
                    _authErrorMessage.value = "Proceeding with SMS OTP verification...$debugMsg"
                    // Resilient fallback: proceed to SMS verification so the user is never blocked
                    onNewUser()
                }
            )
        }
    }

    // -------------------------------------------------------------------------
    // PIN login path (returning users)
    // -------------------------------------------------------------------------

    /**
     * Verifies [pin] for [e164Phone] server-side and signs in with the returned
     * Firebase custom token. On success calls [onVerified].
     */
    fun verifyPinAndLogin(
        activity: Activity,
        e164Phone: String,
        pin: String,
        onVerified: () -> Unit
    ) {
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        viewModelScope.launch {
            val tokenResult = functionsClient.verifyPinAndIssueToken(e164Phone, pin)
            if (tokenResult.isFailure) {
                _isAuthenticating.value = false
                val msg = tokenResult.exceptionOrNull()?.message
                _authErrorMessage.value = when {
                    msg?.contains("failed-precondition", ignoreCase = true) == true ->
                        "No PIN set — please use SMS verification first."
                    msg?.contains("permission-denied", ignoreCase = true) == true ->
                        "This account has been suspended. Please contact support."
                    else -> "Incorrect PIN. Please try again."
                }
                return@launch
            }

            val customToken = tokenResult.getOrThrow()
            val authService = com.example.data.auth.FirebaseAuthService(activity)
            val signInResult = authService.signInWithCustomToken(customToken)
            when (signInResult) {
                is com.example.data.auth.AuthResult.Success -> {
                    val firebaseUser = signInResult.firebaseUser
                    if (firebaseUser == null) {
                        _isAuthenticating.value = false
                        _authErrorMessage.value = "Sign-in session could not be established. Please try again."
                        return@launch
                    }
                    try {
                        val integrityToken = com.example.util.PlayIntegrityManager(activity)
                            .requestIntegrityToken().getOrNull()
                        val user = com.example.data.auth.completeVerifiedLogin(
                            repository, functionsClient, firebaseUser, integrityToken
                        )
                        _isAuthenticating.value = false
                        registerFcmTokenForCurrentUser(user.id)
                        _authSuccessMessage.value = "Welcome back, ${user.fullName}!"
                        onVerified()
                    } catch (e: com.example.data.auth.AccountSuspendedException) {
                        authService.signOut()
                        _isAuthenticating.value = false
                        _authErrorMessage.value = e.message
                    } catch (e: Exception) {
                        _isAuthenticating.value = false
                        _authErrorMessage.value = "Sign-in failed. Please try again."
                    }
                }
                is com.example.data.auth.AuthResult.Error -> {
                    _isAuthenticating.value = false
                    _authErrorMessage.value = signInResult.message
                }
                com.example.data.auth.AuthResult.Cancelled -> {
                    _isAuthenticating.value = false
                }
            }
        }
    }

    /**
     * Verifies PIN for a re-auth lock screen (H1). Uses the current Firebase user's
     * phone number — no full sign-in cycle, just a server-side PIN check.
     */
    fun verifyPinForReauth(pin: String, onResult: (success: Boolean, message: String?) -> Unit) {
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val phone = firebaseUser?.phoneNumber
        if (phone.isNullOrBlank()) {
            onResult(false, "No phone on file — please sign in again.")
            return
        }
        viewModelScope.launch {
            val tokenResult = functionsClient.verifyPinAndIssueToken(phone, pin)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (tokenResult.isSuccess) {
                    onResult(true, null)
                } else {
                    val msg = tokenResult.exceptionOrNull()?.message
                    onResult(false, when {
                        msg?.contains("resource-exhausted", ignoreCase = true) == true ->
                            "Too many attempts — please wait before trying again."
                        else -> "Incorrect PIN."
                    })
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // OTP path (signup + forgot-PIN reset)
    // -------------------------------------------------------------------------

    /**
     * Sends an OTP to [e164Phone]. [purpose] governs what happens after the code
     * is verified: SIGNUP routes to registration form then PIN setup; PIN_RESET
     * routes directly to PIN setup (no registration form — account already exists).
     */
    fun startPhoneVerification(
        activity: Activity,
        e164Phone: String,
        purpose: OtpPurpose = OtpPurpose.SIGNUP,
        onCodeSent: () -> Unit,
        onVerified: (needsRegistration: Boolean) -> Unit
    ) {
        pendingOtpPurpose = purpose
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        authService.sendPhoneVerificationCode(
            activity = activity,
            e164PhoneNumber = e164Phone,
            onCodeSent = { verificationId ->
                pendingVerificationId = verificationId
                _isAuthenticating.value = false
                onCodeSent()
            },
            onAutoVerified = { credential ->
                viewModelScope.launch { finishPhoneVerification(activity, credential, onVerified) }
            },
            onError = { message ->
                _isAuthenticating.value = false
                _authErrorMessage.value = message
            }
        )
    }

    /** Verifies the SMS code the user typed in, then routes per [finishPhoneVerification]. */
    fun submitPhoneVerificationCode(
        activity: Activity,
        smsCode: String,
        onVerified: (needsRegistration: Boolean) -> Unit
    ) {
        val verificationId = pendingVerificationId
        if (verificationId == null) {
            _authErrorMessage.value = "Please request a verification code first."
            return
        }
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        val credential = authService.buildPhoneAuthCredential(verificationId, smsCode)
        viewModelScope.launch { finishPhoneVerification(activity, credential, onVerified, verificationId) }
    }

    private suspend fun finishPhoneVerification(
        activity: Activity,
        credential: com.google.firebase.auth.PhoneAuthCredential,
        onVerified: (needsRegistration: Boolean) -> Unit,
        verificationId: String? = null
    ) {
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        val result = try {
            authService.signInWithPhoneCredential(credential, verificationId)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            _isAuthenticating.value = false
            _authErrorMessage.value = "Verification timed out — please check your connection and try again."
            return
        } catch (e: Exception) {
            _isAuthenticating.value = false
            _authErrorMessage.value = "Verification failed: ${e.message ?: "Please try again."}"
            return
        }
        when (result) {
            is com.example.data.auth.AuthResult.Success -> {
                val firebaseUser = result.firebaseUser
                if (firebaseUser == null) {
                    _isAuthenticating.value = false
                    _authErrorMessage.value = "Phone verification did not return a valid session. Please try again."
                    return
                }
                pendingVerificationId = null
                _isAuthenticating.value = false

                when (pendingOtpPurpose) {
                    OtpPurpose.PIN_RESET -> {
                        // Existing user resetting their PIN after OTP — skip registration form.
                        onVerified(false)
                    }
                    OtpPurpose.SIGNUP -> {
                        if (!result.isNewUser) {
                            // Existing account without PIN yet (migration case).
                            try {
                                val integrityToken = com.example.util.PlayIntegrityManager(activity)
                                    .requestIntegrityToken().getOrNull()
                                val user = com.example.data.auth.completeVerifiedLogin(
                                    repository, functionsClient, firebaseUser, integrityToken
                                )
                                if (user.role != com.example.data.model.UserRole.ADMIN && user.phone.isBlank()) {
                                    repository.discardIncompleteSession()
                                    onVerified(true)
                                } else {
                                    registerFcmTokenForCurrentUser(user.id)
                                    // Send to PIN setup for existing accounts with no PIN
                                    onVerified(false)
                                }
                            } catch (e: com.example.data.auth.AccountSuspendedException) {
                                authService.signOut()
                                _authErrorMessage.value = e.message
                            }
                        } else {
                            // Brand-new account — fill in profile first, then set PIN.
                            onVerified(true)
                        }
                    }
                }
            }
            is com.example.data.auth.AuthResult.Error -> {
                _isAuthenticating.value = false
                _authErrorMessage.value = result.message
            }
            com.example.data.auth.AuthResult.Cancelled -> {
                _isAuthenticating.value = false
            }
        }
    }

    // -------------------------------------------------------------------------
    // Step 3 (new accounts): fill in profile
    // -------------------------------------------------------------------------

    fun completePendingRegistration(
        activity: Activity,
        registration: PendingPhoneRegistration,
        onSuccess: () -> Unit
    ) {
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            _authErrorMessage.value = "Your verified session expired — please verify your phone number again."
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
                    "Profile created for ${user.fullName}! Now set your 6-digit PIN."
                } else {
                    "Profile created for ${user.fullName}! Your ${missedUploads.joinToString(" and ")} " +
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

    // -------------------------------------------------------------------------
    // PIN setup (called after signup registration form OR after forgot-PIN OTP)
    // -------------------------------------------------------------------------

    /**
     * Stores a 6-digit PIN for the currently signed-in user (server-side hash).
     * Called after completing the registration form for new accounts, and after
     * OTP verification for forgot-PIN resets. Requires an active Firebase session.
     */
    fun setPin(pin: String, onSuccess: () -> Unit) {
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        viewModelScope.launch {
            val result = functionsClient.setUserPin(pin)
            _isAuthenticating.value = false
            result.fold(
                onSuccess = {
                    _authSuccessMessage.value = "PIN set successfully. You can now log in with your PIN."
                    onSuccess()
                },
                onFailure = { e ->
                    _authErrorMessage.value = e.message?.let {
                        if (it.contains("unauthenticated", ignoreCase = true))
                            "Your session expired. Please verify your phone number again."
                        else
                            "Failed to set PIN. Please try again."
                    } ?: "Failed to set PIN. Please try again."
                }
            )
        }
    }

    private fun friendlyRegistrationErrorMessage(e: Exception): String {
        val message = e.message
        val looksTechnical = message.isNullOrBlank() ||
            message.contains("Firebase", ignoreCase = true) ||
            message.contains("Exception", ignoreCase = true) ||
            message.contains("com.google", ignoreCase = true)
        return if (looksTechnical) {
            "Registration failed. Please check your details and try again."
        } else {
            message!!
        }
    }
}
