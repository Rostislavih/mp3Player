package org.example.mp3player.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.example.mp3player.presentation.tracks.TracksScreen

@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    MaterialTheme {
        // Временно, до главы 07: навигации и снэкбара ещё нет.
        TracksScreen(
            onOpenPlayer = {},
            onSnackbar = {},
        )
    }
}
