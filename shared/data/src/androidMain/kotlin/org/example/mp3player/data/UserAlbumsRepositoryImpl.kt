package org.example.mp3player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.example.mp3player.data.db.dao.UserAlbumsDao
import org.example.mp3player.data.db.entities.UserAlbumEntity
import org.example.mp3player.data.db.entities.UserAlbumTrackCrossRef
import org.example.mp3player.data.db.entities.UserAlbumWithTrackIds
import org.example.mp3player.domain.UserAlbum
import org.example.mp3player.domain.UserAlbumsRepository

class UserAlbumsRepositoryImpl(
    private val dao: UserAlbumsDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : UserAlbumsRepository {

    override fun observeAll(): Flow<List<UserAlbum>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<UserAlbum?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun create(title: String, description: String, coverUri: String?): Long =
        dao.insertAlbum(
            UserAlbumEntity(
                title = title,
                description = description,
                coverUri = coverUri,
                createdAt = clock(),
            )
        )

    override suspend fun rename(id: Long, newTitle: String) {
        val current = dao.observeById(id).firstValue() ?: return
        dao.updateAlbum(current.album.copy(title = newTitle))
    }

    override suspend fun setCover(id: Long, coverUri: String?) {
        val current = dao.observeById(id).firstValue() ?: return
        dao.updateAlbum(current.album.copy(coverUri = coverUri))
    }

    override suspend fun delete(id: Long) {
        dao.deleteAlbum(id)
    }

    override suspend fun addTrack(albumId: Long, trackId: String) {
        val nextPosition = dao.maxPosition(albumId) + 1
        dao.insertCrossRef(UserAlbumTrackCrossRef(albumId, trackId, nextPosition))
    }

    override suspend fun removeTrack(albumId: Long, trackId: String) {
        dao.deleteCrossRef(albumId, trackId)
    }

    override suspend fun reorderTracks(albumId: Long, trackIds: List<String>) {
        dao.reorderTracks(albumId, trackIds)
    }

    private fun UserAlbumWithTrackIds.toDomain(): UserAlbum = UserAlbum(
        id = album.id,
        title = album.title,
        description = album.description,
        coverUri = album.coverUri,
        createdAt = album.createdAt,
        trackIds = orderedTrackIds
    )
}

private suspend fun <T> Flow<T>.firstValue(): T = first()
