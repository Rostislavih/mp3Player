package org.example.mp3player.data.di

import org.example.mp3player.data.MusicScanner
import org.example.mp3player.data.UserAlbumsRepositoryImpl
import org.example.mp3player.data.db.AppDatabase
import org.example.mp3player.data.db.dao.UserAlbumsDao
import org.example.mp3player.data.player.AudioPlayer
import org.example.mp3player.domain.UserAlbumsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val androidDataModule = module {
    single { AppDatabase.build(androidContext()) }
    single<UserAlbumsDao> { get<AppDatabase>().userAlbumsDao() }

    singleOf(::MusicScanner)
    singleOf(::AudioPlayer)

    singleOf(::UserAlbumsRepositoryImpl) {bind<UserAlbumsRepository>()}
}