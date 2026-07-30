package org.example.mp3player.presentation.player

import org.example.mp3player.presentation.common.formatDuration

data class PlayerUiState(
    val title: String = "",
    val artist: String = "",
    val coverUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val progress: Float = 0f,
    val hasTrack: Boolean = false,
) {
    val positionText: String get() = formatDuration(positionMs)
    val durationText: String get() = formatDuration(durationMs)
}
