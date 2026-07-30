package org.example.mp3player.presentation.tracks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TracksScreen(
    onOpenPlayer: () -> Unit,
    snackbar: (String) -> Unit,
    viewModel: TracksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onEvent(TracksEvent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                TracksEffect.OpenPlayer -> onOpenPlayer()
                is TracksEffect.ShowMessage -> snackbar.showSnackbar(effect.text)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.onEvent(TracksEvent.Search(it)) },
            placeholder = { Text("Поиск") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
        )

        when {
            state.isLoading && state.tracks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.tracks.isEmpty() -> {
                ErrorBanner(
                    message = state.error!!,
                    onRetry = { viewModel.onEvent(TracksEvent.Refresh) },
                )
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.filteredTracks) { index, track ->
                        TrackRow(
                            track = track,
                            onClick = { viewModel.onEvent(TracksEvent.PlayTrack(index)) },
                        )
                    }
                }
            }
        }
    }
}


