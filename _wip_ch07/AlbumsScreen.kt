package org.example.mp3player.presentation.albums

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AlbumsScreen(
    onAlbumsClick: (String) -> Unit,
    viewModel: AlbumsViewModel = koinViewModel(),
) {
    val state by viewModel.state.colle
}