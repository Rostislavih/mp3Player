package org.example.mp3player.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.example.mp3player.core.audio.player.AudioTrack

@Composable
fun TrackRow(
    track: AudioTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = trailingContent ?: {
            Text(formatDuration(track.duration))
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
