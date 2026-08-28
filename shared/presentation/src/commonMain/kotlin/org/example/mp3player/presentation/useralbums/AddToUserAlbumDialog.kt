package org.example.mp3player.presentation.useralbums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.mp3player.domain.model.UserAlbum
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.add_to_album
import org.example.mp3player.presentation.resources.add_to_album_empty
import org.example.mp3player.presentation.resources.user_album_dialog_cancel
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddToUserAlbumDialog(
    albums: List<UserAlbum>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_to_album)) },
        text = {
            if (albums.isEmpty()) {
                Text(stringResource(Res.string.add_to_album_empty))
            } else {
                LazyColumn {
                    items(items = albums, key = { it.id }) { album ->
                        ListItem(
                            headlineContent = { Text(album.title) },
                            modifier = Modifier.clickable { onPick(album.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                Text(stringResource(Res.string.user_album_dialog_cancel))
            }
        },
    )
}