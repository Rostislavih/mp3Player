package org.example.mp3player.presentation.di

import org.example.mp3player.presentation.albumdetails.AlbumDetailsViewModel
import org.example.mp3player.presentation.albums.AlbumsViewModel
import org.example.mp3player.presentation.player.PlayerViewModel
import org.example.mp3player.presentation.tracks.TracksViewModel
import org.example.mp3player.presentation.useralbums.UserAlbumDetailsViewModel
import org.example.mp3player.presentation.useralbums.UserAlbumsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::TracksViewModel)
    viewModelOf(::AlbumsViewModel)
    viewModelOf(::UserAlbumsViewModel)
    viewModelOf(::PlayerViewModel)

    // albumId приходит снаружи, остальное Koin достаёт сам через get()
    viewModel { (albumId: String) -> AlbumDetailsViewModel(albumId, get(), get()) }
    viewModel { (albumId: Long) -> UserAlbumDetailsViewModel(albumId, get(), get()) }
}
