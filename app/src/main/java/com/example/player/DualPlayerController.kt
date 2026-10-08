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
 * DualPlayerController:
 * Orchestrates VideoPlayerEngine and ExternalAudioEngine cleanly as two completely separate engines.
 * Switching audio does NOT reload or recreate the video player.
 */
class DualPlayerController(
    context: Context,
    private val scope: CoroutineScope
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
        // Stop any external audio from previous channel, revert to original audio by default
        audioEngine.stopExternalAudio()
        videoEngine.setMuted(false)
        _audioMode.value = AudioSourceMode.Original
        _selectedExternalAudio.value = null

        videoEngine.playChannel(channel)
    }

    /**
     * Switch to External Audio Track:
     * - Video continues playing uninterrupted
     * - Video is NOT reloaded or recreated
     * - Video player's internal audio track is muted
     * - Independent ExternalAudioEngine starts playing
     */
    fun selectExternalAudio(audio: ExternalAudioEntity) {
        _selectedExternalAudio.value = audio
        _audioMode.value = AudioSourceMode.External(audio.stableId, audio.title)

        // Mute video player's original audio stream so only external audio is heard
        videoEngine.setMuted(true)

        // Start external audio engine independently
        audioEngine.playExternalAudio(audio)
    }

    /**
     * Revert back to Original Channel Audio:
     * - Stops external audio engine cleanly
     * - Unmutes video engine without resetting or seeking video
     */
    fun selectOriginalAudio() {
        audioEngine.stopExternalAudio()
        videoEngine.setMuted(false)
        _audioMode.value = AudioSourceMode.Original
        _selectedExternalAudio.value = null
    }

    fun togglePlayPause() {
        videoEngine.togglePlayPause()
        if (_audioMode.value is AudioSourceMode.External) {
            audioEngine.togglePlayPause()
        }
    }

    fun stopAll() {
        audioEngine.stopExternalAudio()
        videoEngine.stop()
    }

    fun release() {
        videoEngine.release()
        audioEngine.release()
    }
}
