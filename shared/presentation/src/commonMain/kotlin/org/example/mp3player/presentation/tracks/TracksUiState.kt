package org.example.mp3player.presentation.tracks

import org.example.mp3player.core.audio.player.AudioTrack

sealed interface TracksUiState {

    /** Первая загрузка: треков ещё нет, показываем спиннер на весь экран. */
    data object Loading : TracksUiState

    /** Загрузка провалилась и показать нечего. */
    data class Error(val errorText: String) : TracksUiState

    /** Есть что показывать. [isLoading] = идёт фоновое обновление поверх готового списка. */
    data class Content(
        val tracks: List<AudioTrack> = emptyList(),
        val searchQuery: String = "",
        val isLoading: Boolean = false,
    ) : TracksUiState {

        val filteredTracks: List<AudioTrack>
            get() = if (searchQuery.isBlank()) {
                tracks
            } else {
                tracks.filter { track ->
                    track.title.contains(searchQuery, ignoreCase = true) ||
                        track.artist.contains(searchQuery, ignoreCase = true)
                }
            }
    }
}
