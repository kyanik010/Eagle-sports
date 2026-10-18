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
    // Keep the legacy Media3 engines available for callers that explicitly use them,
    // but do not allocate two extra ExoPlayers/HTTP clients during normal libmpv playback.
    private val videoEngineDelegate = lazy(LazyThreadSafetyMode.NONE) { VideoPlayerEngine(context, scope) }
    val videoEngine by videoEngineDelegate
    private val audioEngineDelegate = lazy(LazyThreadSafetyMode.NONE) { ExternalAudioEngine(context, scope) }
    val audioEngine by audioEngineDelegate
    val mpvEngine = MpvSessionEngine(context)

    private val _currentChannel = MutableStateFlow<ChannelEntity?>(null)
    val currentChannel: StateFlow<ChannelEntity?> = _currentChannel.asStateFlow()

    private val _audioMode = MutableStateFlow<AudioSourceMode>(AudioSourceMode.Original)
    val audioMode: StateFlow<AudioSourceMode> = _audioMode.asStateFlow()

    private val _selectedExternalAudio = MutableStateFlow<ExternalAudioEntity?>(null)
    val selectedExternalAudio: StateFlow<ExternalAudioEntity?> = _selectedExternalAudio.asStateFlow()

    fun getVideoPlayer(): ExoPlayer? = videoEngine.getPlayer()

    fun playChannel(channel: ChannelEntity) {
        _currentChannel.value = channel
        // The active libmpv session owns both video and commentary. Stop only that
        // session; touching legacy engines here would initialize them unnecessarily.
        mpvEngine.stop()
        _audioMode.value = AudioSourceMode.Original
        _selectedExternalAudio.value = null

        mpvEngine.playVideo(channel.streamUrl)
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

        // Add commentary to the same libmpv session as the live video.
        // This keeps one playback clock and leaves delay entirely user-controlled.
        mpvEngine.addExternalAudio(audio.streamUrl)
    }

    /**
     * Revert back to Original Channel Audio:
     * - Stops external audio engine cleanly
     * - Unmutes video engine without resetting or seeking video
     */
    fun selectOriginalAudio() {
        mpvEngine.clearExternalAudio()
        _audioMode.value = AudioSourceMode.Original
        _selectedExternalAudio.value = null
    }

    fun togglePlayPause() {
        mpvEngine.togglePlayPause()
    }

    fun stopAll() {
        mpvEngine.stop()
    }

    fun release() {
        mpvEngine.close()
        if (videoEngineDelegate.isInitialized()) videoEngine.release()
        if (audioEngineDelegate.isInitialized()) audioEngine.release()
    }
}
