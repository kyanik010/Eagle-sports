package com.example.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.iptv.DefaultIptvProvider
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.ChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val enteredPass = pass

        if (trimmedUser.isEmpty()) {
            _uiState.value = LoginUiState.Error("Please enter your Username")
            return
        }
        if (enteredPass.isBlank()) {
            _uiState.value = LoginUiState.Error("Please enter your Password")
            return
        }

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            // OkHttp execute() is blocking; never run the activation request on Android's main thread.
            val result = withContext(Dispatchers.IO) {
                iptvProvider.authenticate(trimmedUser, enteredPass)
            }
            if (result.isSuccess) {
                val session = result.getOrThrow()
                preferencesManager.saveSession(session)
                // Repository performs initial channel sync on Dispatchers.IO.
                channelRepository.syncAll()
                _uiState.value = LoginUiState.Success
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
