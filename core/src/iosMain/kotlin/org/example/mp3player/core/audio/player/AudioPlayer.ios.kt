package org.example.mp3player.core.audio.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class AudioPlayer {
    actual val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())

    actual fun play(queue: List<AudioTrack>, startIndex: Int) { TODO("iOS: AVAudioPlayer / MPMusicPlayerController") }
    actual fun resume() { TODO("iOS") }
    actual fun pause() { TODO("iOS") }
    actual fun toggle() { TODO("iOS") }
    actual fun seekTo(positionMs: Long) { TODO("iOS") }
    actual fun next() { TODO("iOS") }
    actual fun previous() { TODO("iOS") }
    actual fun setRepeatMode(mode: RepeatMode) { TODO("iOS") }
    actual fun setShuffleModeEnabled(enabled: Boolean) { TODO("iOS") }
    actual fun release() {}
}
