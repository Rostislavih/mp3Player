package org.example.mp3player.presentation.common

/**
 * Миллисекунды → "3:07" или "1:05:42" для треков длиннее часа.
 * Чистый Kotlin: работает и на Android, и на iOS, без зависимостей.
 */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "$hours:${minutes.pad2()}:${seconds.pad2()}"
    } else {
        "$minutes:${seconds.pad2()}"
    }
}

private fun Long.pad2(): String = toString().padStart(2, '0')
