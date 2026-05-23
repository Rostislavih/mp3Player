package org.example.mp3player.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.example.mp3player.data.db.entities.UserAlbumEntity
import org.example.mp3player.data.db.entities.UserAlbumTrackCrossRef
import org.example.mp3player.data.db.entities.UserAlbumWithTrackIds

@Dao
interface UserAlbumsDao {

    //observe all albums
    @Transaction
    @Query("SELECT * FROM user_albums ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<UserAlbumWithTrackIds>>

    @Transaction
    @Query("SELECT * FROM user_albums WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<UserAlbumWithTrackIds?>

    //CRUD operations
    @Insert
    suspend fun insertAlbum(album: UserAlbumEntity): Long

    @Update
    suspend fun updateAlbum(album: UserAlbumEntity)

    @Query("DELETE FROM user_albums WHERE id = :id")
    suspend fun deleteAlbum(id: Long)

    @Query("SELECT COALESCE(MAX(position),-1) FROM user_album_track_cross_ref WHERE albumId = :albumId")
    suspend fun maxPosition(albumId: Long): Int

    //cross ref operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: UserAlbumTrackCrossRef)

    @Query("DELETE FROM user_album_track_cross_ref WHERE albumId = :albumId AND trackId = :trackId")
    suspend fun deleteCrossRef(albumId: Long, trackId: String)

    @Query("DELETE FROM user_album_track_cross_ref WHERE albumId = :albumId ")
    suspend fun deleteAllRefs(albumId: Long,)

    @Insert
    suspend fun insertCrossRefs(refs: List<UserAlbumTrackCrossRef>)

    //@Transaction reorderTracks (стереть все cross-ref альбома и пересоздать в новом порядке)
    @Transaction
    suspend fun reorderTracks(albumId: Long, trackIds: List<String>) {
        deleteAllRefs(albumId)
        insertCrossRefs(
            trackIds.mapIndexed { index, trackId ->
                UserAlbumTrackCrossRef(albumId, trackId, index)
            }
        )
    }
}