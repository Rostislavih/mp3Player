package org.example.mp3player.presentation.useralbums

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.mp3player.presentation.common.AlbumDetailsContent
import org.example.mp3player.presentation.common.ErrorBanner
import org.example.mp3player.presentation.common.LoadingBox
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.action_back
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun UserAlbumDetailsScreen(
    albumId: Long,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserAlbumDetailsViewModel = koinViewModel { parametersOf(albumId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                UserAlbumDetailsEffect.OpenPlayer -> onOpenPlayer()
            }
        }
    }

    when (val current = state) {
        UserAlbumDetailsUiState.Loading -> LoadingBox(modifier)

        is UserAlbumDetailsUiState.Content -> AlbumDetailsContent(
            title = current.title,
            subtitle = current.description,
            coverUri = current.coverUri,
            tracks = current.tracks,
            onBack = onBack,
            onPlayAll = { viewModel.onEvent(UserAlbumDetailsEvent.PlayAll) },
            onTrackClick = { index -> viewModel.onEvent(UserAlbumDetailsEvent.PlayTrack(index)) },
            modifier = modifier,
        )

        is UserAlbumDetailsUiState.Error -> ErrorBanner(
            message = current.errorText,
            onRetry = onBack,
            retryText = stringResource(Res.string.action_back),
            modifier = modifier,
        )
    }
}
