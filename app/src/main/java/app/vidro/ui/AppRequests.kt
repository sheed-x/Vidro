package app.vidro.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Navigation asked for from outside the UI (notification, the video player, other apps). */
object AppRequests {
    private val _nowPlaying = MutableStateFlow(false)
    val nowPlaying: StateFlow<Boolean> = _nowPlaying.asStateFlow()

    fun openNowPlaying() {
        _nowPlaying.value = true
    }

    fun consumeNowPlaying() {
        _nowPlaying.value = false
    }
}
