package org.example.mp3player.domain

data class UserAlbum(
    val id: Long,
    val title: String,
    val description: String,
    val coverUri: String?,
    val createdAt: Long,
    val trackIds: List<String>,
)
