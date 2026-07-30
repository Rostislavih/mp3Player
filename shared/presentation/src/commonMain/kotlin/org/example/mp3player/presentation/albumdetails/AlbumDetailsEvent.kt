package org.example.mp3player.presentation.albumdetails

sealed interface AlbumDetailsEvent {
    data object PlayAll : AlbumDetailsEvent
    data class PlayTrack(val index: Int) : AlbumDetailsEvent
}

sealed interface AlbumDetailsEffect {
    data object OpenPlayer : AlbumDetailsEffect
}
