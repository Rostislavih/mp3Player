package org.example.mp3player.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.example.mp3player.presentation.albums.AlbumsScreen
import org.example.mp3player.presentation.tracks.TracksScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    onSnackbar: (String) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = TracksRoute,
    ) {
        composable<TracksRoute> {
            TracksScreen(
                onOpenPlayer = { navController.navigate(Player) },
                snackbar = onSnackbar,
            )
        }
        composable<AlbumsRoute> {
            AlbumsScreen(
                onAlbumClick = { albumId ->
                    navController.navigate(AlbumDetails(albumId))
                },
            )
        }
        composable<UserAlbumsRoute> {
            UserAlbumsScreen(
                onAlbumClick = { id ->
                    navController.navigate(UserAlbumDetails(id))
                },
            )
        }
        composable<AlbumDetails> { backStackEntry ->
            val args: AlbumDetails = backStackEntry.toRoute()
            AlbumDetailsScreen(
                albumId = args.albumId,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate(Player) },
            )
        }
        composable<Player> {
            PlayerScreen(
                onClose = { navController.popBackStack() },
            )
        }
    }
}