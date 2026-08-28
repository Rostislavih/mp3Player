package org.example.mp3player.presentation.common

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.mp3player.core.audio.player.AudioTrack
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.action_back
import org.example.mp3player.presentation.resources.album_details_play_all
import org.example.mp3player.presentation.resources.album_tracks_count
import org.jetbrains.compose.resources.stringResource

/**
 * «Глупый» общий UI деталей альбома: системного и пользовательского.
 * Ничего не знает про ViewModel, Koin и модели конкретных экранов —
 * получает примитивы, отдаёт колбэки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailsContent(
    title: String,
    subtitle: String,
    coverUri: String?,
    tracks: List<AudioTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                AlbumHeader(
                    title = title,
                    subtitle = subtitle,
                    coverUri = coverUri,
                    trackCount = tracks.size,
                    onPlayAll = onPlayAll,
                )
            }
            itemsIndexed(
                items = tracks,
                key = { _, track -> track.id },
            ) { index, track ->
                TrackRow(
                    track = track,
                    onClick = { onTrackClick(index) }
                )
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    title: String,
    subtitle: String,
    coverUri: String?,
    trackCount: Int,
    onPlayAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            data = coverUri,
            contentDescription = title,
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(Res.string.album_tracks_count, trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onPlayAll, enabled = trackCount > 0) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(
                    text = stringResource(Res.string.album_details_play_all),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
