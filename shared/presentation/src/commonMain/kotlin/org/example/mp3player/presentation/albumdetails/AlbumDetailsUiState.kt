package org.example.mp3player.presentation.albumdetails

import org.example.mp3player.core.audio.player.AudioTrack
import org.example.mp3player.domain.model.Album

sealed interface AlbumDetailsUiState {
    data object Loading : AlbumDetailsUiState
    data class Error(val errorText: String) : AlbumDetailsUiState
    data class Content(
        val album: Album,
        val tracks: List<AudioTrack>,
    ) : AlbumDetailsUiState
}
