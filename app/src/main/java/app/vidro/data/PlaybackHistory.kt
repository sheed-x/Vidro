package app.vidro.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaybackEntry(
    /** File path, or the content:// URI for videos opened from other apps. */
    val key: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayed: Long,
    val finished: Boolean,
    /** Per-file sync fixes, remembered for next time. */
    val audioDelayMs: Long = 0,
    val subDelayMs: Long = 0,
    /** How many times playback of this video was started. */
    val playCount: Int = 0,
) {
    /** 0..1 watched fraction for the progress bar on thumbnails. */
    val progress: Float
        get() = when {
            finished -> 1f
            durationMs <= 0 -> 0f
            else -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        }
}

data class Bookmark(val id: Long, val key: String, val positionMs: Long, val created: Long)

/**
 * Where each video was left off, plus bookmarks. Entries are kept fully in memory as a [StateFlow]
 * (one small row per watched video) so library screens can show progress without querying;
 * writes go to SQLite in the background.
 */
class PlaybackHistory(context: Context) : SQLiteOpenHelper(context, "playback.db", null, 3) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _entries = MutableStateFlow<Map<String, PlaybackEntry>>(emptyMap())
    val entries: StateFlow<Map<String, PlaybackEntry>> = _entries.asStateFlow()

    init {
        scope.launch { _entries.value = readAll() }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE playback (" +
                "key TEXT PRIMARY KEY, position_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL, " +
                "last_played INTEGER NOT NULL, finished INTEGER NOT NULL, " +
                "audio_delay_ms INTEGER NOT NULL DEFAULT 0, sub_delay_ms INTEGER NOT NULL DEFAULT 0, " +
                "play_count INTEGER NOT NULL DEFAULT 0)",
        )
        createBookmarks(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE playback ADD COLUMN audio_delay_ms INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE playback ADD COLUMN sub_delay_ms INTEGER NOT NULL DEFAULT 0")
            createBookmarks(db)
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE playback ADD COLUMN play_count INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun createBookmarks(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT NOT NULL, " +
                "position_ms INTEGER NOT NULL, created INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX bookmarks_key ON bookmarks(key)")
    }

    /** Reads one entry straight from disk; safe before the in-memory copy has loaded. */
    fun load(key: String): PlaybackEntry? =
        _entries.value[key] ?: readableDatabase.query(
            "playback", null, "key = ?", arrayOf(key), null, null, null,
        ).use { c -> if (c.moveToFirst()) c.toEntry() else null }

    fun save(update: PlaybackEntry) {
        // Progress saves don't know the play count; keep the one already recorded.
        val entry = update.copy(playCount = maxOf(update.playCount, _entries.value[update.key]?.playCount ?: 0))
        _entries.update { it + (entry.key to entry) }
        scope.launch {
            val values = ContentValues().apply {
                put("key", entry.key)
                put("position_ms", entry.positionMs)
                put("duration_ms", entry.durationMs)
                put("last_played", entry.lastPlayed)
                put("finished", if (entry.finished) 1 else 0)
                put("audio_delay_ms", entry.audioDelayMs)
                put("sub_delay_ms", entry.subDelayMs)
                put("play_count", entry.playCount)
            }
            writableDatabase.insertWithOnConflict("playback", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** Counts one more play of [key] (creating its row if this is the first time). */
    fun recordPlay(key: String) {
        val current = load(key)
        save(
            (current ?: PlaybackEntry(key, 0, 0, System.currentTimeMillis(), finished = false))
                .let { it.copy(playCount = it.playCount + 1, lastPlayed = System.currentTimeMillis()) },
        )
    }

    /** Forgets every resume point, watched time and play count (bookmarks are kept). */
    fun clearAll() {
        _entries.value = emptyMap()
        scope.launch { writableDatabase.delete("playback", null, null) }
    }

    fun bookmarks(key: String): List<Bookmark> =
        readableDatabase.query("bookmarks", null, "key = ?", arrayOf(key), null, null, "position_ms").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Bookmark(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            key = key,
                            positionMs = c.getLong(c.getColumnIndexOrThrow("position_ms")),
                            created = c.getLong(c.getColumnIndexOrThrow("created")),
                        ),
                    )
                }
            }
        }

    fun addBookmark(key: String, positionMs: Long): Long = writableDatabase.insert(
        "bookmarks", null,
        ContentValues().apply {
            put("key", key)
            put("position_ms", positionMs)
            put("created", System.currentTimeMillis())
        },
    )

    fun deleteBookmark(id: Long) {
        writableDatabase.delete("bookmarks", "id = ?", arrayOf(id.toString()))
    }

    private fun readAll(): Map<String, PlaybackEntry> =
        readableDatabase.query("playback", null, null, null, null, null, null).use { c ->
            buildMap { while (c.moveToNext()) c.toEntry().let { put(it.key, it) } }
        }

    private fun Cursor.toEntry() = PlaybackEntry(
        key = getString(getColumnIndexOrThrow("key")),
        positionMs = getLong(getColumnIndexOrThrow("position_ms")),
        durationMs = getLong(getColumnIndexOrThrow("duration_ms")),
        lastPlayed = getLong(getColumnIndexOrThrow("last_played")),
        finished = getInt(getColumnIndexOrThrow("finished")) != 0,
        audioDelayMs = getLong(getColumnIndexOrThrow("audio_delay_ms")),
        subDelayMs = getLong(getColumnIndexOrThrow("sub_delay_ms")),
        playCount = getInt(getColumnIndexOrThrow("play_count")),
    )
}
