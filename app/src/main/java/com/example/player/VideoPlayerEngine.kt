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
 * VideoPlayerEngine:
 * Dedicated exclusively to the Video stream and original audio.
 * Uses tuned live buffering without huge latency.
 * Re-uses the existing ExoPlayer instance during channel switching without recreation leaks.
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
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    init {
        initPlayer()
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

        // Custom live buffer:
        // Min buffer: 1.5s, Max buffer: 5s, Buffer for playback: 1s, Buffer after rebuffer: 2s
        // Prevents high latency while keeping live IPTV stable.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1500, // minBufferMs
                5000, // maxBufferMs
                1000, // bufferForPlaybackMs
                2000  // bufferForPlaybackAfterRebufferMs
            )
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
                    Player.STATE_IDLE -> {
                        if (_status.value != PlaybackStatus.RECONNECTING && _status.value != PlaybackStatus.ERROR) {
                            _status.value = PlaybackStatus.IDLE
                        }
                    }
                    Player.STATE_BUFFERING -> {
                        _status.value = PlaybackStatus.BUFFERING
                    }
                    Player.STATE_READY -> {
                        reconnectAttempts = 0
                        _status.value = if (player.playWhenReady) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
                    }
                    Player.STATE_ENDED -> {
                        _status.value = PlaybackStatus.IDLE
                    }
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
                _errorMessage.value = error.message ?: "Stream playback error"
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
        _status.value = PlaybackStatus.PREPARING

        val player = exoPlayer ?: return
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(channel.streamUrl))
            .setMediaId(channel.stableId)
            .build()

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun retryPlayback() {
        currentChannel?.let { playChannel(it) }
    }

    private fun handlePlaybackFailure() {
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            _status.value = PlaybackStatus.RECONNECTING
            reconnectJob?.cancel()
            reconnectJob = scope.launch(Dispatchers.Main) {
                delay(2000L * reconnectAttempts)
                currentChannel?.let {
                    val player = exoPlayer ?: return@let
                    val mediaItem = MediaItem.fromUri(Uri.parse(it.streamUrl))
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playWhenReady = true
                }
            }
        } else {
            _status.value = PlaybackStatus.ERROR
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        exoPlayer?.volume = if (muted) 0f else _volume.value
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        if (!_isMuted.value) {
            exoPlayer?.volume = clamped
        }
    }

    fun release() {
        reconnectJob?.cancel()
        _status.value = PlaybackStatus.RELEASED
        exoPlayer?.release()
        exoPlayer = null
    }
}
