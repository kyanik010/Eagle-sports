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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as EagleSportsApp).channelRepository
    private val preferencesManager = (application as EagleSportsApp).preferencesManager

    val dualPlayerController = DualPlayerController(application, viewModelScope)

    val currentChannel: StateFlow<ChannelEntity?> = dualPlayerController.currentChannel
    val videoStatus: StateFlow<PlaybackStatus> = dualPlayerController.mpvEngine.status
    val audioStatus: StateFlow<PlaybackStatus> = dualPlayerController.mpvEngine.audioStatus
    val audioMode: StateFlow<AudioSourceMode> = dualPlayerController.audioMode
    val audioDelayMs: StateFlow<Int> = dualPlayerController.mpvEngine.manualDelayMs
    val videoError: StateFlow<String?> = dualPlayerController.mpvEngine.error
    val audioError: StateFlow<String?> = dualPlayerController.mpvEngine.audioError

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
            // Offer audio sources from the customer's existing IPTV channel list first.
            // This avoids requiring a separate audio M3U/subscription just to select another
            // channel as the commentary source. Keep explicitly configured audio-only tracks too.
            val iptvChannels = repository.allChannels.firstOrNull().orEmpty()
                .filterNot { it.stableId == channel.stableId }
                .filter { item ->
                    val scheme = android.net.Uri.parse(item.streamUrl).scheme?.lowercase()
                    (scheme == "http" || scheme == "https") && !android.net.Uri.parse(item.streamUrl).host.isNullOrBlank()
                }
                .map { item ->
                    ExternalAudioEntity(
                        stableId = "iptv-channel:${item.stableId}",
                        channelStableId = item.stableId,
                        tvgId = item.tvgId,
                        normalizedName = com.example.data.iptv.M3uParser.normalizeName(item.name),
                        title = item.name,
                        streamUrl = item.streamUrl,
                        language = null,
                        commentator = "من اشتراك IPTV الحالي",
                        bitrate = "قناة IPTV"
                    )
                }
            val configuredAudioTracks = repository.getAllExternalAudio()
            _availableAudioTracks.value = (iptvChannels + configuredAudioTracks)
                .distinctBy { it.streamUrl.trim() }
        }
    }

    fun togglePlayPause() {
        dualPlayerController.togglePlayPause()
    }

    fun stopPlayback() {
        dualPlayerController.stopAll()
    }

    fun retryVideo() {
        currentChannel.value?.let { dualPlayerController.mpvEngine.playVideo(it.streamUrl) }
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
        dualPlayerController.mpvEngine.adjustManualDelay(deltaMs)
        viewModelScope.launch {
            preferencesManager.setExternalAudioDelayMs(dualPlayerController.mpvEngine.manualDelayMs.value)
        }
    }

    override fun onCleared() {
        super.onCleared()
        dualPlayerController.release()
    }
}
