package org.example.mp3player.domain

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffleEnabled: Boolean = false,
)

enum class RepeatMode {
Off, One, All
}

