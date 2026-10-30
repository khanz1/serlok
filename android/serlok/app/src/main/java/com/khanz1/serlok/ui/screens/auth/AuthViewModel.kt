package com.khanz1.serlok.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.data.remote.AuthApiClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle    : AuthUiState()
    object Loading : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel : ViewModel() {

    private val api = AuthApiClient()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _navigateToHome = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToHome: SharedFlow<Unit> = _navigateToHome.asSharedFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all fields")
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            api.login(email.trim(), password).fold(
                onSuccess = { result ->
                    AppSettings.saveAuth(result.token, result.user.id, result.user.name, result.user.email)
                    _uiState.value = AuthUiState.Idle
                    _navigateToHome.tryEmit(Unit)
                },
                onFailure = { _uiState.value = AuthUiState.Error(it.message ?: "Login failed") },
            )
        }
    }

    fun register(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all fields")
            return
        }
        if (password.length < 8) {
            _uiState.value = AuthUiState.Error("Password must be at least 8 characters")
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            api.register(name.trim(), email.trim(), password).fold(
                onSuccess = { result ->
                    AppSettings.saveAuth(result.token, result.user.id, result.user.name, result.user.email)
                    _uiState.value = AuthUiState.Idle
                    _navigateToHome.tryEmit(Unit)
                },
                onFailure = { _uiState.value = AuthUiState.Error(it.message ?: "Registration failed") },
            )
        }
    }

    fun clearError() { _uiState.value = AuthUiState.Idle }
}
