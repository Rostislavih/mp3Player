package org.example.mp3player.data.player


import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi


import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.mp3player.domain.PlaybackState
import org.example.mp3player.domain.RepeatMode
import org.example.mp3player.domain.Track

actual class AudioPlayer(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _state = MutableStateFlow(PlaybackState())
    actual val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var currentQueue: List<Track> = emptyList()

    init {
        connect()
    }

    @OptIn(UnstableApi::class)
    private fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(playerListener)
                syncFromController(c)
            }
            startPositionPolling()
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = @UnstableApi object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncFromController()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncFromController()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            syncFromController()
        }

        @Deprecated("Deprecated in Java")
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            syncFromController()
        }

        override fun onPlayerError(error: PlaybackException) {}
    }

    private fun syncFromController(c: MediaController? = controller) {
        val ctrl = c ?: return
        val index = ctrl.currentMediaItemIndex
        val current = currentQueue.getOrNull(index)
        _state.value = _state.value.copy(
            currentTrack = current,
            isPlaying = ctrl.isPlaying,
            positionMs = ctrl.currentPosition.coerceAtLeast(0),
            durationMs = ctrl.duration.takeIf { it > 0 } ?: 0,
            queue = currentQueue,
            queueIndex = index,
        )
    }

    private fun startPositionPolling() {
        scope.launch {
            while (true) {
                val ctrl = controller
                if (ctrl != null && ctrl.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = ctrl.currentPosition.coerceAtLeast(0)
                    )
                }
                delay(500)
            }
        }
    }

    actual fun play(queue: List<Track>, startIndex: Int) {
        val ctrl = controller ?: return
        currentQueue = queue
        val items = queue.map { it.toMediaItem() }
        ctrl.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0)
        ctrl.prepare()
        ctrl.play()
    }

    actual fun resume() {
        controller?.play()
    }

    actual fun pause() {
        controller?.pause()
    }

    actual fun toggle() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    actual fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    actual fun next() {
        controller?.seekToNextMediaItem()
    }

    actual fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    actual fun setRepeatMode(mode: RepeatMode) {
        controller?.repeatMode = when (mode) {
            RepeatMode.Off -> Player.REPEAT_MODE_OFF
            RepeatMode.One -> Player.REPEAT_MODE_ONE
            RepeatMode.All -> Player.REPEAT_MODE_ALL
        }
    }

    actual fun setShuffleModeEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    actual fun release() {
        controller?.release()
        controller = null
    }
    private fun Track.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(coverUri?.let(Uri::parse))
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(path)
            .setMediaMetadata(metadata)
            .build()
    }

}
