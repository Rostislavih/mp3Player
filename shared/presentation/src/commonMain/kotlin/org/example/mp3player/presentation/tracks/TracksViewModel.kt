package org.example.mp3player.presentation.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.example.mp3player.core.audio.player.AudioPlayer
import org.example.mp3player.domain.repository.TracksRepository
import org.example.mp3player.domain.repository.UserAlbumsRepository
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.add_to_album_done
import org.example.mp3player.presentation.resources.failed
import org.example.mp3player.presentation.resources.failed_to_update
import org.jetbrains.compose.resources.getString

class TracksViewModel(
    private val tracksRepository: TracksRepository,
    private val userAlbumsRepository: UserAlbumsRepository,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _error = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)

    val state: StateFlow<TracksUiState> = combine(
        tracksRepository.observeTracks(),
        _searchQuery,
        _isLoading,
        _error,
    ) { tracks, query, loading, error ->
        when {
            // Ошибка имеет смысл, только когда показать вообще нечего.
            error != null && tracks.isEmpty() -> TracksUiState.Error(error)

            // Первая загрузка: треков ещё нет.
            loading && tracks.isEmpty() -> TracksUiState.Loading

            // Есть список (возможно, пустой после успешного скана) — рисуем контент.
            else -> TracksUiState.Content(
                tracks = tracks,
                searchQuery = query,
                isLoading = loading,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TracksUiState.Loading,
    )

    private val _effects = MutableSharedFlow<TracksEffect>()
    val effects: SharedFlow<TracksEffect> = _effects.asSharedFlow()

    fun onEvent(event: TracksEvent) {
        when (event) {
            TracksEvent.Load -> load()
            TracksEvent.Refresh -> refresh()
            is TracksEvent.Search -> _searchQuery.value = event.query
            is TracksEvent.PlayTrack -> playTrack(event.index)
            is TracksEvent.AddToUserAlbum -> addToAlbum(event.trackId, event.albumId)
        }
    }

    private fun load() {
        // Первая загрузка — делегируем репозиторию, observeTracks сам эмитнет результат.
        refresh()
    }

    private var refreshJob: Job? = null

    private fun refresh() {
        if (refreshJob?.isActive == true) return  // или cancelAndJoin(), если нужен restart

        refreshJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                tracksRepository.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: getString(Res.string.failed_to_update)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun playTrack(index: Int) {
        val content = state.value as? TracksUiState.Content ?: return
        val queue = content.filteredTracks
        if (index !in queue.indices) return
        audioPlayer.play(queue, startIndex = index)
        viewModelScope.launch { _effects.emit(TracksEffect.OpenPlayer) }
    }

    private fun addToAlbum(trackId: String, albumId: Long) {
        viewModelScope.launch {
            runCatching { userAlbumsRepository.addTrack(albumId, trackId) }
                .onSuccess { _effects.emit(TracksEffect.ShowMessage(getString(Res.string.add_to_album_done))) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _effects.emit(TracksEffect.ShowMessage(getString(Res.string.failed) + ": ${e.message}"))
                }
        }
    }
}
