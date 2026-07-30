package org.example.mp3player.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.example.mp3player.core.audio.player.AudioTrack
import org.example.mp3player.domain.model.Album
import org.example.mp3player.domain.repository.AlbumsRepository
import org.example.mp3player.domain.repository.TracksRepository


class AlbumsRepositoryImpl(
    private val tracksRepository: TracksRepository,
) : AlbumsRepository {

    override fun observeAlbums(): Flow<List<Album>> =
        tracksRepository.observeTracks().map { tracks -> groupIntoAlbums(tracks) }

    override fun observeTracksOfAlbum(albumId: String): Flow<List<AudioTrack>> =
        tracksRepository.observeTracks().map { tracks ->
            tracks.filter { it.albumId == albumId }
                .sortedBy { it.title }
        }

    private fun groupIntoAlbums(tracks: List<AudioTrack>): List<Album> =
        tracks
            .groupBy { it.albumId }
            .map { (albumId, items) ->
                val artists = items.map { it.artist }.distinct()
                val artist = if (artists.size == 1) artists.first() else "Various Artists"

                Album(
                    id = albumId,
                    title = items.first().album,
                    artist = artist,
                    trackCount = items.size,
                    coverUri = items.firstOrNull { it.coverUri != null }?.coverUri,
                    totalDurationMs = items.sumOf { it.duration },
                )
            }
            .sortedBy { it.title.lowercase() }

}
