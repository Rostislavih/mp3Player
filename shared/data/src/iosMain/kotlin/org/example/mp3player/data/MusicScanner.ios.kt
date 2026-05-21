package org.example.mp3player.data

import org.example.mp3player.domain.Track

actual class MusicScanner {
    actual suspend fun scanTracks(): List<Track> {
        TODO("iOS implementation: использовать MPMediaQuery.songs()")
    }
}