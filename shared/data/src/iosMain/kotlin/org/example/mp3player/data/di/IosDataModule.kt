package org.example.mp3player.data.di

import org.example.mp3player.data.MusicScanner
import org.example.mp3player.data.player.AudioPlayer
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val iosDataModule = module {
    singleOf(::MusicScanner)
    singleOf(::AudioPlayer)
}