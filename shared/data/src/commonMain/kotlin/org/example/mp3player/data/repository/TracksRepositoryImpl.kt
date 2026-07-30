package org.example.mp3player.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.mp3player.core.audio.player.AudioTrack
import org.example.mp3player.core.audio.scanner.MusicScanner
import org.example.mp3player.domain.repository.TracksRepository

class TracksRepositoryImpl(
    private val scanner: MusicScanner,
) : TracksRepository {
    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val scanLock = Mutex()

    override fun observeTracks(): Flow<List<AudioTrack>> = _tracks.asStateFlow()


    override suspend fun refresh() {
        scanLock.withLock {
            _tracks.value = scanner.scanTracks()
        }
    }
}