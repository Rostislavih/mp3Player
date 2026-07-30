package org.example.mp3player.presentation.albumdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.example.mp3player.core.audio.player.AudioPlayer
import org.example.mp3player.domain.repository.AlbumsRepository

class AlbumDetailsViewModel(
    private val albumId: String,
    albumsRepository: AlbumsRepository,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    private val _effects = MutableSharedFlow<AlbumDetailsEffect>()
    val effects: SharedFlow<AlbumDetailsEffect> = _effects.asSharedFlow()

    val state: StateFlow<AlbumDetailsUiState> = combine(
        albumsRepository.observeAlbums(),
        albumsRepository.observeTracksOfAlbum(albumId),
    ) { albums, tracks ->
        val album = albums.firstOrNull { it.id == albumId }
        if (album == null) {
            AlbumDetailsUiState.Error("Альбом не найден")
        } else {
            AlbumDetailsUiState.Content(album = album, tracks = tracks)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumDetailsUiState.Loading,
    )

    fun onEvent(event: AlbumDetailsEvent) {
        when (event) {
            AlbumDetailsEvent.PlayAll -> play(0)
            is AlbumDetailsEvent.PlayTrack -> play(event.index)
        }
    }

    private fun play(index: Int) {
        val content = state.value as? AlbumDetailsUiState.Content ?: return
        if (index !in content.tracks.indices) return
        audioPlayer.play(content.tracks, startIndex = index)
        viewModelScope.launch { _effects.emit(AlbumDetailsEffect.OpenPlayer) }
    }
}
