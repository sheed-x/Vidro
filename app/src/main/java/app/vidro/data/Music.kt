package app.vidro.data

import android.content.Context
import android.database.ContentObserver
import android.provider.MediaStore
import app.vidro.player.PlayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Song(
    val id: Long,
    val path: String,
    val title: String,
    /** Null when the file has no artist tag. */
    val artist: String?,
    val album: String?,
    val albumId: Long,
    val durationMs: Long,
    val size: Long,
    /** Seconds since epoch. */
    val dateAdded: Long,
    /** Disc × 1000 + track, as MediaStore stores it; 0 = unknown. */
    val track: Int,
    val year: Int,
) {
    val folderPath: String get() = path.substringBeforeLast('/')
    val extension: String get() = path.substringAfterLast('.', "").uppercase()

    fun toPlayItem() = PlayItem(path, title, artist, album, isAudio = true, durationMs = durationMs)
}

data class Album(val id: Long, val name: String, val artist: String?, val songs: List<Song>) {
    val year: Int = songs.maxOfOrNull { it.year } ?: 0
    val durationMs: Long = songs.sumOf { it.durationMs }
}

data class Artist(val name: String, val songs: List<Song>) {
    val albumCount: Int = songs.mapNotNull { it.album }.distinct().size
}

data class MusicState(
    val songs: List<Song> = emptyList(),
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
) {
    val albums: List<Album> by lazy {
        songs.groupBy { it.albumId }.mapNotNull { (id, list) ->
            val name = list.firstNotNullOfOrNull { it.album } ?: return@mapNotNull null
            val artists = list.mapNotNull { it.artist }.distinct()
            Album(id, name, artists.singleOrNull() ?: artists.firstOrNull(), list.sortedWith(compareBy<Song> { it.track }.thenComparator { a, b -> NaturalOrder.compare(a.title, b.title) }))
        }.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    }

    val artists: List<Artist> by lazy {
        songs.filter { it.artist != null }.groupBy { it.artist!! }
            .map { (name, list) -> Artist(name, list.sortedWith { a, b -> NaturalOrder.compare(a.title, b.title) }) }
            .sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    }

    val totalDurationMs: Long by lazy { songs.sumOf { it.durationMs } }
}

fun List<Song>.sortedSongs(order: SortOrder): List<Song> {
    val byTitle = Comparator<Song> { a, b -> NaturalOrder.compare(a.title, b.title) }
    val comparator: Comparator<Song> = when (order.field) {
        SortField.Date -> compareBy { it.dateAdded }
        SortField.Duration -> compareBy { it.durationMs }
        SortField.Size -> compareBy { it.size }
        SortField.Artist -> Comparator<Song> { a, b -> NaturalOrder.compare(a.artist ?: "￿", b.artist ?: "￿") }.then(byTitle)
        SortField.Name, SortField.Count -> byTitle
    }
    return sortedWith(if (order.ascending) comparator else comparator.reversed())
}

/** Reads songs from MediaStore's audio index (ringtones, alarms and notifications are left out). */
object MusicScanner {

    private const val UNKNOWN = "<unknown>"

    fun scan(context: Context): List<Song> {
        val columns = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR,
        )
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null,
        ) ?: return emptyList()
        return cursor.use { c ->
            val songs = ArrayList<Song>(c.count)
            val seen = HashSet<String>()
            while (c.moveToNext()) {
                val path = c.getString(1) ?: continue
                if (!seen.add(path)) continue
                val name = path.substringAfterLast('/').substringBeforeLast('.')
                songs += Song(
                    id = c.getLong(0),
                    path = path,
                    title = c.getString(2)?.takeIf { it.isNotBlank() } ?: name,
                    artist = c.getString(3)?.takeIf { it.isNotBlank() && it != UNKNOWN },
                    album = c.getString(4)?.takeIf { it.isNotBlank() && it != UNKNOWN },
                    albumId = c.getLong(5),
                    durationMs = c.getLong(6),
                    size = c.getLong(7),
                    dateAdded = c.getLong(8),
                    track = c.getInt(9),
                    year = c.getInt(10),
                )
            }
            songs
        }
    }
}

/** The music library; rescans when MediaStore reports changes to audio files. */
class MusicRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var debounceJob: Job? = null
    private var observing = false

    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(1500)
                refresh()
            }
        }
    }

    /** Call whenever access may have changed (opening the Music tab, returning from settings). */
    fun start() {
        if (!StorageAccess.hasAudioAccess(context)) return
        if (!observing) {
            context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
            observing = true
        }
        if (!_state.value.hasScanned) refresh()
    }

    fun refresh() {
        if (!StorageAccess.hasAudioAccess(context)) return
        scanJob?.cancel()
        scanJob = scope.launch {
            _state.update { it.copy(isScanning = true) }
            val songs = runCatching { MusicScanner.scan(context) }.getOrDefault(emptyList())
            _state.value = MusicState(songs, isScanning = false, hasScanned = true)
        }
    }
}
