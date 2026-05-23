package org.example.mp3player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.example.mp3player.data.db.dao.UserAlbumsDao
import org.example.mp3player.data.db.entities.UserAlbumEntity
import org.example.mp3player.data.db.entities.UserAlbumTrackCrossRef

@Database(
    entities = [UserAlbumEntity::class, UserAlbumTrackCrossRef::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userAlbumsDao(): UserAlbumsDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context = context.applicationContext,
                klass = AppDatabase::class.java,
                name = "mp3player.db",
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}