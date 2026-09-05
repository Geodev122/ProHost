package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthResult
import com.example.data.auth.FirebaseAuthService
import com.example.data.auth.FirebaseFunctionsClient
import com.example.data.auth.completeVerifiedLogin
import com.example.data.auth.completeVerifiedRegistration
import com.example.data.model.*
import com.example.data.repository.ProSpaceRepository
import com.example.ui.state.AuthUiEvent
import com.example.ui.state.AuthUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing authentication, user session persistence, role selection, and Lebanese Syndicate checks.
 *
 * Role is never trusted from the client: login() resolves the signed-in user's role from
 * their Firebase Auth ID token's custom claim, and register() can only ever request the
 * PROFESSIONAL default or a SPACE_OWNER self-upgrade (never ADMIN) — both decided
 * server-side by the assignInitialRole/requestRoleUpgrade Cloud Functions. See
 * com.example.data.auth.AuthFlow.
 */
class AuthViewModel(
    private val repository: ProSpaceRepository = ProSpaceRepository.getInstance()
) : ViewModel() {

    private val functionsClient = FirebaseFunctionsClient()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthUiEvent>()
    val events: SharedFlow<AuthUiEvent> = _events.asSharedFlow()

    init {
        // Observe currentUser from repository
        viewModelScope.launch {
            repository.currentUser.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    fun setLoginMode(isLogin: Boolean) {
        _uiState.update { it.copy(isLoginMode = isLogin, errorMessage = null, successMessage = null) }
    }

    fun setRole(role: UserRole) {
        // ADMIN is never a selectable registration role — clamp to PROFESSIONAL if it
        // somehow gets passed in (defensive; the UI should never offer it as an option).
        val safeRole = if (role == UserRole.ADMIN) UserRole.PROFESSIONAL else role
        _uiState.update { it.copy(selectedRole = safeRole) }
    }

    fun updateEmail(email: String) {
        _uiState.update { it.copy(emailInput = email) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(passwordInput = password) }
    }

    fun updateFullName(fullName: String) {
        _uiState.update { it.copy(fullNameInput = fullName) }
    }

    fun updatePhone(phone: String) {
        _uiState.update { it.copy(phoneInput = phone) }
    }

    fun updateSpecialty(specialty: String) {
        _uiState.update { it.copy(specialtyInput = specialty) }
    }

    fun updateAffiliation(affiliation: String) {
        _uiState.update { it.copy(affiliationInput = affiliation) }
    }

    fun updateSyndicateNumber(syndicate: String) {
        _uiState.update { it.copy(syndicateNumberInput = syndicate) }
    }

    fun updateGovernorate(governorate: Governorate) {
        _uiState.update { it.copy(selectedGovernorate = governorate) }
    }

    /** Signs in with real Firebase Auth; role is resolved server-side, never from [uiState]. */
    fun login(context: Context) {
        val state = _uiState.value
        if (state.emailInput.isBlank() || state.passwordInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please provide your email and password.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val authService = FirebaseAuthService(context)
            when (val result = authService.signInWithEmail(state.emailInput, state.passwordInput)) {
                is AuthResult.Success -> {
                    val firebaseUser = result.firebaseUser
                    if (firebaseUser == null) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Sign-in did not return a valid session.") }
                        return@launch
                    }
                    val user = completeVerifiedLogin(repository, functionsClient, firebaseUser)
                    _uiState.update { it.copy(isLoading = false, currentUser = user, successMessage = "Welcome back, ${user.fullName}!") }
                    _events.emit(AuthUiEvent.NavigateToMain)
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                AuthResult.Cancelled -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    /** Registers with real Firebase Auth. selectedRole may only ever resolve to PROFESSIONAL or SPACE_OWNER. */
    fun register(context: Context) {
        val state = _uiState.value
        if (state.emailInput.isBlank() || state.fullNameInput.isBlank() || state.passwordInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please provide email, full name, and password.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val authService = FirebaseAuthService(context)
            when (val result = authService.registerWithEmail(state.emailInput, state.passwordInput, state.fullNameInput)) {
                is AuthResult.Success -> {
                    val firebaseUser = result.firebaseUser
                    if (firebaseUser == null) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Registration did not return a valid session.") }
                        return@launch
                    }
                    val newUser = completeVerifiedRegistration(
                        repository = repository,
                        functionsClient = functionsClient,
                        firebaseUser = firebaseUser,
                        requestedRole = state.selectedRole,
                        fullName = state.fullNameInput.trim(),
                        phone = state.phoneInput.trim(),
                        specialty = state.specialtyInput.trim(),
                        syndicateNumber = state.syndicateNumberInput.trim(),
                        affiliation = state.affiliationInput.trim(),
                        governorate = state.selectedGovernorate
                    )
                    _uiState.update { it.copy(isLoading = false, currentUser = newUser, successMessage = "Profile activated!") }
                    _events.emit(AuthUiEvent.NavigateToMain)
                }
                is AuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                AuthResult.Cancelled -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            repository.logout()
            _events.emit(AuthUiEvent.LoggedOut)
        }
    }
}
