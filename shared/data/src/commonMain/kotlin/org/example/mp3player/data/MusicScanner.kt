package org.example.mp3player.data

import org.example.mp3player.domain.Track

expect class MusicScanner {
    suspend fun scanTracks(): List<Track>
}