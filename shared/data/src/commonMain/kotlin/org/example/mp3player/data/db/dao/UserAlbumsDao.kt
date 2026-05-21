package org.example.mp3player.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.example.mp3player.data.db.entities.UserAlbumWithTrackIds

@Dao
interface UserAlbumsDao {

    @Transaction
    @Query("SELECT * FROM user_albums ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<UserAlbumWithTrackIds>>

    @Transaction
    @Query("SELECT * FROM user_albums WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<UserAlbumWithTrackIds?>
}