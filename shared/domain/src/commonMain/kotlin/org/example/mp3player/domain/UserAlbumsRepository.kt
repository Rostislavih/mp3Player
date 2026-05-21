package org.example.mp3player.domain

import kotlinx.coroutines.flow.Flow

interface UserAlbumsRepository {
    fun observeAll(): Flow<List<UserAlbum>>
    fun observeById(id: Long): Flow<UserAlbum?>

    suspend fun create(title: String, description: String, coverUri: String?): Long
    suspend fun rename(id: Long, newTitle: String)
    suspend fun setCover(id: Long, coverUri: String?)
    suspend fun delete(id: Long)

    suspend fun addTrack(albumId: Long, trackId: String)
    suspend fun removeTrack(albumId: Long, trackId: String)
    suspend fun reorderTracks(albumId: Long, trackIds: List<String>)
}
