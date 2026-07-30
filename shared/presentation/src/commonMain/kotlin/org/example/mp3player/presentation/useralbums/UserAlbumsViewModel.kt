package org.example.mp3player.presentation.useralbums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.example.mp3player.domain.repository.UserAlbumsRepository

class UserAlbumsViewModel(
    private val repository: UserAlbumsRepository,
) : ViewModel() {

    val state: StateFlow<UserAlbumsUiState> = repository.observeAll()
        .map { albums ->
            if (albums.isEmpty()) UserAlbumsUiState.Empty else UserAlbumsUiState.Content(albums)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserAlbumsUiState.Loading,
        )

    private val _effects = MutableSharedFlow<UserAlbumsEffect>()
    val effects: SharedFlow<UserAlbumsEffect> = _effects.asSharedFlow()

    fun onEvent(event: UserAlbumsEvent) {
        when (event) {
            is UserAlbumsEvent.Create -> create(event.title)
            is UserAlbumsEvent.Delete -> delete(event.id)
        }
    }

    private fun create(title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.create(title = trimmed, description = "", coverUri = null)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _effects.emit(UserAlbumsEffect.ShowMessage("Не удалось создать: ${e.message}"))
            }
        }
    }

    private fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _effects.emit(UserAlbumsEffect.ShowMessage("Не удалось удалить: ${e.message}"))
                }
        }
    }
}
