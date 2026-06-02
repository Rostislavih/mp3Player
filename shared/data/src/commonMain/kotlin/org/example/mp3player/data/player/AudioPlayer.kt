package org.example.mp3player.data.player

import kotlinx.coroutines.flow.StateFlow
import org.example.mp3player.domain.PlaybackState
import org.example.mp3player.domain.RepeatMode
import org.example.mp3player.domain.Track

expect class AudioPlayer {
    val state: StateFlow<PlaybackState>

    fun play(queue: List<Track>, startIndex: Int = 0)
    fun resume()
    fun pause()
    fun toggle()
    fun seekTo(positionMs: Long)
    fun next()
    fun previous()
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffleModeEnabled(enabled: Boolean)

    fun release()
}