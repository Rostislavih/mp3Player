package org.example.mp3player.data.di

import org.example.mp3player.data.AlbumsRepositoryImpl
import org.example.mp3player.data.TracksRepositoryImpl
import org.example.mp3player.domain.AlbumsRepository
import org.example.mp3player.domain.TracksRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dataModule = module {
    singleOf(::TracksRepositoryImpl) {bind<TracksRepository>()}
    singleOf(::AlbumsRepositoryImpl) {bind<AlbumsRepository>()}
}