package app.vidro.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ViewMode { List, Grid }

/** What the horizontal shelf on Home shows. */
enum class HomeShelf { ContinueWatching, RecentlyAdded, Off }

enum class SortField { Name, Date, Size, Duration, Count, Artist }

data class SortOrder(val field: SortField, val ascending: Boolean)

data class LibrarySettings(
    val folderView: ViewMode = ViewMode.List,
    val folderSort: SortOrder = SortOrder(SortField.Name, ascending = true),
    val videoView: ViewMode = ViewMode.List,
    val videoSort: SortOrder = SortOrder(SortField.Name, ascending = true),
    val showThumbnails: Boolean = true,
    val showNewBadge: Boolean = true,
    /** "Watched 4 of 10 min" under each video. */
    val showWatchTime: Boolean = true,
    /** The Videos tab has its own layout, list by default. */
    val allVideosView: ViewMode = ViewMode.List,
    /** Folders the user chose to hide from the library. */
    val hiddenFolders: Set<String> = emptySet(),
    /** Videos shorter than this (seconds) are left out; 0 = show all. */
    val minDurationSec: Int = 0,
    /** Tick haptics and click sounds in the UI. */
    val haptics: Boolean = true,
    val homeShelf: HomeShelf = HomeShelf.ContinueWatching,
    /** Tilt-following sheen on the counters and other small motion touches. */
    val motionEffects: Boolean = true,
    /** Ask GitHub for a newer release when the app starts. */
    val checkUpdates: Boolean = true,
    val musicSort: SortOrder = SortOrder(SortField.Name, ascending = true),
    /** Leaves voice notes and other clips under 30 seconds out of the music library. */
    val musicSkipShort: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.dataStore

    val library: Flow<LibrarySettings> = store.data.map { p ->
        val defaults = LibrarySettings()
        LibrarySettings(
            folderView = p.enum(FOLDER_VIEW, defaults.folderView),
            folderSort = SortOrder(
                p.enum(FOLDER_SORT, defaults.folderSort.field),
                p[FOLDER_ASC] ?: defaults.folderSort.ascending,
            ),
            videoView = p.enum(VIDEO_VIEW, defaults.videoView),
            videoSort = SortOrder(
                p.enum(VIDEO_SORT, defaults.videoSort.field),
                p[VIDEO_ASC] ?: defaults.videoSort.ascending,
            ),
            showThumbnails = p[SHOW_THUMBNAILS] ?: defaults.showThumbnails,
            showNewBadge = p[SHOW_NEW] ?: defaults.showNewBadge,
            showWatchTime = p[SHOW_WATCH_TIME] ?: defaults.showWatchTime,
            allVideosView = p.enum(ALL_VIDEOS_VIEW, defaults.allVideosView),
            hiddenFolders = p[HIDDEN_FOLDERS] ?: defaults.hiddenFolders,
            minDurationSec = p[MIN_DURATION] ?: defaults.minDurationSec,
            haptics = p[HAPTICS] ?: defaults.haptics,
            homeShelf = p.enum(HOME_SHELF, defaults.homeShelf),
            motionEffects = p[MOTION] ?: defaults.motionEffects,
            checkUpdates = p[CHECK_UPDATES] ?: defaults.checkUpdates,
            musicSort = SortOrder(
                p.enum(MUSIC_SORT, defaults.musicSort.field),
                p[MUSIC_ASC] ?: defaults.musicSort.ascending,
            ),
            musicSkipShort = p[MUSIC_SKIP_SHORT] ?: defaults.musicSkipShort,
        )
    }

    suspend fun setFolderView(mode: ViewMode) {
        store.edit { it[FOLDER_VIEW] = mode.name }
    }

    suspend fun setVideoView(mode: ViewMode) {
        store.edit { it[VIDEO_VIEW] = mode.name }
    }

    suspend fun setFolderSort(order: SortOrder) {
        store.edit {
            it[FOLDER_SORT] = order.field.name
            it[FOLDER_ASC] = order.ascending
        }
    }

    suspend fun setVideoSort(order: SortOrder) {
        store.edit {
            it[VIDEO_SORT] = order.field.name
            it[VIDEO_ASC] = order.ascending
        }
    }

    suspend fun setShowThumbnails(show: Boolean) {
        store.edit { it[SHOW_THUMBNAILS] = show }
    }

    suspend fun setShowNewBadge(show: Boolean) {
        store.edit { it[SHOW_NEW] = show }
    }

    suspend fun setShowWatchTime(show: Boolean) {
        store.edit { it[SHOW_WATCH_TIME] = show }
    }

    suspend fun setAllVideosView(mode: ViewMode) {
        store.edit { it[ALL_VIDEOS_VIEW] = mode.name }
    }

    suspend fun setFolderHidden(path: String, hidden: Boolean) {
        store.edit {
            val current = it[HIDDEN_FOLDERS] ?: emptySet()
            it[HIDDEN_FOLDERS] = if (hidden) current + path else current - path
        }
    }

    suspend fun setMinDuration(seconds: Int) {
        store.edit { it[MIN_DURATION] = seconds }
    }

    suspend fun setHaptics(enabled: Boolean) {
        store.edit { it[HAPTICS] = enabled }
    }

    suspend fun setHomeShelf(shelf: HomeShelf) {
        store.edit { it[HOME_SHELF] = shelf.name }
    }

    suspend fun setMotionEffects(enabled: Boolean) {
        store.edit { it[MOTION] = enabled }
    }

    suspend fun setCheckUpdates(enabled: Boolean) {
        store.edit { it[CHECK_UPDATES] = enabled }
    }

    suspend fun setMusicSort(order: SortOrder) {
        store.edit {
            it[MUSIC_SORT] = order.field.name
            it[MUSIC_ASC] = order.ascending
        }
    }

    suspend fun setMusicSkipShort(enabled: Boolean) {
        store.edit { it[MUSIC_SKIP_SHORT] = enabled }
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private companion object {
        val FOLDER_VIEW = stringPreferencesKey("folder_view")
        val FOLDER_SORT = stringPreferencesKey("folder_sort")
        val FOLDER_ASC = booleanPreferencesKey("folder_sort_asc")
        val VIDEO_VIEW = stringPreferencesKey("video_view")
        val VIDEO_SORT = stringPreferencesKey("video_sort")
        val VIDEO_ASC = booleanPreferencesKey("video_sort_asc")
        val SHOW_THUMBNAILS = booleanPreferencesKey("show_thumbnails")
        val SHOW_NEW = booleanPreferencesKey("show_new_badge")
        val SHOW_WATCH_TIME = booleanPreferencesKey("show_watch_time")
        val ALL_VIDEOS_VIEW = stringPreferencesKey("all_videos_view")
        val HIDDEN_FOLDERS = stringSetPreferencesKey("hidden_folders")
        val MIN_DURATION = intPreferencesKey("min_duration_sec")
        val HAPTICS = booleanPreferencesKey("haptics")
        val HOME_SHELF = stringPreferencesKey("home_shelf")
        val MOTION = booleanPreferencesKey("motion_effects")
        val CHECK_UPDATES = booleanPreferencesKey("check_updates")
        val MUSIC_SORT = stringPreferencesKey("music_sort")
        val MUSIC_ASC = booleanPreferencesKey("music_sort_asc")
        val MUSIC_SKIP_SHORT = booleanPreferencesKey("music_skip_short")
    }
}
