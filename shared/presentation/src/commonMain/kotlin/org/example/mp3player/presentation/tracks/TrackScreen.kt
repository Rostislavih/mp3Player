package org.example.mp3player.presentation.tracks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TrackScreen(
    onOpenPlayer: () -> Unit,
    viewModel: TracksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
}

