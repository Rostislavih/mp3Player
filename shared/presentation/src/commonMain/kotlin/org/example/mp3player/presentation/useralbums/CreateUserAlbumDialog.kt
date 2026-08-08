package org.example.mp3player.presentation.useralbums

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.user_album_dialog_cancel
import org.example.mp3player.presentation.resources.user_album_dialog_create
import org.example.mp3player.presentation.resources.user_album_dialog_name_hint
import org.example.mp3player.presentation.resources.user_album_dialog_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateUserAlbumDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.user_album_dialog_title)) },
        text = {
            OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true,
                placeholder = { Text(stringResource(Res.string.user_album_dialog_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title.trim()) }, enabled = title.isNotBlank()) {
                Text(stringResource(Res.string.user_album_dialog_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.user_album_dialog_cancel))
            }
        }
    )
}