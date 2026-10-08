package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.data.model.ExternalAudioEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * ExternalAudioEngine:
 * An entirely independent Media3 / ExoPlayer instance dedicated solely to external audio streaming.
 * - Does NOT recreate or reload or seek the VideoPlayerEngine.
 * - Can fail independently: if external audio stream errors, video continues playing uninterrupted.
 * - Applies manual audio delay offset (-3000ms to +3000ms).
 * - No aggressive continuous seeking or auto-sync watchdogs.
 */
class ExternalAudioEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var audioPlayer: ExoPlayer? = null
    private var currentAudio: ExternalAudioEntity? = null
    private var pendingDelayMs = 0

    private val _status = MutableStateFlow(PlaybackStatus.IDLE)
    val status: StateFlow<PlaybackStatus> = _status.asStateFlow()

    private val _delayMs = MutableStateFlow(0)
    val delayMs: StateFlow<Int> = _delayMs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    init {
        initAudioPlayer()
    }

    private fun initAudioPlayer() {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("EagleSports/1.0 (AudioEngine)")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000, // minBufferMs
                4000, // maxBufferMs
                800,  // bufferForPlaybackMs
                1500  // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Audio-only renderers factory
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> {
                        if (_status.value != PlaybackStatus.ERROR) {
                            _status.value = PlaybackStatus.IDLE
                        }
                    }
                    Player.STATE_BUFFERING -> _status.value = PlaybackStatus.BUFFERING
                    Player.STATE_READY -> _status.value = if (player.playWhenReady) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
                    Player.STATE_ENDED -> _status.value = PlaybackStatus.IDLE
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    _status.value = PlaybackStatus.PLAYING
                } else if (_status.value == PlaybackStatus.PLAYING) {
                    _status.value = PlaybackStatus.PAUSED
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // If External audio fails, ONLY audio status changes to ERROR.
                // Video player is untouched and continues unaffected.
                _errorMessage.value = error.message ?: "External commentary stream unavailable"
                _status.value = PlaybackStatus.ERROR
            }
        })

        audioPlayer = player
    }

    fun playExternalAudio(audio: ExternalAudioEntity, videoPositionMs: Long = 0L) {
        currentAudio = audio
        _errorMessage.value = null
        _status.value = PlaybackStatus.PREPARING

        scope.launch(Dispatchers.Main) {
            val player = audioPlayer ?: return@launch
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(audio.streamUrl))
                .setMediaId(audio.stableId)
                .build()

            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            // For seekable external streams, apply only the user-selected manual offset.
            // The video engine is never touched here.
            if (player.isCurrentMediaItemSeekable && pendingDelayMs != 0) {
                player.seekTo((videoPositionMs + pendingDelayMs).coerceAtLeast(0L))
            }
        }
    }

    /**
     * Stop external audio cleanly without resetting or interrupting the video player.
     */
    fun stopExternalAudio() {
        scope.launch(Dispatchers.Main) {
            audioPlayer?.stop()
            audioPlayer?.clearMediaItems()
            _status.value = PlaybackStatus.IDLE
            currentAudio = null
        }
    }

    fun togglePlayPause() {
        audioPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    /**
     * Manual Delay Adjustments (-3000ms to +3000ms with step of 50ms or 100ms)
     */
    fun adjustDelay(deltaMs: Int) {
        val newDelay = (_delayMs.value + deltaMs).coerceIn(-3000, 3000)
        setDelay(newDelay)
    }

    fun setDelay(delay: Int) {
        val clamped = delay.coerceIn(-3000, 3000)
        val previousDelay = _delayMs.value
        val deltaMs = clamped - previousDelay

        _delayMs.value = clamped
        pendingDelayMs = clamped

        // Adjust ONLY the external audio timeline by the amount the user changed.
        // The video player is never seeked, paused, reloaded, or recreated.
        if (deltaMs != 0) {
            audioPlayer?.let { player ->
                if (player.isCurrentMediaItemSeekable) {
                    player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
                }
            }
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        audioPlayer?.volume = clamped
    }

    fun release() {
        _status.value = PlaybackStatus.RELEASED
        audioPlayer?.release()
        audioPlayer = null
    }
}
