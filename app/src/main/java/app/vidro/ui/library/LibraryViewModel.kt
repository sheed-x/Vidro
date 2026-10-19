package app.vidro.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.vidro.VidroApp
import app.vidro.data.HomeShelf
import app.vidro.data.LibrarySettings
import app.vidro.data.LibraryState
import app.vidro.data.PlaybackEntry
import app.vidro.data.SortOrder
import app.vidro.data.StorageAccess
import app.vidro.data.Video
import app.vidro.data.VideoFolder
import app.vidro.data.ViewMode
import app.vidro.data.sortedFolders
import app.vidro.data.sortedVideos
import app.vidro.player.PlayItem
import app.vidro.player.PlayerHolder
import app.vidro.player.PlayerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VidroApp

    val settings: StateFlow<LibrarySettings> = app.settings.library
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySettings())

    /** The scanned library minus hidden folders and videos shorter than the user's minimum. */
    val library: StateFlow<LibraryState> = combine(app.library.state, settings) { lib, s ->
        if (s.hiddenFolders.isEmpty() && s.minDurationSec <= 0) return@combine lib
        val minMs = s.minDurationSec * 1000L
        lib.copy(
            folders = lib.folders
                .filter { it.path !in s.hiddenFolders }
                .mapNotNull { folder ->
                    val kept = folder.videos.filter { it.durationMs <= 0 || it.durationMs >= minMs }
                    if (kept.isEmpty()) null else if (kept.size == folder.videos.size) folder else folder.copy(videos = kept)
                },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, app.library.state.value)

    private val _hasAccess = MutableStateFlow(StorageAccess.hasAnyAccess(application))
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private val _hasFullAccess = MutableStateFlow(StorageAccess.hasFullAccess(application))
    val hasFullAccess: StateFlow<Boolean> = _hasFullAccess.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val folders: StateFlow<List<VideoFolder>> = combine(library, settings) { lib, s ->
        lib.folders.sortedFolders(s.folderSort)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchResults: StateFlow<List<Video>> = combine(library, settings, _query) { lib, s, q ->
        val term = q.trim()
        if (term.isEmpty()) emptyList()
        else lib.allVideos.filter { it.name.contains(term, ignoreCase = true) }.sortedVideos(s.videoSort)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allVideos: StateFlow<List<Video>> = combine(library, settings) { lib, s ->
        lib.allVideos.sortedVideos(s.videoSort)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyAdded: StateFlow<List<Video>> = library.map { lib ->
        lib.allVideos.sortedByDescending { it.dateAdded }.take(RECENT_COUNT)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Started but not finished videos, most recently watched first. */
    val continueWatching: StateFlow<List<Video>> = combine(library, app.history.entries) { lib, history ->
        val byPath = lib.allVideos.associateBy { it.path }
        history.values
            .filter { !it.finished && it.positionMs > 0 }
            .sortedByDescending { it.lastPlayed }
            .mapNotNull { byPath[it.key] }
            .take(RECENT_COUNT)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun folderVideos(path: String): Flow<List<Video>> = combine(library, settings) { lib, s ->
        lib.folders.firstOrNull { it.path == path }?.videos.orEmpty().sortedVideos(s.videoSort)
    }.flowOn(Dispatchers.Default)

    val history: StateFlow<Map<String, PlaybackEntry>> = app.history.entries

    /** Player options that are also offered in the app's Settings screen. */
    val playerSettings: StateFlow<PlayerSettings> = app.playerSettings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerSettings())

    fun updatePlayerSettings(transform: (PlayerSettings) -> PlayerSettings) =
        viewModelScope.launch { app.playerSettings.update(transform) }

    /** The play queue for [video]: its whole folder in the current sort order, and where it sits in it. */
    fun queueFor(video: Video): Pair<List<String>, Int> {
        val folder = library.value.folders.firstOrNull { it.path == video.folderPath }
        val paths = (folder?.videos?.sortedVideos(settings.value.videoSort) ?: listOf(video)).map { it.path }
        return paths to paths.indexOf(video.path).coerceAtLeast(0)
    }

    /** Plays [video] and the rest of its folder as sound only, for the music screen and a locked phone. */
    fun playAsAudio(video: Video) {
        val folder = library.value.folders.firstOrNull { it.path == video.folderPath }
        val videos = folder?.videos?.sortedVideos(settings.value.videoSort) ?: listOf(video)
        val items = videos.map { PlayItem(it.path, it.title, durationMs = it.durationMs) }
        PlayerHolder.start(app, items, videos.indexOfFirst { it.path == video.path }.coerceAtLeast(0), audioOnly = true)
    }

    fun folderName(path: String): String =
        library.value.folders.firstOrNull { it.path == path }?.name ?: path.substringAfterLast('/')

    fun onResume() {
        val context = getApplication<Application>()
        val hadFull = _hasFullAccess.value
        _hasAccess.value = StorageAccess.hasAnyAccess(context)
        _hasFullAccess.value = StorageAccess.hasFullAccess(context)
        if (!_hasAccess.value) return
        // Rescan on first resume or when the access level changed; MediaStore changes trigger rescans themselves.
        if (!library.value.hasScanned || hadFull != _hasFullAccess.value) app.library.start()
    }

    fun refresh() = app.library.refresh()

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFolderView(mode: ViewMode) = viewModelScope.launch { app.settings.setFolderView(mode) }
    fun setVideoView(mode: ViewMode) = viewModelScope.launch { app.settings.setVideoView(mode) }
    fun setFolderSort(order: SortOrder) = viewModelScope.launch { app.settings.setFolderSort(order) }
    fun setVideoSort(order: SortOrder) = viewModelScope.launch { app.settings.setVideoSort(order) }
    fun setShowThumbnails(show: Boolean) = viewModelScope.launch { app.settings.setShowThumbnails(show) }
    fun setShowNewBadge(show: Boolean) = viewModelScope.launch { app.settings.setShowNewBadge(show) }
    fun setShowWatchTime(show: Boolean) = viewModelScope.launch { app.settings.setShowWatchTime(show) }
    fun setAllVideosView(mode: ViewMode) = viewModelScope.launch { app.settings.setAllVideosView(mode) }
    fun setFolderHidden(path: String, hidden: Boolean) = viewModelScope.launch { app.settings.setFolderHidden(path, hidden) }
    fun setMinDuration(seconds: Int) = viewModelScope.launch { app.settings.setMinDuration(seconds) }
    fun setHaptics(enabled: Boolean) = viewModelScope.launch { app.settings.setHaptics(enabled) }
    fun setHomeShelf(shelf: HomeShelf) = viewModelScope.launch { app.settings.setHomeShelf(shelf) }
    fun setMotionEffects(enabled: Boolean) = viewModelScope.launch { app.settings.setMotionEffects(enabled) }
    fun setCheckUpdates(enabled: Boolean) = viewModelScope.launch { app.settings.setCheckUpdates(enabled) }
    fun setMusicSkipShort(enabled: Boolean) = viewModelScope.launch { app.settings.setMusicSkipShort(enabled) }
    fun rescanMusic() = app.music.refresh()
    val updates get() = app.updates

    /** The launch-time check, run once the stored setting is known. */
    fun checkUpdatesOnLaunch() {
        viewModelScope.launch {
            if (app.settings.library.first().checkUpdates) app.updates.checkOnLaunch()
        }
    }
    fun clearHistory() = app.history.clearAll()

    private companion object {
        const val RECENT_COUNT = 12
    }
}
