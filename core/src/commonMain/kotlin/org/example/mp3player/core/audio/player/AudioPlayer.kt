package org.example.mp3player.core.audio.player

import kotlinx.coroutines.flow.StateFlow

expect class AudioPlayer {
    val state: StateFlow<PlaybackState>
    fun play(queue: List<AudioTrack>, startIndex: Int = 0)
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