package org.example.mp3player.presentation.useralbums

/** Снизу вверх: экран -> ViewModel. */
sealed interface UserAlbumDetailsEvent {
    data object PlayAll : UserAlbumDetailsEvent
    data class PlayTrack(val index: Int) : UserAlbumDetailsEvent
}

/** Сверху вниз, одноразово: ViewModel -> экран. */
sealed interface UserAlbumDetailsEffect {
    data object OpenPlayer : UserAlbumDetailsEffect
}
