package org.example.mp3player.domain

import kotlinx.coroutines.flow.Flow

interface TracksRepository {
    fun observeTracks(): Flow<List<Track>>
    suspend fun refresh()
}