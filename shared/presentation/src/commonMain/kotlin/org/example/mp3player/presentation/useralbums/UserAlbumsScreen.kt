package org.example.mp3player.presentation.useralbums

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
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

            is UserAlbumsUiState.Content -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                items(items = current.albums, key = { it.id }) { album ->
                    ListItem(
                        headlineContent = { Text(album.title) },
                        supportingContent = {
                            Text(stringResource(Res.string.album_tracks_count, album.trackIds.size))
                        },
                        trailingContent = {
                            IconButton(
                                onClick = { viewModel.onEvent(UserAlbumsEvent.Delete(album.id)) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(Res.string.user_album_delete),
                                )
                            }
                        },
                    )
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