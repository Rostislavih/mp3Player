package org.example.mp3player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.example.mp3player.core.audio.player.AudioTrack
import org.example.mp3player.domain.model.Album

interface AlbumsRepository {
    fun observeAlbums(): Flow<List<Album>>
    fun observeTracksOfAlbum(albumId: String): Flow<List<AudioTrack>>
}
