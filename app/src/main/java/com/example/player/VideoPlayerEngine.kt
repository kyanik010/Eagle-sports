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
import com.example.data.model.ChannelEntity
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
 * Video-only Media3 engine. Playback setup errors are surfaced in state rather than
 * escaping into the UI thread and terminating the app.
 */
class VideoPlayerEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var exoPlayer: ExoPlayer? = null
    private var reconnectJob: Job? = null
    private var currentChannel: ChannelEntity? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    private val _status = MutableStateFlow(PlaybackStatus.IDLE)
    val status: StateFlow<PlaybackStatus> = _status.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    private val _isMuted = MutableStateFlow(false)
    val volume: StateFlow<Float> = _volume.asStateFlow()
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    init {
        try {
            initPlayer()
        } catch (e: Exception) {
            reportPlaybackError("Player initialization failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun initPlayer() {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("EagleSports/1.0 (Linux; Android)")
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 5000, 1000, 2000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()

        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> if (_status.value != PlaybackStatus.RECONNECTING &&
                        _status.value != PlaybackStatus.ERROR) {
                        _status.value = PlaybackStatus.IDLE
                    }
                    Player.STATE_BUFFERING -> _status.value = PlaybackStatus.BUFFERING
                    Player.STATE_READY -> {
                        reconnectAttempts = 0
                        _status.value = if (player.playWhenReady) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
                    }
                    Player.STATE_ENDED -> _status.value = PlaybackStatus.IDLE
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) _status.value = PlaybackStatus.PLAYING
                else if (_status.value == PlaybackStatus.PLAYING) _status.value = PlaybackStatus.PAUSED
            }

            override fun onPlayerError(error: PlaybackException) {
                _errorMessage.value = error.message ?: error.errorCodeName
                handlePlaybackFailure()
            }
        })
        exoPlayer = player
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun playChannel(channel: ChannelEntity) {
        currentChannel = channel
        reconnectJob?.cancel()
        reconnectAttempts = 0
        _errorMessage.value = null

        val uri = runCatching { Uri.parse(channel.streamUrl) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || uri.toString().isBlank() || scheme !in setOf("http", "https")) {
            reportPlaybackError("Invalid channel stream URL")
            return
        }

        val player = exoPlayer
        if (player == null) {
            reportPlaybackError(_errorMessage.value ?: "Video player is unavailable")
            return
        }

        try {
            _status.value = PlaybackStatus.PREPARING
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(channel.stableId)
                .build()
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            reportPlaybackError("Unable to start channel: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun retryPlayback() {
        currentChannel?.let(::playChannel)
    }

    private fun handlePlaybackFailure() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            _status.value = PlaybackStatus.ERROR
            return
        }
        reconnectAttempts++
        _status.value = PlaybackStatus.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.Main.immediate) {
            delay(2000L * reconnectAttempts)
            val channel = currentChannel ?: return@launch
            val player = exoPlayer ?: return@launch
            try {
                player.setMediaItem(MediaItem.fromUri(Uri.parse(channel.streamUrl)))
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                reportPlaybackError("Reconnect failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun reportPlaybackError(message: String) {
        _errorMessage.value = message
        _status.value = PlaybackStatus.ERROR
    }

    fun togglePlayPause() {
        try {
            exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
        } catch (e: Exception) {
            reportPlaybackError("Playback control failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        runCatching { exoPlayer?.volume = if (muted) 0f else _volume.value }
            .onFailure { reportPlaybackError("Audio control failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        if (!_isMuted.value) {
            runCatching { exoPlayer?.volume = clamped }
                .onFailure { reportPlaybackError("Volume control failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun release() {
        reconnectJob?.cancel()
        _status.value = PlaybackStatus.RELEASED
        runCatching { exoPlayer?.release() }
        exoPlayer = null
    }
}
