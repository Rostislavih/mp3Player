package org.example.mp3player.presentation.tracks

sealed interface TracksEvent {
    data object Load : TracksEvent
    data object Refresh : TracksEvent
    data class Search(val query: String) : TracksEvent
    data class PlayTrack(val index: Int) : TracksEvent
    data class AddToUserAlbum(val trackId: String, val albumId: Long) : TracksEvent
}