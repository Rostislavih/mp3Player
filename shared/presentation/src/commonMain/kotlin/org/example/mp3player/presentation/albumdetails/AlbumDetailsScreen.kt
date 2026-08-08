package org.example.mp3player.presentation.albumdetails

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.mp3player.presentation.common.CoverArt
import org.example.mp3player.presentation.common.ErrorBanner
import org.example.mp3player.presentation.common.LoadingBox
import org.example.mp3player.presentation.common.TrackRow
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.action_back
import org.example.mp3player.presentation.resources.album_details_play_all
import org.example.mp3player.presentation.resources.album_tracks_count
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailsScreen(
    albumId: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailsViewModel = koinViewModel { parametersOf(albumId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AlbumDetailsEffect.OpenPlayer -> onOpenPlayer()
            }
        }
    }

    val title = (state as? AlbumDetailsUiState.Content)?.album?.title.orEmpty()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back)
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            AlbumDetailsUiState.Loading -> LoadingBox(Modifier.padding(padding))

            is AlbumDetailsUiState.Content -> LazyColumn(
                Modifier.padding().fillMaxSize()
            ) {
                item {
                    AlbumHeader(
                        state = current,
                        onPlayAll = { viewModel.onEvent(AlbumDetailsEvent.PlayAll) }
                    )
                }
                itemsIndexed(
                    items = current.tracks,
                    key = { _, track -> track.id },
                ) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { viewModel.onEvent(AlbumDetailsEvent.PlayTrack(index)) }
                    )
                }
            }

            is AlbumDetailsUiState.Error -> ErrorBanner(
                message = current.errorText,
                onRetry = onBack,
                retryText = stringResource(Res.string.action_back),
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun AlbumHeader(state: AlbumDetailsUiState.Content, onPlayAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            data = state.album.coverUri,
            contentDescription = state.album.title,
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(state.album.artist, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(Res.string.album_tracks_count, state.tracks.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onPlayAll, enabled = state.tracks.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(
                    text = stringResource(Res.string.album_details_play_all),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}