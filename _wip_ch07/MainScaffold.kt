package org.example.mp3player.presentation.root

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.example.mp3player.presentation.navigation.*
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.tab_albums
import org.example.mp3player.presentation.resources.tab_my_albums
import org.example.mp3player.presentation.resources.tab_tracks
import org.jetbrains.compose.resources.stringResource


@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (currentDestination.isTopLevel()) {
                NavigationBar {
                    BottomItems.forEach { item ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.hasRoute(item.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) navController.navigate(item.route) {
                                    popUpTo(TracksRoute) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AppNavHost(
                navController = navController,
                onSnackbar = { msg ->
                }
            )
        }
    }
}

private data class BottomItem(
    val route: Route,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelRes: org.jetbrains.compose.resources.StringResource,
)

private val BottomItems = listOf(
    BottomItem(TracksRoute, Icons.Default.MusicNote, Res.string.tab_tracks),
    BottomItem(AlbumsRoute, Icons.Default.Album, Res.string.tab_albums),
    BottomItem(UserAlbumsRoute, Icons.Default.PlaylistPlay, Res.string.tab_my_albums),
)

private fun androidx.navigation.NavDestination?.isTopLevel(): Boolean {
    if (this == null) return false
    return hasRoute(TracksRoute::class) || hasRoute(AlbumsRoute::class) || hasRoute(UserAlbumsRoute::class)
}