package org.example.mp3player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.mp3player.domain.Track
import org.example.mp3player.domain.TracksRepository

class TracksRepositoryImpl(
    private val scanner: MusicScanner,
    ) : TracksRepository {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val scanLock = Mutex()

    override fun observeTracks(): Flow<List<Track>> = _tracks.asStateFlow()


    override suspend fun refresh() {
        scanLock.withLock {
            _tracks.value = scanner.scanTracks()
        }
    }
}