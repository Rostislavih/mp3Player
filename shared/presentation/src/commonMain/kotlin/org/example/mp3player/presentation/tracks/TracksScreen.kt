package org.example.mp3player.presentation.tracks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.mp3player.presentation.common.EmptyState
import org.example.mp3player.presentation.common.ErrorBanner
import org.example.mp3player.presentation.common.LoadingBox
import org.example.mp3player.presentation.common.TrackRow
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TracksScreen(
    onOpenPlayer: () -> Unit,
    onSnackbar: (String) -> Unit,
    viewModel: TracksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onEvent(TracksEvent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TracksEffect.OpenPlayer -> onOpenPlayer()
                is TracksEffect.ShowMessage -> onSnackbar(effect.text)
            }
        }
    }

    when (val current = state) {
        TracksUiState.Loading -> LoadingBox()

        is TracksUiState.Error -> ErrorBanner(
            message = current.errorText,
            onRetry = { viewModel.onEvent(TracksEvent.Refresh) },
        )

        is TracksUiState.Content -> TracksContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun TracksContent(
    state: TracksUiState.Content,
    onEvent: (TracksEvent) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onEvent(TracksEvent.Search(it)) },
            placeholder = { Text("Поиск") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        val tracks = state.filteredTracks
        when {
            state.tracks.isEmpty() -> EmptyState(
                title = "Нет музыки",
                description = "Добавь треки на устройство и обнови",
            )

            tracks.isEmpty() -> EmptyState(title = "Ничего не найдено")

            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.id },
                ) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onEvent(TracksEvent.PlayTrack(index)) },
                    )
                }
            }
        }
    }
}
