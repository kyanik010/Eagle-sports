package com.example.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.EagleSportsApp
import com.example.data.model.ChannelEntity
import com.example.data.model.ExternalAudioEntity
import com.example.player.AudioSourceMode
import com.example.player.DualPlayerController
import com.example.player.PlaybackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as EagleSportsApp).channelRepository
    private val preferencesManager = (application as EagleSportsApp).preferencesManager

    val dualPlayerController = DualPlayerController(application, viewModelScope)

    val currentChannel: StateFlow<ChannelEntity?> = dualPlayerController.currentChannel
    val videoStatus: StateFlow<PlaybackStatus> = dualPlayerController.videoEngine.status
    val audioStatus: StateFlow<PlaybackStatus> = dualPlayerController.audioEngine.status
    val audioMode: StateFlow<AudioSourceMode> = dualPlayerController.audioMode
    val audioDelayMs: StateFlow<Int> = dualPlayerController.audioEngine.delayMs
    val videoError: StateFlow<String?> = dualPlayerController.videoEngine.errorMessage
    val audioError: StateFlow<String?> = dualPlayerController.audioEngine.errorMessage

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _availableAudioTracks = MutableStateFlow<List<ExternalAudioEntity>>(emptyList())
    val availableAudioTracks: StateFlow<List<ExternalAudioEntity>> = _availableAudioTracks.asStateFlow()

    private val _channelsList = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val channelsList: StateFlow<List<ChannelEntity>> = _channelsList.asStateFlow()

    val keepScreenOn = preferencesManager.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    init {
        viewModelScope.launch {
            repository.allChannels.collect {
                _channelsList.value = it
            }
        }
    }

    fun loadChannel(channel: ChannelEntity) {
        dualPlayerController.playChannel(channel)
        _isFavorite.value = channel.isFavorite
        viewModelScope.launch {
            preferencesManager.setLastChannelId(channel.stableId)
            // Refresh favorite state from the database, as channel objects passed by navigation
            // may predate the latest favorite toggle.
            repository.getChannelById(channel.stableId)?.let { saved ->
                if (currentChannel.value?.stableId == saved.stableId) {
                    _isFavorite.value = saved.isFavorite
                }
            }
            // Show the complete external-audio M3U catalog. Audio channel names often differ
            // from the video channel name, so filtering by the current video channel hides valid tracks.
            val tracks = repository.getAllExternalAudio()
            _availableAudioTracks.value = tracks
        }
    }

    fun togglePlayPause() {
        dualPlayerController.togglePlayPause()
    }

    fun stopPlayback() {
        dualPlayerController.stopAll()
    }

    fun retryVideo() {
        dualPlayerController.videoEngine.retryPlayback()
    }

    fun channelUp() {
        val list = _channelsList.value
        val cur = currentChannel.value ?: return
        val idx = list.indexOfFirst { it.stableId == cur.stableId }
        if (idx != -1 && list.isNotEmpty()) {
            val nextIdx = (idx + 1) % list.size
            loadChannel(list[nextIdx])
        }
    }

    fun channelDown() {
        val list = _channelsList.value
        val cur = currentChannel.value ?: return
        val idx = list.indexOfFirst { it.stableId == cur.stableId }
        if (idx != -1 && list.isNotEmpty()) {
            val prevIdx = if (idx - 1 < 0) list.size - 1 else idx - 1
            loadChannel(list[prevIdx])
        }
    }

    fun toggleFavorite() {
        val cur = currentChannel.value ?: return
        val requestedFavorite = !_isFavorite.value
        // Update immediately so the star responds without waiting for disk I/O.
        _isFavorite.value = requestedFavorite
        viewModelScope.launch {
            try {
                repository.toggleFavorite(cur.stableId)
                val saved = repository.getChannelById(cur.stableId)
                if (currentChannel.value?.stableId == cur.stableId) {
                    _isFavorite.value = saved?.isFavorite ?: requestedFavorite
                }
            } catch (_: Exception) {
                if (currentChannel.value?.stableId == cur.stableId) {
                    _isFavorite.value = !requestedFavorite
                }
            }
        }
    }

    fun selectExternalAudio(audio: ExternalAudioEntity) {
        dualPlayerController.selectExternalAudio(audio)
    }

    fun selectOriginalAudio() {
        dualPlayerController.selectOriginalAudio()
    }

    fun adjustAudioDelay(deltaMs: Int) {
        dualPlayerController.audioEngine.adjustDelay(deltaMs)
        viewModelScope.launch {
            preferencesManager.setExternalAudioDelayMs(dualPlayerController.audioEngine.delayMs.value)
        }
    }

    override fun onCleared() {
        super.onCleared()
        dualPlayerController.release()
    }
}
