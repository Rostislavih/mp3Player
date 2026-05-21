package org.example.mp3player.domain

import kotlinx.coroutines.flow.Flow

interface AlbumsRepository {
    fun observeAlbums(): Flow<List<Album>>
    fun observeTracksOfAlbum(albumId: String): Flow<List<Track>>
}
