package org.example.mp3player.presentation.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.example.mp3player.presentation.albumdetails.AlbumDetailsScreen
import org.example.mp3player.presentation.albums.AlbumsScreen
import org.example.mp3player.presentation.player.PlayerScreen
import org.example.mp3player.presentation.tracks.TracksScreen
import org.example.mp3player.presentation.useralbums.UserAlbumsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    onSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TracksRoute,
        modifier = modifier,
    ) {
        composable<TracksRoute> {
            TracksScreen(
                onOpenPlayer = { navController.navigate(PlayerRoute) },
                onSnackbar = onSnackbar
            )
        }
        composable<AlbumsRoute> {
            AlbumsScreen(
                onAlbumClick = { albumId ->
                    navController.navigate(AlbumDetailsRoute(albumId))
                },
            )
        }
        composable<UserAlbumsRoute> {
            UserAlbumsScreen(
                onSnackbar = onSnackbar,
            )
        }
        composable<AlbumDetailsRoute> { backStackEntry ->
            val args: AlbumDetailsRoute = backStackEntry.toRoute()
            AlbumDetailsScreen(
                albumId = args.albumId,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate(PlayerRoute) },
            )
        }
        composable<PlayerRoute> {
            PlayerScreen(
                onClose = { navController.popBackStack() }
            )
        }
    }
}