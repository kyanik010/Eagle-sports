package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserSession
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.ChannelRepository
import com.example.data.repository.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val repository: ChannelRepository
) : ViewModel() {

    val userSession: StateFlow<UserSession?> = preferencesManager.userSession
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val lastSyncTime: StateFlow<Long> = preferencesManager.lastSyncTime
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val hwDecoding: StateFlow<Boolean> = preferencesManager.hwDecoding
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val autoReconnect: StateFlow<Boolean> = preferencesManager.autoReconnect
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val keepScreenOn: StateFlow<Boolean> = preferencesManager.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val favoritesFirst: StateFlow<Boolean> = preferencesManager.favoritesFirst
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val showLogos: StateFlow<Boolean> = preferencesManager.showLogos
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val showNumbers: StateFlow<Boolean> = preferencesManager.showChannelNumbers
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun syncAll() {
        viewModelScope.launch {
            repository.syncAll { state ->
                _syncState.value = state
            }
        }
    }

    fun syncChannelsOnly() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing("Syncing channels only...")
            val result = repository.syncChannelsOnly()
            if (result.isSuccess) {
                _syncState.value = SyncState.Success(result.getOrDefault(0), 0)
            } else {
                _syncState.value = SyncState.Error(result.exceptionOrNull()?.message ?: "Failed")
            }
        }
    }

    fun syncAudioOnly() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing("Syncing external commentary...")
            val result = repository.syncExternalAudioOnly()
            if (result.isSuccess) {
                _syncState.value = SyncState.Success(0, result.getOrDefault(0))
            } else {
                _syncState.value = SyncState.Error(result.exceptionOrNull()?.message ?: "Failed")
            }
        }
    }

    fun clearCatalog() {
        viewModelScope.launch {
            repository.clearCatalog()
            _syncState.value = SyncState.Idle
        }
    }

    fun toggleHwDecoding(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setHwDecoding(enabled) }
    }

    fun toggleAutoReconnect(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAutoReconnect(enabled) }
    }

    fun toggleKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setKeepScreenOn(enabled) }
    }

    fun toggleFavoritesFirst(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setFavoritesFirst(enabled) }
    }

    fun toggleShowLogos(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setShowLogos(enabled) }
    }

    fun toggleShowNumbers(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setShowChannelNumbers(enabled) }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            preferencesManager.clearSession()
            onLoggedOut()
        }
    }
}
