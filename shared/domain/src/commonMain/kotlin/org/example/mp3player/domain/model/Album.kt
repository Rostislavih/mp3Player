package org.example.mp3player.domain.model

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val trackCount: Int,
    val coverUri: String?,
    val totalDurationMs: Long,
)
