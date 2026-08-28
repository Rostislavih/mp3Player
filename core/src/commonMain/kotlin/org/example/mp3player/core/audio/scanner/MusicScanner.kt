package org.example.mp3player.core.audio.scanner

import org.example.mp3player.core.audio.player.AudioTrack

expect class MusicScanner {
    suspend fun scanTracks(): List<AudioTrack>
}