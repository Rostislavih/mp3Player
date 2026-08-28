package org.example.mp3player.presentation.root

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.example.mp3player.presentation.nav.AlbumsRoute
import org.example.mp3player.presentation.nav.AppNavHost
import org.example.mp3player.presentation.nav.Route
import org.example.mp3player.presentation.nav.TracksRoute
import org.example.mp3player.presentation.nav.UserAlbumsRoute
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.tab_albums
import org.example.mp3player.presentation.resources.tab_my_albums
import org.example.mp3player.presentation.resources.tab_tracks
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class BottomItem(
    val route: Route,
    val icon: ImageVector,
    val labelRes: StringResource
)

private val BottomItems = listOf(
    BottomItem(TracksRoute, Icons.Default.MusicNote, Res.string.tab_tracks),
    BottomItem(AlbumsRoute, Icons.Default.Album, Res.string.tab_albums),
    BottomItem(UserAlbumsRoute, Icons.AutoMirrored.Filled.PlaylistPlay, Res.string.tab_my_albums),
)

@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentDestination.isTopLevel()) {
                NavigationBar {
                    BottomItems.forEach { item ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.hasRoute(item.route::class) } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(item.route) {
                                        popUpTo(TracksRoute) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AppNavHost(
            navController = navController,
            onSnackbar = { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            },
            modifier = Modifier.padding(padding),
        )
    }
}

    private fun NavDestination?.isTopLevel(): Boolean {
        if (this == null) return false
        return BottomItems.any { hasRoute(it.route::class) }
    }