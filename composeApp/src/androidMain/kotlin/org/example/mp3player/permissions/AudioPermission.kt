package org.example.mp3player.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

enum class AudioPermission { Granted, Denied, Unknown }

@Composable
fun rememberAudioPermissionState(): Pair<AudioPermission, () -> Unit> {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var state by remember {
        mutableStateOf(
            if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED
            ) AudioPermission.Granted
            else AudioPermission.Unknown
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        state = if (granted) AudioPermission.Granted else AudioPermission.Denied
    }

    val request = remember(launcher) { { launcher.launch(permission) } }

    return state to request
}