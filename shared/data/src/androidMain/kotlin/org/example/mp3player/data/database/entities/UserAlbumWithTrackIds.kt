package org.example.mp3player.data.database.entities

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Альбом со списком trackId через junction-таблицу.
 * Room сам сгенерирует JOIN по полям.
 */
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
    val refs: List<UserAlbumTrackCrossRef>,
) {
    /** Упорядоченный список trackId. */
    val orderedTrackIds: List<String>
        get() = refs.sortedBy { it.position }.map { it.trackId }
}
