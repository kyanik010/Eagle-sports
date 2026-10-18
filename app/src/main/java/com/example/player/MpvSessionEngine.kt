package com.example.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.annotation.MainThread
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.io.File

/**
 * Single-session libmpv playback primitive for live video plus a separately selected
 * commentary stream. Delay is applied only when the user presses a delay control;
 * this class deliberately contains no automatic synchronization logic.
 *
 * The player surface and MPV instance share one playback session, so audio-add does
 * not create a second clock or a second independent live buffer.
 */
class MpvSessionEngine(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val mpv = MPV(
        appContext,
        File(appContext.filesDir, "eagle-mpv").absolutePath,
        File(appContext.cacheDir, "eagle-mpv").absolutePath
    )

    private val _manualDelayMs = MutableStateFlow(0)
    val manualDelayMs: StateFlow<Int> = _manualDelayMs.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _audioError = MutableStateFlow<String?>(null)
    val audioError: StateFlow<String?> = _audioError.asStateFlow()

    private val _status = MutableStateFlow(PlaybackStatus.IDLE)
    val status: StateFlow<PlaybackStatus> = _status.asStateFlow()

    private val _audioStatus = MutableStateFlow(PlaybackStatus.IDLE)
    val audioStatus: StateFlow<PlaybackStatus> = _audioStatus.asStateFlow()

    private var surfaceAttached = false
    private var activeVideoUrl: String? = null
    private var activeAudioUrl: String? = null
    private var released = false

    init {
        // Conservative live-stream profile: bounded demux cache avoids unbounded
        // memory growth while absorbing short network jitter.
        runCatching {
            mpv.setOptionString("profile", "fast")
            mpv.setOptionString("hwdec", "auto")
            mpv.setOptionString("msg-level", "all=warn")
            mpv.setOptionString("tls-verify", "yes")
            mpv.setOptionString("demuxer-max-bytes", "64MiB")
            mpv.setOptionString("demuxer-max-back-bytes", "16MiB")
            mpv.setOptionString("keep-open", "yes")
            mpv.setOptionString("audio-fallback-to-null", "yes")
            mpv.setPropertyString("vo", "gpu")
            mpv.setPropertyBoolean("pause", true)
        }.onFailure {
            _error.value = "تعذر تهيئة محرك libmpv: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    /** Create a view which attaches/detaches its surface to this MPV session. */
    @MainThread
    fun createVideoView(context: Context): View {
        check(!released) { "MpvSessionEngine is released" }
        return object : SurfaceView(context), SurfaceHolder.Callback {
            init {
                holder.addCallback(this)
                setBackgroundColor(android.graphics.Color.BLACK)
            }

            override fun surfaceCreated(holder: SurfaceHolder) {
                if (released || surfaceAttached) return
                runCatching {
                    mpv.attachSurface(holder.surface)
                    mpv.setPropertyString("vo", "gpu")
                    mpv.setPropertyBoolean("force-window", true)
                    surfaceAttached = true
                    activeVideoUrl?.let { loadVideo(it) }
                }.onFailure {
                    _error.value = "تعذر تشغيل سطح الفيديو: ${it.message ?: it.javaClass.simpleName}"
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                if (!released && mpv.isInitialized) {
                    runCatching { mpv.setPropertyString("android-surface-size", "${width}x${height}") }
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (!released && surfaceAttached) {
                    runCatching {
                        mpv.setPropertyString("vo", "null")
                        mpv.setPropertyBoolean("force-window", false)
                        mpv.detachSurface()
                    }
                    surfaceAttached = false
                }
            }
        }
    }

    @MainThread
    fun playVideo(url: String) {
        if (released) return
        if (!isHttpUrl(url)) {
            _error.value = "رابط الفيديو غير صالح"
            return
        }
        activeVideoUrl = url
        activeAudioUrl = null
        _manualDelayMs.value = 0
        _audioStatus.value = PlaybackStatus.IDLE
        _audioError.value = null
        _status.value = PlaybackStatus.PREPARING
        _error.value = null
        runCatching {
            mpv.setPropertyBoolean("pause", true)
            mpv.command("loadfile", url, "replace")
            mpv.setPropertyString("aid", "auto")
            mpv.setPropertyDouble("audio-delay", 0.0)
            mpv.setPropertyBoolean("pause", false)
            _status.value = PlaybackStatus.PLAYING
        }.onFailure {
            _status.value = PlaybackStatus.ERROR
            _error.value = "تعذر فتح البث: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    /**
     * Adds commentary to the currently playing video session and explicitly selects
     * the newly added track. No independent audio player and no auto-sync loop.
     */
    @MainThread
    fun addExternalAudio(url: String) {
        if (released) return
        if (activeVideoUrl == null) {
            _error.value = "شغّل الفيديو أولاً ثم أضف التعليق الخارجي"
            return
        }
        if (!isHttpUrl(url)) {
            _error.value = "رابط الصوت الخارجي غير صالح"
            return
        }
        activeAudioUrl = url
        _audioStatus.value = PlaybackStatus.PREPARING
        _audioError.value = null
        _error.value = null
        runCatching {
            mpv.command("audio-add", url, "select")
            // Reapply the user's explicit value after adding a late audio track;
            // this is not an automatic sync calculation.
            mpv.setPropertyDouble("audio-delay", _manualDelayMs.value / 1000.0)
            mpv.setPropertyBoolean("pause", false)
            _audioStatus.value = PlaybackStatus.PLAYING
        }.onFailure {
            activeAudioUrl = null
            _audioStatus.value = PlaybackStatus.ERROR
            _audioError.value = "تعذر إضافة التعليق الخارجي: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    /** Manual audio offset only. Positive values delay commentary; negative values advance it. */
    @MainThread
    fun setManualDelayMs(requestedMs: Int) {
        if (released) return
        if (activeAudioUrl == null) {
            _audioError.value = "اختر التعليق الخارجي قبل ضبط المزامنة"
            return
        }
        val clamped = requestedMs.coerceIn(-3000, 3000)
        runCatching {
            mpv.setPropertyDouble("audio-delay", clamped / 1000.0)
            _manualDelayMs.value = clamped
            _audioError.value = null
        }.onFailure {
            _audioError.value = "تعذر تطبيق التأخير اليدوي: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    @MainThread
    fun adjustManualDelay(deltaMs: Int) = setManualDelayMs(_manualDelayMs.value + deltaMs)

    @MainThread
    fun clearExternalAudio() {
        if (released) return
        activeAudioUrl = null
        _manualDelayMs.value = 0
        _audioStatus.value = PlaybackStatus.IDLE
        _audioError.value = null
        runCatching {
            // Reload the current channel to remove added tracks deterministically.
            // Keep playback in the same session; never leave the previous commentary alive.
            activeVideoUrl?.let { url ->
                mpv.command("loadfile", url, "replace")
                mpv.setPropertyString("aid", "auto")
                mpv.setPropertyBoolean("pause", false)
            } ?: mpv.command("stop")
            mpv.setPropertyDouble("audio-delay", 0.0)
            _error.value = null
        }.onFailure {
            _error.value = "تعذر الرجوع لصوت القناة: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    @MainThread
    fun togglePlayPause() {
        if (released) return
        runCatching {
            val paused = mpv.getPropertyBoolean("pause") ?: false
            mpv.setPropertyBoolean("pause", !paused)
            _status.value = if (paused) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
        }.onFailure {
            _error.value = "تعذر التحكم بالتشغيل: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    @MainThread
    fun stop() {
        if (released) return
        activeVideoUrl = null
        activeAudioUrl = null
        _manualDelayMs.value = 0
        _status.value = PlaybackStatus.IDLE
        _audioStatus.value = PlaybackStatus.IDLE
        runCatching { mpv.command("stop") }
    }

    private fun loadVideo(url: String) {
        if (released) return
        runCatching {
            mpv.command("loadfile", url, "replace")
            mpv.setPropertyString("aid", "auto")
            mpv.setPropertyDouble("audio-delay", 0.0)
            mpv.setPropertyBoolean("pause", false)
            _status.value = PlaybackStatus.PLAYING
        }.onFailure {
            _status.value = PlaybackStatus.ERROR
            _error.value = "تعذر تحميل الفيديو: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun isHttpUrl(value: String): Boolean =
        runCatching {
            val uri = android.net.Uri.parse(value)
            uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)

    override fun close() {
        if (released) return
        // Detach native rendering before destroying the libmpv instance.
        if (surfaceAttached) runCatching { mpv.detachSurface() }
        released = true
        activeVideoUrl = null
        activeAudioUrl = null
        surfaceAttached = false
        _status.value = PlaybackStatus.RELEASED
        _audioStatus.value = PlaybackStatus.RELEASED
        runCatching { mpv.close() }
    }
}
