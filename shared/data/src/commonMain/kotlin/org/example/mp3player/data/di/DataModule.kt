package org.example.mp3player.data.di

import org.example.mp3player.data.repository.AlbumsRepositoryImpl
import org.example.mp3player.data.repository.TracksRepositoryImpl
import org.example.mp3player.domain.repository.AlbumsRepository
import org.example.mp3player.domain.repository.TracksRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Общая часть data-модуля.
 * Platform-specific зависимости (Context, Room, ExoPlayer) — в androidDataModule/iosDataModule.
 */
val dataModule = module {
    singleOf(::TracksRepositoryImpl) { bind<TracksRepository>() }
    singleOf(::AlbumsRepositoryImpl) { bind<AlbumsRepository>() }
}
