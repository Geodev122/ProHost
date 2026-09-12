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
 * ViewModel for LoginAuthScreen — the phone-OTP-only sign-in/registration flow.
 * Relocated here from ProHostViewModel (the ViewModel-split effort): every
 * method/state field below had exactly one caller (LoginAuthScreen) before this
 * move, unlike the payment, WhatsApp, and booking-dialog logic that stayed on the
 * shared ViewModel because multiple different screens call it identically.
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
     * Everything the registration form collects, submitted only AFTER the phone number
     * is already verified (see [startPhoneVerification]/[submitPhoneVerificationCode] —
     * this app has exactly one entry point, phone-first: verify, then — only for a
     * brand-new number — fill in the rest of the profile). Passed to
     * [completePendingRegistration].
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
        // The registration form's Terms of Use / Privacy Policy checkbox must have
        // actually been checked before this reaches here — enforced client-side by
        // the form's own submit gate, and again server-side by assignInitialRole.ts,
        // which rejects registration outright if this isn't literally true.
        val tosAccepted: Boolean
    )

    private var pendingVerificationId: String? = null

    /**
     * Backfills this device's current FCM token onto [uid]'s profile right after a
     * successful sign-in/registration — [com.example.service.ProHostMessagingService.onNewToken]
     * only fires on a genuine token refresh, which could be long after this device
     * first got a token (e.g. it was assigned before this account ever signed in).
     * Best effort: a failure here shouldn't block sign-in.
     */
    private fun registerFcmTokenForCurrentUser(uid: String) {
        viewModelScope.launch {
            runCatching {
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                repository.registerFcmToken(uid, token)
            }
        }
    }

    /**
     * Step 1 of the ONE sign-in/registration entry point this app has: send an SMS OTP
     * to [e164Phone]. There is no separate "Sign In" vs "Register" form anymore — every
     * account, new or returning, starts here with nothing but a phone number. What
     * happens after the code is verified — sign the caller straight into an existing
     * account, or ask them to fill in the rest of a brand-new profile — is decided in
     * [submitPhoneVerificationCode] purely from Firebase's own `isNewUser` signal, never
     * guessed or asked up front.
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

    /** Step 2: verifies the SMS code the user typed in, then routes per [finishPhoneVerification]. */
    fun submitPhoneVerificationCode(activity: Activity, smsCode: String, onVerified: (needsRegistration: Boolean) -> Unit) {
        val verificationId = pendingVerificationId
        if (verificationId == null) {
            _authErrorMessage.value = "Please request a verification code first."
            return
        }
        _isAuthenticating.value = true
        _authErrorMessage.value = null
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        val credential = authService.buildPhoneAuthCredential(verificationId, smsCode)
        viewModelScope.launch { finishPhoneVerification(activity, credential, onVerified) }
    }

    /**
     * Resolves the verified phone credential and decides what the caller sees next:
     * an existing account is never routed back through a registration form — only a
     * genuinely brand-new phone number is.
     */
    private suspend fun finishPhoneVerification(
        activity: Activity,
        credential: com.google.firebase.auth.PhoneAuthCredential,
        onVerified: (needsRegistration: Boolean) -> Unit
    ) {
        val authService = com.example.data.auth.FirebaseAuthService(activity)
        val result = authService.signInWithPhoneCredential(credential)
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
                if (!result.isNewUser) {
                    // This exact phone number already had an account — sign the caller
                    // straight into it, no registration form, nothing to overwrite.
                    try {
                        val user = com.example.data.auth.completeVerifiedLogin(repository, functionsClient, firebaseUser)
                        registerFcmTokenForCurrentUser(user.id)
                        _authSuccessMessage.value = "Welcome back, ${user.fullName}!"
                        onVerified(false)
                    } catch (e: com.example.data.auth.AccountSuspendedException) {
                        authService.signOut()
                        _authErrorMessage.value = e.message
                    }
                } else {
                    // Brand-new phone number — Firebase Auth already has a signed-in
                    // session for it; the caller just needs to fill in the rest of
                    // their profile now.
                    onVerified(true)
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

    /**
     * Step 3 (brand-new accounts only): the phone number is already verified and
     * Firebase Auth already has a signed-in session for it (from
     * [finishPhoneVerification]) — this just uploads the picked files and writes the
     * rest of the profile. No further OTP step; verification already happened.
     */
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
                    tosAccepted = registration.tosAccepted
                )
                _isAuthenticating.value = false
                registerFcmTokenForCurrentUser(user.id)
                _authSuccessMessage.value = "Account created successfully for ${user.fullName}!"
                onSuccess()
            } catch (e: com.example.data.auth.AccountSuspendedException) {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                _isAuthenticating.value = false
                _authErrorMessage.value = e.message
            } catch (e: Exception) {
                // Covers assignInitialRole's server-side registration-format rejection
                // (invalid-argument) as well as any upload/network failure — previously
                // uncaught here, which would have crashed the coroutine instead of
                // surfacing a message the registration form could show.
                _isAuthenticating.value = false
                _authErrorMessage.value = friendlyRegistrationErrorMessage(e)
            }
        }
    }

    /**
     * A Cloud Function's own rejection message (e.g. assignInitialRole's format
     * validation) is already written for end users and safe to show as-is. Anything
     * else — a raw network/SDK exception — is never shown verbatim, since it can
     * contain technical text ("FirebaseFunctionsException", stack-trace fragments)
     * that would read like an app crash to someone who has never heard of Firebase.
     */
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
