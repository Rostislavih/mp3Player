package org.example.mp3player.presentation.tracks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.mp3player.domain.model.UserAlbum
import org.example.mp3player.presentation.common.EmptyState
import org.example.mp3player.presentation.common.ErrorBanner
import org.example.mp3player.presentation.common.LoadingBox
import org.example.mp3player.presentation.common.TrackRow
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.add_to_album
import org.example.mp3player.presentation.resources.tracks_empty_description
import org.example.mp3player.presentation.resources.tracks_empty_title
import org.example.mp3player.presentation.resources.tracks_no_results
import org.example.mp3player.presentation.resources.tracks_search_hint
import org.example.mp3player.presentation.useralbums.AddToUserAlbumDialog
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TracksScreen(
    onOpenPlayer: () -> Unit,
    onSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TracksViewModel = koinViewModel(),
) {
    val userAlbums by viewModel.userAlbums.collectAsStateWithLifecycle()
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
            modifier = modifier,
        )

        is TracksUiState.Content -> TracksContent(
            state = current,
            userAlbums = userAlbums,
            onEvent = viewModel::onEvent,
            modifier = modifier,
        )
    }
}

@Composable
private fun TracksContent(
    state: TracksUiState.Content,
    onEvent: (TracksEvent) -> Unit,
    userAlbums: List<UserAlbum>,
    modifier: Modifier = Modifier,
) {

    var pendingTrackId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onEvent(TracksEvent.Search(it)) },
            placeholder = { Text(stringResource(Res.string.tracks_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        val tracks = state.filteredTracks
        when {
            state.tracks.isEmpty() -> EmptyState(
                title = stringResource(Res.string.tracks_empty_title),
                description = stringResource(Res.string.tracks_empty_description),
            )

            tracks.isEmpty() -> EmptyState(title = stringResource(Res.string.tracks_no_results))

            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.id },
                ) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onEvent(TracksEvent.PlayTrack(index)) },
                        trailingContent = {
                            IconButton(onClick = { pendingTrackId = track.id }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(Res.string.add_to_album)
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    val trackId = pendingTrackId
    if (trackId != null) {
        AddToUserAlbumDialog(
            albums = userAlbums,
            onDismiss = { pendingTrackId = null },
            onPick = { albumId ->
                onEvent(TracksEvent.AddToUserAlbum(trackId, albumId))
                pendingTrackId = null
            },
        )
    }
}
