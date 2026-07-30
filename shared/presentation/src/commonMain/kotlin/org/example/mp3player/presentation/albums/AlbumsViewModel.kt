package org.example.mp3player.presentation.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.example.mp3player.domain.repository.AlbumsRepository

class AlbumsViewModel(
    albumsRepository: AlbumsRepository,
) : ViewModel() {

    val state: StateFlow<AlbumsUiState> = albumsRepository.observeAlbums()
        .map { albums ->
            if (albums.isEmpty()) AlbumsUiState.Empty else AlbumsUiState.Content(albums)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlbumsUiState.Loading,
        )
}
