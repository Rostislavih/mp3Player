package org.example.mp3player.core.audio.scanner

import org.example.mp3player.core.audio.player.AudioTrack

expect class MusicScanner {
    /**
     * Полное сканирование медиатеки.
     * Вызывать в фоновом диспатчере — может быть медленно на устройствах с тысячами треков.
     */
    suspend fun scanTracks(): List<AudioTrack>
}