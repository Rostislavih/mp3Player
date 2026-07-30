package org.example.mp3player.presentation.tracks

sealed interface TracksEffect {
    data object OpenPlayer : TracksEffect
    data class ShowMessage(val text: String) : TracksEffect
}
