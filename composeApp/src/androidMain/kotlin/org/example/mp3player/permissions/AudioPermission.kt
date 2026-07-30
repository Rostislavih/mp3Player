package org.example.mp3player.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

enum class AudioPermissionState { Granted, Denied, Unknown }

@Composable
fun rememberAudioPermissionState(): Pair<AudioPermissionState, () -> Unit> {
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
            ) AudioPermissionState.Granted
            else AudioPermissionState.Unknown
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        state = if (granted) AudioPermissionState.Granted else AudioPermissionState.Denied
    }

    val request = remember(launcher) { { launcher.launch(permission) } }

    return state to request
}
