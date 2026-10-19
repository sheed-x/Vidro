package app.vidro.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.vidro.VidroApp
import app.vidro.data.Album
import app.vidro.data.Artist
import app.vidro.data.LibrarySettings
import app.vidro.data.MusicState
import app.vidro.data.Song
import app.vidro.data.SortOrder
import app.vidro.data.StorageAccess
import app.vidro.data.sortedSongs
import app.vidro.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VidroApp

    val settings: StateFlow<LibrarySettings> = app.settings.library
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySettings())

    private val _hasAccess = MutableStateFlow(StorageAccess.hasAudioAccess(application))
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    /** The scanned library, minus short clips when the user asked for that. */
    val library: StateFlow<MusicState> = combine(app.music.state, settings) { state, s ->
        if (!s.musicSkipShort) state
        else state.copy(songs = state.songs.filter { it.durationMs <= 0 || it.durationMs >= SHORT_CLIP_MS })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, MusicState())

    val songs: StateFlow<List<Song>> = combine(library, settings) { lib, s -> lib.songs.sortedSongs(s.musicSort) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Songs from the playback history, latest first. */
    val recentlyPlayed: StateFlow<List<Song>> = combine(library, app.history.entries) { lib, history ->
        val byPath = lib.songs.associateBy { it.path }
        history.values.sortedByDescending { it.lastPlayed }.mapNotNull { byPath[it.key] }.take(RECENT_COUNT)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onResume() {
        _hasAccess.value = StorageAccess.hasAudioAccess(app)
        if (_hasAccess.value) app.music.start()
    }

    fun refresh() = app.music.refresh()

    fun setSort(order: SortOrder) = viewModelScope.launch { app.settings.setMusicSort(order) }

    fun album(id: Long): Album? = library.value.albums.firstOrNull { it.id == id }

    fun artist(name: String): Artist? = library.value.artists.firstOrNull { it.name == name }

    /** Starts [songs] from [index]; [shuffle] plays them in a random order instead. */
    fun play(songs: List<Song>, index: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val ordered = if (shuffle) songs.shuffled() else songs
        PlayerHolder.start(app, ordered.map { it.toPlayItem() }, if (shuffle) 0 else index, audioOnly = true)
    }

    fun playNext(song: Song) {
        val controller = PlayerHolder.controller
        if (controller == null) play(listOf(song)) else controller.playNext(listOf(song.toPlayItem()))
    }

    fun addToQueue(song: Song) {
        val controller = PlayerHolder.controller
        if (controller == null) play(listOf(song)) else controller.addToQueue(listOf(song.toPlayItem()))
    }

    private companion object {
        const val SHORT_CLIP_MS = 30_000L
        const val RECENT_COUNT = 12
    }
}
