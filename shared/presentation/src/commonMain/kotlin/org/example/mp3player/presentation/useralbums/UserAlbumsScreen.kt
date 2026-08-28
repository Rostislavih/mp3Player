package org.example.mp3player.presentation.useralbums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.mp3player.domain.model.UserAlbum
import org.example.mp3player.presentation.common.CoverArt
import org.example.mp3player.presentation.common.EmptyState
import org.example.mp3player.presentation.common.LoadingBox
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.album_tracks_count
import org.example.mp3player.presentation.resources.user_album_delete
import org.example.mp3player.presentation.resources.user_albums_create
import org.example.mp3player.presentation.resources.user_albums_empty_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAlbumsScreen(
    onSnackbar: (String) -> Unit,
    onAlbumClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserAlbumsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UserAlbumsEffect.ShowMessage -> onSnackbar(effect.text)
            }
        }
    }
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.user_albums_create)
                )
            }
        }
    ) { padding ->
        when (val current = state) {
            UserAlbumsUiState.Loading -> LoadingBox(Modifier.padding(padding))
            UserAlbumsUiState.Empty -> EmptyState(
                title = stringResource(Res.string.user_albums_empty_title),
                description = stringResource(Res.string.user_albums_empty_title),
                modifier = Modifier.padding(padding),
            )

            is UserAlbumsUiState.Content -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = current.albums, key = { it.id }) { album ->
                    UserAlbumCard(album = album, onClick = {
                        onAlbumClick(album.id)
                    }, onDelete = { viewModel.onEvent(UserAlbumsEvent.Delete(album.id)) })
                }
            }
        }
    }
    if (showCreateDialog) {
        CreateUserAlbumDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title ->
                viewModel.onEvent(UserAlbumsEvent.Create(title))
                showCreateDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserAlbumCard(album: UserAlbum, onClick: () -> Unit, onDelete: () -> Unit) {
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
                    text = stringResource(Res.string.album_tracks_count, album.trackIds.size),
                    style = MaterialTheme.typography.labelSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.user_album_delete),
                        )
                    }
                }
            }
        }
    }
}

