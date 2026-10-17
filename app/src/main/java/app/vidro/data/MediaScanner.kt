package app.vidro.data

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns

/** Reads the video index from MediaStore. All-files access widens the query to every file by extension. */
object MediaScanner {

    // Extensions that also belong to common non-video files (TypeScript, data dumps…); tiny ones are skipped.
    private val ambiguousExtensions = setOf("ts", "str", "dir", "box", "rec", "sec", "vid", "rp", "rv", "scn")
    private const val AMBIGUOUS_MIN_BYTES = 100 * 1024L

    private const val COLUMN_DURATION = "duration"

    fun scan(context: Context, fullAccess: Boolean): List<Video> {
        val uri: Uri
        val selection: String?
        val args: Array<String>?
        if (fullAccess) {
            uri = MediaStore.Files.getContentUri("external")
            val extensions = VideoExtensions.all.toList()
            selection = extensions.joinToString(" OR ") { "${MediaColumns.DATA} LIKE ?" }
            args = extensions.map { "%.$it" }.toTypedArray()
        } else {
            uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            selection = null
            args = null
        }

        val full = arrayOf(
            MediaColumns._ID, MediaColumns.DATA, MediaColumns.SIZE, MediaColumns.DATE_ADDED,
            MediaColumns.DATE_MODIFIED, COLUMN_DURATION, MediaColumns.WIDTH, MediaColumns.HEIGHT,
        )
        val minimal = arrayOf(
            MediaColumns._ID, MediaColumns.DATA, MediaColumns.SIZE, MediaColumns.DATE_ADDED,
            MediaColumns.DATE_MODIFIED,
        )
        // Older MediaStore versions may reject the extra columns on the Files table; retry without them.
        val cursor = runCatching { context.contentResolver.query(uri, full, selection, args, null) }.getOrNull()
            ?: context.contentResolver.query(uri, minimal, selection, args, null)
            ?: return emptyList()

        return cursor.use { readVideos(it) }
    }

    private fun readVideos(c: Cursor): List<Video> {
        val idCol = c.getColumnIndexOrThrow(MediaColumns._ID)
        val dataCol = c.getColumnIndexOrThrow(MediaColumns.DATA)
        val sizeCol = c.getColumnIndexOrThrow(MediaColumns.SIZE)
        val addedCol = c.getColumnIndexOrThrow(MediaColumns.DATE_ADDED)
        val modifiedCol = c.getColumnIndexOrThrow(MediaColumns.DATE_MODIFIED)
        val durationCol = c.getColumnIndex(COLUMN_DURATION)
        val widthCol = c.getColumnIndex(MediaColumns.WIDTH)
        val heightCol = c.getColumnIndex(MediaColumns.HEIGHT)

        val videos = ArrayList<Video>(c.count)
        val seen = HashSet<String>()
        while (c.moveToNext()) {
            val path = c.getString(dataCol) ?: continue
            val name = path.substringAfterLast('/')
            if (!VideoExtensions.isVideo(name) || !seen.add(path)) continue
            val size = c.getLong(sizeCol)
            val ext = name.substringAfterLast('.').lowercase()
            if (ext in ambiguousExtensions && size < AMBIGUOUS_MIN_BYTES) continue
            videos += Video(
                id = c.getLong(idCol),
                path = path,
                name = name,
                size = size,
                durationMs = if (durationCol >= 0) c.getLong(durationCol) else 0,
                width = if (widthCol >= 0) c.getInt(widthCol) else 0,
                height = if (heightCol >= 0) c.getInt(heightCol) else 0,
                dateAdded = c.getLong(addedCol),
                dateModified = c.getLong(modifiedCol),
            )
        }
        return videos
    }

    /** Fills in length and resolution that MediaStore didn't know, by reading the file's own metadata. */
    fun probe(video: Video): Video? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            video.copy(
                durationMs = duration ?: video.durationMs,
                width = width ?: video.width,
                height = height ?: video.height,
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
