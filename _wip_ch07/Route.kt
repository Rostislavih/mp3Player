package org.example.mp3player.presentation.navigation

import kotlinx.serialization.Serializable

sealed interface TopLevelRoute {
    @Serializable
    data object Tracks : TopLevelRoute
    @Serializable
    data object Albums : TopLevelRoute
    @Serializable
    data object UserAlbums : TopLevelRoute
}

@Serializable data class AlbumDetails(val albumId: String) : Route
@Serializable data class UserAlbumDetails(val albumId: Long) : Route
@Serializable data object Player : Route

interface Route

val TopLevelRoute.asRoute: Route
    get() = when (this) {
        TopLevelRoute.Tracks -> TracksRoute
        TopLevelRoute.Albums -> AlbumsRoute
        TopLevelRoute.UserAlbums -> UserAlbumsRoute
    }

@Serializable data object TracksRoute : Route
@Serializable data object AlbumsRoute : Route
@Serializable data object UserAlbumsRoute : Route
