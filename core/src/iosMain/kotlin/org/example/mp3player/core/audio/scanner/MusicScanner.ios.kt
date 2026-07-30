package org.example.mp3player.core.audio.scanner

import org.example.mp3player.core.audio.player.AudioTrack

actual class MusicScanner {
    actual suspend fun scanTracks(): List<AudioTrack> {
        TODO("iOS implementation: использовать MPMediaQuery.songs()")
    }
}
