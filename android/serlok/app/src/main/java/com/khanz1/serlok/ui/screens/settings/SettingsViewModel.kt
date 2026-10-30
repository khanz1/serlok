package com.khanz1.serlok.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.data.remote.AuthApiClient
import com.khanz1.serlok.data.remote.UserApiClient
import com.khanz1.serlok.safety.DebugSnapshot
import com.khanz1.serlok.service.SosEventBus
import com.khanz1.serlok.ui.screens.room.RoomViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val name: String = AppSettings.userName,
    val email: String = AppSettings.userEmail,
    val refreshing: Boolean = false,
    val gforceDebugEnabled: Boolean = AppSettings.gforceDebugEnabled,
    val debugSnapshot: DebugSnapshot = DebugSnapshot.empty(),
)

class SettingsViewModel : ViewModel() {
    private val authApi = AuthApiClient()
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            SosEventBus.debugSnapshot.collect { snapshot ->
                _uiState.update { state -> state.copy(debugSnapshot = snapshot) }
            }
        }
    }

    fun refreshProfile() {
        if (_uiState.value.refreshing) return
        _uiState.value = _uiState.value.copy(refreshing = true)
        viewModelScope.launch {
            val profile = UserApiClient.fetchMe()
            if (profile != null) {
                AppSettings.saveUserProfile(profile.name, profile.email)
                _uiState.value = _uiState.value.copy(
                    name = profile.name,
                    email = profile.email,
                    refreshing = false,
                )
            } else {
                _uiState.value = _uiState.value.copy(refreshing = false)
            }
        }
    }

    fun setGforceDebugEnabled(enabled: Boolean) {
        AppSettings.setGforceDebugEnabled(enabled)
        _uiState.update { state -> state.copy(gforceDebugEnabled = enabled) }
    }

    fun signOut(roomViewModel: RoomViewModel, onComplete: () -> Unit) {
        viewModelScope.launch {
            roomViewModel.roomState.value?.let { roomViewModel.leaveRoom() }
            if (AppSettings.token.isNotBlank()) authApi.logout()
            roomViewModel.disconnectWebSocket()
            AppSettings.clearAuth()
            onComplete()
        }
    }
}
