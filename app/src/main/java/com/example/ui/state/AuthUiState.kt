package com.example.ui.state

import com.example.data.model.*

/**
 * Immutable UI State representing authentication & session management.
 */
data class AuthUiState(
    val currentUser: AppUser? = null,
    val isLoading: Boolean = false,
    val selectedRole: UserRole = UserRole.PROFESSIONAL,
    val isLoginMode: Boolean = true,
    val emailInput: String = "",
    val fullNameInput: String = "",
    val phoneInput: String = "+961 ",
    val specialtyInput: String = "",
    val affiliationInput: String = "",
    val syndicateNumberInput: String = "",
    val selectedGovernorate: Governorate = Governorate.BEIRUT,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

/**
 * One-time UI events emitted by the Auth flow.
 */
sealed interface AuthUiEvent {
    data class ShowToast(val message: String) : AuthUiEvent
    data object NavigateToMain : AuthUiEvent
    data object LoggedOut : AuthUiEvent
}
