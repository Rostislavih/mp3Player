package org.example.mp3player.core.audio.scanner

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.mp3player.core.audio.player.AudioTrack

actual class MusicScanner(private val context: Context) {

    actual suspend fun scanTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrack>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf("10000")

        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)

                tracks += AudioTrack(
                    id = id.toString(),
                    title = c.getString(titleCol).orFallback("Без названия"),
                    artist = c.getString(artistCol).orFallback("Неизвестный исполнитель"),
                    album = c.getString(albumCol).orFallback("Неизвестный альбом"),
                    albumId = albumId.toString(),
                    path = c.getString(pathCol).orEmpty(),
                    duration = c.getLong(durationCol),
                    coverUri = albumArtUri(albumId),
                )
            }
        }

        tracks
    }

    private fun albumArtUri(albumId: Long): String =
        "content://media/external/audio/albumart/$albumId"

    private fun String?.orFallback(fallback: String): String =
        if (this.isNullOrBlank()) fallback else this
}
