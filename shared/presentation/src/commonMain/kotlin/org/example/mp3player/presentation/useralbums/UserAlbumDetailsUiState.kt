package org.example.mp3player.presentation.useralbums

import org.example.mp3player.core.audio.player.AudioTrack

sealed interface UserAlbumDetailsUiState {
    data object Loading : UserAlbumDetailsUiState
    data class Error(val errorText: String) : UserAlbumDetailsUiState
    data class Content(
        val title: String,
        val description: String,
        val coverUri: String?,
        val tracks: List<AudioTrack>,
    ) : UserAlbumDetailsUiState
}
