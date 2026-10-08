package com.example.player

enum class PlaybackStatus {
    IDLE,
    PREPARING,
    BUFFERING,
    READY,
    PLAYING,
    PAUSED,
    RECONNECTING,
    ERROR,
    RELEASED
}

sealed class AudioSourceMode {
    object Original : AudioSourceMode()
    data class External(val audioId: String, val audioTitle: String) : AudioSourceMode()
}
