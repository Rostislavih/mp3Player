package org.example.mp3player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.example.mp3player.core.audio.player.AudioTrack

interface TracksRepository {
    fun observeTracks(): Flow<List<AudioTrack>>

    suspend fun refresh()
}