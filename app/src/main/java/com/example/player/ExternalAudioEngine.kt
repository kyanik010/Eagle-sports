package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.example.data.model.ExternalAudioEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * ExternalAudioEngine:
 * An independent Media3 / ExoPlayer instance dedicated solely to external audio.
 * Audio errors and retries never reload, seek, or recreate the video player.
 * Audio delay remains manual; no automatic synchronization is performed.
 */
class ExternalAudioEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var audioPlayer: ExoPlayer? = null
    private var audioHttpClient: OkHttpClient? = null
    private var currentAudio: ExternalAudioEntity? = null
    private var retryJob: Job? = null
    private var retryAttempts = 0
    private var operationId = 0L

    private val maxRetryAttempts = 2
    private val retryDelaysMs = longArrayOf(1000L, 2500L)

    private val _status = MutableStateFlow(PlaybackStatus.IDLE)
    val status: StateFlow<PlaybackStatus> = _status.asStateFlow()

    private val _delayMs = MutableStateFlow(0)
    val delayMs: StateFlow<Int> = _delayMs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    init {
        try {
            initAudioPlayer()
        } catch (e: Exception) {
            _errorMessage.value = "External audio unavailable: ${e.message ?: e.javaClass.simpleName}"
            _status.value = PlaybackStatus.ERROR
        }
    }

    private fun initAudioPlayer() {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
        audioHttpClient = okHttpClient

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("EagleSports/1.0 (AudioEngine)")
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        // Match the main player's live buffer so brief network jitter is less likely
        // to starve the external audio stream and coincide with video stalls.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                12000, // minBufferMs
                25000, // maxBufferMs
                1500,  // bufferForPlaybackMs
                5000   // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        // Give transient segment/request failures more opportunities to recover,
        // without touching or recreating the independent video player.
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(8))

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()

        // External audio may come from a regular live IPTV channel URL. Disable
        // video track selection so this secondary player does not decode/render
        // a second video stream while the main player continues showing video.
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
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
                    Player.STATE_READY -> {
                        retryAttempts = 0
                        _status.value = if (player.playWhenReady) {
                            PlaybackStatus.PLAYING
                        } else {
                            PlaybackStatus.PAUSED
                        }
                    }
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
                // Retry only the selected external audio. Never interact with the video engine.
                scheduleRetryOrFail(player, error)
            }
        })

        audioPlayer = player
    }

    fun playExternalAudio(audio: ExternalAudioEntity) {
        val uri = Uri.parse(audio.streamUrl)
        val scheme = uri.scheme?.lowercase()
        if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) {
            cancelPendingRetry()
            currentAudio = null
            _errorMessage.value = "Invalid external audio URL"
            _status.value = PlaybackStatus.ERROR
            return
        }

        cancelPendingRetry()
        operationId++
        currentAudio = audio
        retryAttempts = 0
        _errorMessage.value = null
        _status.value = PlaybackStatus.PREPARING
        val requestedOperation = operationId

        scope.launch(Dispatchers.Main) {
            if (requestedOperation != operationId || currentAudio?.stableId != audio.stableId) {
                return@launch
            }
            val player = audioPlayer ?: run {
                _errorMessage.value = "External audio unavailable"
                _status.value = PlaybackStatus.ERROR
                return@launch
            }

            try {
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(audio.stableId)
                    .build()
                player.stop()
                player.clearMediaItems()
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unable to start external audio"
                _status.value = PlaybackStatus.ERROR
            }
        }
    }

    private fun scheduleRetryOrFail(player: ExoPlayer, error: PlaybackException) {
        val failedAudio = currentAudio
        if (failedAudio == null || audioPlayer !== player ||
            player.currentMediaItem?.mediaId != failedAudio.stableId
        ) {
            return
        }

        if (retryAttempts >= maxRetryAttempts) {
            _errorMessage.value = error.message ?: "External audio stream unavailable"
            _status.value = PlaybackStatus.ERROR
            return
        }

        val retryIndex = retryAttempts
        retryAttempts++
        val expectedOperation = operationId
        _status.value = PlaybackStatus.PREPARING
        _errorMessage.value = "External audio interrupted; retrying (${retryAttempts}/$maxRetryAttempts)"

        retryJob?.cancel()
        retryJob = scope.launch(Dispatchers.Main) {
            delay(retryDelaysMs[retryIndex])
            if (expectedOperation != operationId ||
                currentAudio?.stableId != failedAudio.stableId ||
                audioPlayer !== player ||
                player.currentMediaItem?.mediaId != failedAudio.stableId
            ) {
                return@launch
            }

            try {
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unable to retry external audio"
                _status.value = PlaybackStatus.ERROR
            }
        }
    }

    private fun cancelPendingRetry() {
        retryJob?.cancel()
        retryJob = null
    }

    /** Stop external audio without resetting or interrupting the video player. */
    fun stopExternalAudio() {
        operationId++
        cancelPendingRetry()
        retryAttempts = 0
        currentAudio = null
        _status.value = PlaybackStatus.IDLE
        _errorMessage.value = null
        scope.launch(Dispatchers.Main) {
            try {
                audioPlayer?.stop()
                audioPlayer?.clearMediaItems()
            } catch (_: Exception) {
                // Stopping audio must not affect video playback.
            }
        }
    }

    fun togglePlayPause() {
        try {
            audioPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Unable to control external audio"
            _status.value = PlaybackStatus.ERROR
        }
    }

    /** Manual delay adjustments are limited to -3000ms..+3000ms. */
    fun adjustDelay(deltaMs: Int) {
        val newDelay = (_delayMs.value + deltaMs).coerceIn(-3000, 3000)
        setDelay(newDelay)
    }

    fun setDelay(delay: Int) {
        val clamped = delay.coerceIn(-3000, 3000)
        val previousDelay = _delayMs.value
        _delayMs.value = clamped

        // Apply only the change from the previous manual setting, not the full
        // absolute offset on every button press. Video is never touched.
        val positionDelta = clamped - previousDelay
        if (positionDelta == 0) return

        try {
            audioPlayer?.let { player ->
                if (player.isCurrentMediaItemSeekable) {
                    val targetPosition = (player.currentPosition + positionDelta).coerceAtLeast(0L)
                    player.seekTo(targetPosition)
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Unable to adjust external audio delay"
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        try {
            audioPlayer?.volume = clamped
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Unable to change external audio volume"
        }
    }

    fun release() {
        operationId++
        cancelPendingRetry()
        currentAudio = null
        retryAttempts = 0
        _status.value = PlaybackStatus.RELEASED
        try {
            audioPlayer?.release()
        } finally {
            audioPlayer = null
            audioHttpClient?.let { client ->
                client.connectionPool.evictAll()
                client.dispatcher.cancelAll()
                client.dispatcher.executorService.shutdown()
            }
            audioHttpClient = null
        }
    }
}
