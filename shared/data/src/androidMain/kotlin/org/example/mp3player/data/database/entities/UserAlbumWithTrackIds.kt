package org.example.mp3player.data.database.entities

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Альбом со своими строками связей.
 *
 * Это обычная связь один-ко-многим: user_albums.id -> cross_ref.albumId.
 * Junction здесь НЕ нужен: junction описывает "альбом -> трек" через промежуточную
 * таблицу, а нам нужны сами строки промежуточной таблицы (в них лежит position).
 */
data class UserAlbumWithTrackIds(
    @Embedded val album: UserAlbumEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "albumId",
    )
    val refs: List<UserAlbumTrackCrossRef>,
) {
    /** Упорядоченный список trackId. */
    val orderedTrackIds: List<String>
        get() = refs.sortedBy { it.position }.map { it.trackId }
}
