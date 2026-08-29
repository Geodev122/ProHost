package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ProSpaceRepository
import com.example.ui.state.AuthUiEvent
import com.example.ui.state.AuthUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing authentication, user session persistence, role selection, and Lebanese Syndicate checks.
 */
class AuthViewModel(
    private val repository: ProSpaceRepository = ProSpaceRepository.getInstance()
) : ViewModel() {

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
        _uiState.update { it.copy(selectedRole = role) }
    }

    fun updateEmail(email: String) {
        _uiState.update { it.copy(emailInput = email) }
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

    fun login(email: String, role: UserRole) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val user = repository.login(email.trim(), role)
            _uiState.update { it.copy(isLoading = false, currentUser = user, successMessage = "Welcome back, ${user.fullName}!") }
            _events.emit(AuthUiEvent.NavigateToMain)
        }
    }

    fun register() {
        val state = _uiState.value
        if (state.emailInput.isBlank() || state.fullNameInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please provide email and full name.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val newUser = AppUser(
                id = "user_${System.currentTimeMillis()}",
                email = state.emailInput.trim(),
                fullName = state.fullNameInput.trim(),
                role = state.selectedRole,
                specialty = state.specialtyInput.trim(),
                phone = state.phoneInput.trim(),
                affiliation = state.affiliationInput.trim(),
                syndicateNumber = state.syndicateNumberInput.trim(),
                governorate = state.selectedGovernorate,
                isVerified = true
            )
            repository.setCurrentUser(newUser)
            _uiState.update { it.copy(isLoading = false, currentUser = newUser, successMessage = "Profile activated!") }
            _events.emit(AuthUiEvent.NavigateToMain)
        }
    }

    fun selectPresetDemoUser(user: AppUser) {
        viewModelScope.launch {
            repository.setCurrentUser(user)
            _events.emit(AuthUiEvent.NavigateToMain)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _events.emit(AuthUiEvent.LoggedOut)
        }
    }
}
