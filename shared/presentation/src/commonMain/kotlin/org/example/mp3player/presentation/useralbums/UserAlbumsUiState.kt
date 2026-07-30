package org.example.mp3player.presentation.useralbums

import org.example.mp3player.domain.model.UserAlbum

sealed interface UserAlbumsUiState {
    data object Loading : UserAlbumsUiState
    data object Empty : UserAlbumsUiState
    data class Content(val albums: List<UserAlbum>) : UserAlbumsUiState
}
