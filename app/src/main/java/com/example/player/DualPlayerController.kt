package com.example.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.ChannelEntity
import com.example.data.model.ExternalAudioEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Media3 owns video playback and a separate Media3 instance owns optional external
 * commentary. Native libmpv is deliberately not used: the app uses one consistent
 * playback stack and audio failures cannot tear down the video player.
 */
class DualPlayerController(
    context: Context,
    scope: CoroutineScope
) {
    val videoEngine = VideoPlayerEngine(context, scope)
    val audioEngine = ExternalAudioEngine(context, scope)

    private val _currentChannel = MutableStateFlow<ChannelEntity?>(null)
    val currentChannel: StateFlow<ChannelEntity?> = _currentChannel.asStateFlow()

    private val _audioMode = MutableStateFlow<AudioSourceMode>(AudioSourceMode.Original)
    val audioMode: StateFlow<AudioSourceMode> = _audioMode.asStateFlow()

    private val _selectedExternalAudio = MutableStateFlow<ExternalAudioEntity?>(null)
    val selectedExternalAudio: StateFlow<ExternalAudioEntity?> = _selectedExternalAudio.asStateFlow()

    fun getVideoPlayer(): ExoPlayer? = videoEngine.getPlayer()

    fun playChannel(channel: ChannelEntity) {
        _currentChannel.value = channel
        audioEngine.stopExternalAudio()
        videoEngine.setMuted(false)
        _audioMode.value = AudioSourceMode.Original
        _selectedExternalAudio.value = null
        videoEngine.playChannel(channel)
    }

    fun selectExternalAudio(audio: ExternalAudioEntity) {
        if (audio.streamUrl == _currentChannel.value?.streamUrl) {
            return
        }
        // The external stream has its own Media3 instance. Never recreate or stop video.
        audioEngine.playExternalAudio(audio)
        videoEngine.setMuted(true)
        _selectedExternalAudio.value = audio
        _audioMode.value = AudioSourceMode.External(audio.stableId, audio.title)
    }

    fun selectOriginalAudio() {
        audioEngine.stopExternalAudio()
        videoEngine.setMuted(false)
        _audioMode.value = AudioSourceMode.Original
        _selectedExternalAudio.value = null
    }

    fun togglePlayPause() {
        videoEngine.togglePlayPause()
    }

    fun stopAll() {
        audioEngine.stopExternalAudio()
        videoEngine.setMuted(false)
        videoEngine.stop()
        _audioMode.value = AudioSourceMode.Original
        _selectedExternalAudio.value = null
    }

    fun release() {
        audioEngine.release()
        videoEngine.release()
    }
}
