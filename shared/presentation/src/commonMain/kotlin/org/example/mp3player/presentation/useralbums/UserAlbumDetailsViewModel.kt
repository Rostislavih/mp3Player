package org.example.mp3player.presentation.useralbums

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
import org.example.mp3player.domain.repository.UserAlbumsRepository

class UserAlbumDetailsViewModel(
    private val albumId: Long,
    userAlbumsRepository: UserAlbumsRepository,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    private val _effects = MutableSharedFlow<UserAlbumDetailsEffect>()
    val effects: SharedFlow<UserAlbumDetailsEffect> = _effects.asSharedFlow()

    val state: StateFlow<UserAlbumDetailsUiState> = combine(
        userAlbumsRepository.observeById(albumId),
        userAlbumsRepository.observeTracksOfAlbum(albumId),
    ) { album, tracks ->
        if (album == null) {
            UserAlbumDetailsUiState.Error("Альбом не найден")
        } else {
            UserAlbumDetailsUiState.Content(
                title = album.title,
                description = album.description,
                coverUri = album.coverUri,
                tracks = tracks,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserAlbumDetailsUiState.Loading,
    )

    fun onEvent(event: UserAlbumDetailsEvent) {
        when (event) {
            UserAlbumDetailsEvent.PlayAll -> play(0)
            is UserAlbumDetailsEvent.PlayTrack -> play(event.index)
        }
    }

    private fun play(index: Int) {
        val content = state.value as? UserAlbumDetailsUiState.Content ?: return
        if (index !in content.tracks.indices) return
        audioPlayer.play(content.tracks, startIndex = index)
        viewModelScope.launch { _effects.emit(UserAlbumDetailsEffect.OpenPlayer) }
    }
}
