package org.example.mp3player.presentation.albums

import org.example.mp3player.domain.model.Album

sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data object Empty : AlbumsUiState
    data class Content(val albums: List<Album>) : AlbumsUiState
}
