package org.example.mp3player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.mp3player.permissions.AudioPermissionState
import org.example.mp3player.permissions.rememberAudioPermissionState
import org.example.mp3player.shared.RootScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioPermissionGate {
                RootScreen()
            }
        }
    }
}

/**
 * Пускает [content] дальше только когда разрешение на чтение аудио выдано.
 * Пока не выдано — просит его (один раз) и показывает баннер с кнопкой.
 */
@Composable
private fun AudioPermissionGate(content: @Composable () -> Unit) {
    val (permissionState, requestPermission) = rememberAudioPermissionState()

    LaunchedEffect(permissionState) {
        if (permissionState == AudioPermissionState.Unknown) {
            requestPermission()
        }
    }

    when (permissionState) {
        AudioPermissionState.Granted -> content()

        else -> MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Приложению нужен доступ к музыке на устройстве",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = requestPermission,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Разрешить")
                }
            }
        }
    }
}
