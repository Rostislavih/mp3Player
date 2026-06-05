package org.example.mp3player.presentation.di

import org.example.mp3player.domain.TracksRepository
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val presentationModule = module {
    // ViewModel'и пишутся в главе 06. По мере создания каждой —
    // раскомментируй её строку и добавь import (Alt+Enter в Android Studio):
    //
    // viewModelOf(::TracksViewModel)
    // viewModelOf(::AlbumsViewModel)
    // viewModelOf(::AlbumDetailsViewModel)
    // viewModelOf(::PlayerViewModel)
    // viewModelOf(::UserAlbumsViewModel)
}