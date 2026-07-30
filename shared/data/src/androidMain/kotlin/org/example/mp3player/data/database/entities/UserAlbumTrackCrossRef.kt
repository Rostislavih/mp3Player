package org.example.mp3player.data.database.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "user_album_track_cross_ref",
    primaryKeys = ["albumId", "trackId"],
    indices = [Index("albumId"), Index("trackId")],
)
data class UserAlbumTrackCrossRef(
    val albumId: Long,
    val trackId: String,
    val position: Int,       // порядок в альбоме
)
