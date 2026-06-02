package org.example.mp3player.data.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.mp3player.domain.PlaybackState
import org.example.mp3player.domain.RepeatMode
import org.example.mp3player.domain.Track

actual class AudioPlayer {
    actual val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())

    actual fun play(queue: List<Track>, startIndex: Int) {TODO("iOS: AVAudioPlayer / MPMusicPlayerController")}
    actual fun resume() {TODO("iOS: AVAudioPlayer / MPMusicPlayerController")}
    actual fun pause() {TODO("iOS: AVAudioPlayer / MPMusicPlayerController")}
    actual fun toggle() {TODO("iOS: AVAudioPlayer / MPMusicPlayerController")}
    actual fun seekTo(positionMs: Long) {TODO("iOS: AVAudioPlayer / MPMusicPlayerController")}
    actual fun next() {TODO("iOS: AVAudioPlayer / MPMusicPlayerController")}
    actual fun previous() {TODO("iOS: AVAudioPlayer / MPMusicPlayerController")}
    actual fun setRepeatMode(mode: RepeatMode) {TODO("iOS: AVAudioPlayer / MPMusicPlayerController")}
    actual fun setShuffleModeEnabled(enabled: Boolean) {TODO("iOS: AVAudioPlayer / MPMusicPlayerController")}
    actual fun release() {TODO("iOS: AVAudioPlayer / MPMusicPlayerController")}

}