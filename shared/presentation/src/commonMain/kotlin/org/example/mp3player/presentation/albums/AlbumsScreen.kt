package org.example.mp3player.presentation.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.mp3player.domain.model.Album
import org.example.mp3player.presentation.common.CoverArt
import org.example.mp3player.presentation.common.EmptyState
import org.example.mp3player.presentation.common.LoadingBox
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.album_tracks_count
import org.example.mp3player.presentation.resources.albums_empty
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.component.getScopeId


@Composable
fun AlbumsScreen(
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is AlbumsUiState.Content -> LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = current.albums, key = { it.id }) { album ->
                AlbumCard(album = album, onClick = { onAlbumClick(album.id) })
            }
        }

        AlbumsUiState.Empty -> EmptyState(
            title = stringResource(Res.string.albums_empty),
            modifier = modifier
        )

        AlbumsUiState.Loading -> LoadingBox(modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column {
            CoverArt(
                data = album.coverUri,
                contentDescription = album.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                cornerRadius = 0.dp
            )
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = album.title,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleSmall,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(Res.string.album_tracks_count, album.trackCount),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}