package org.example.mp3player.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.example.mp3player.core.audio.player.AudioPlayer
import org.example.mp3player.core.audio.player.PlaybackState

class PlayerViewModel(
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = audioPlayer.state
        .map { it.toUi() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerUiState(),
        )

    fun onEvent(event: PlayerEvent) {
        when (event) {
            PlayerEvent.PlayPause -> audioPlayer.toggle()
            PlayerEvent.Next -> audioPlayer.next()
            PlayerEvent.Previous -> audioPlayer.previous()
            is PlayerEvent.SeekTo -> audioPlayer.seekTo(event.positionMs)
            is PlayerEvent.SeekToFraction -> {
                val duration = state.value.durationMs
                if (duration > 0) {
                    audioPlayer.seekTo((duration * event.fraction).toLong())
                }
            }
        }
    }

    private fun PlaybackState.toUi(): PlayerUiState {
        val track = currentTrack
        val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
        return PlayerUiState(
            title = track?.title.orEmpty(),
            artist = track?.artist.orEmpty(),
            coverUri = track?.coverUri,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            progress = progress.coerceIn(0f, 1f),
            hasTrack = track != null,
        )
    }
}
