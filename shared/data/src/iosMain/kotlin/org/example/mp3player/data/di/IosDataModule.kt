package org.example.mp3player.data.di

import org.example.mp3player.core.audio.player.AudioPlayer
import org.example.mp3player.core.audio.scanner.MusicScanner
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val iosDataModule = module {
    singleOf(::MusicScanner)
    singleOf(::AudioPlayer)
    // UserAlbumsRepository — отдельная задача, пока iOS не используем.
}
