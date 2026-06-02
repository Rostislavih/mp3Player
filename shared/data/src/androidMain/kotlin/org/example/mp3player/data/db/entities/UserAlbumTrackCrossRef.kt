package org.example.mp3player.data.db.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.Relation

@Entity(
    tableName = "user_album_track_cross_ref",
    primaryKeys = ["albumId", "trackId"],
    indices = [Index("albumId"), Index("trackId")]
)
data class UserAlbumTrackCrossRef(
    val albumId: Long,
    val trackId: String,
    val position: Int
)

data class UserAlbumWithTrackIds(
    @Embedded val album: UserAlbumEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "trackId",
        associateBy = Junction(
            value = UserAlbumTrackCrossRef::class,
            parentColumn = "albumId",
            entityColumn = "trackId",
        ),
    )
    val refs: List<UserAlbumTrackCrossRef>
){
    val orderedTrackIds: List<String>
        get() = refs.sortedBy { it.position }.map { it.trackId }
}