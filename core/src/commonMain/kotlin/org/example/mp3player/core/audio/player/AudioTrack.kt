package org.example.mp3player.core.audio.player

data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String,
    val path: String,
    val duration: Long,
    val coverUri: String? = null,
)
