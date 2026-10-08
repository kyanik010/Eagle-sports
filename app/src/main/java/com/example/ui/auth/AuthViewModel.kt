package com.example.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.iptv.DefaultIptvProvider
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class AuthViewModel(
    private val preferencesManager: PreferencesManager,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val iptvProvider = DefaultIptvProvider()

    fun login(username: String, pass: String) {
        val trimmedUser = username.trim()
        val trimmedPass = pass.trim()

        if (trimmedUser.isEmpty()) {
            _uiState.value = LoginUiState.Error("Please enter your Username")
            return
        }
        if (trimmedPass.isEmpty()) {
            _uiState.value = LoginUiState.Error("Please enter your Password")
            return
        }

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            val result = iptvProvider.authenticate(trimmedUser, trimmedPass)
            if (result.isSuccess) {
                val session = result.getOrThrow()
                preferencesManager.saveSession(session)

                // Show the app immediately after authentication.
                // Catalog synchronization runs independently so login is never blocked
                // by IPTV/audio M3U download or parsing.
                _uiState.value = LoginUiState.Success

                viewModelScope.launch {
                    channelRepository.syncAll()
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Authentication failed"
                _uiState.value = LoginUiState.Error(err)
            }
        }
    }

    fun clearError() {
        _uiState.value = LoginUiState.Idle
    }
}
