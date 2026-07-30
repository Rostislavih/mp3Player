package org.example.mp3player.presentation.player

sealed interface PlayerEvent {
    data object PlayPause : PlayerEvent
    data object Next : PlayerEvent
    data object Previous : PlayerEvent
    data class SeekTo(val positionMs: Long) : PlayerEvent
    data class SeekToFraction(val fraction: Float) : PlayerEvent
}
