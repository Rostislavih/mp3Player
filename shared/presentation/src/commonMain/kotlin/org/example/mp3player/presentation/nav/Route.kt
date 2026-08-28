package org.example.mp3player.presentation.nav

import kotlinx.serialization.Serializable

sealed interface Route

@Serializable data object TracksRoute: Route
@Serializable data object AlbumsRoute: Route
@Serializable data class AlbumDetailsRoute(val albumId: String) : Route

@Serializable data object UserAlbumsRoute: Route
@Serializable data class UserAlbumDetailsRoute(val albumId: Long): Route
@Serializable data object PlayerRoute : Route
