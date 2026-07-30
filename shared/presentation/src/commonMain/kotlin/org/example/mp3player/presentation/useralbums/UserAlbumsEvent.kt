package org.example.mp3player.presentation.useralbums

sealed interface UserAlbumsEvent {
    data class Create(val title: String) : UserAlbumsEvent
    data class Delete(val id: Long) : UserAlbumsEvent
}

sealed interface UserAlbumsEffect {
    data class ShowMessage(val text: String) : UserAlbumsEffect
}
