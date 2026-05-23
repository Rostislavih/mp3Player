package org.example.mp3player.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_albums")
data class UserAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val coverUri: String? = null,
    val createdAt: Long,
    val trackIds: List<String>,
)
