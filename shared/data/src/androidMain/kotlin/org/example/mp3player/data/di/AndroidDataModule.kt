package org.example.mp3player.data.di

import org.example.mp3player.core.audio.player.AudioPlayer
import org.example.mp3player.core.audio.scanner.MusicScanner
import org.example.mp3player.data.database.AppDatabase
import org.example.mp3player.data.database.dao.UserAlbumsDao
import org.example.mp3player.data.repository.UserAlbumsRepositoryImpl
import org.example.mp3player.domain.repository.UserAlbumsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val androidDataModule = module {
    single { AppDatabase.build(androidContext()) }
    single<UserAlbumsDao> { get<AppDatabase>().userAlbumsDao() }

    singleOf(::MusicScanner)

    // У AudioPlayer второй параметр конструктора (scope) имеет значение по умолчанию,
    // а singleOf его не понимает и полез бы искать CoroutineScope в графе.
    single { AudioPlayer(androidContext()) }

    // То же самое: у UserAlbumsRepositoryImpl параметр clock со значением по умолчанию.
    single<UserAlbumsRepository> { UserAlbumsRepositoryImpl(get(), get()) }
}
