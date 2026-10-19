package app.vidro.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.max

/** Cover pictures for the music screen, the notification and the lock screen. */
object Artwork {

    private const val MAX_SIZE = 720

    /** The file's own album art; for a video without one, a frame from early in the video. */
    fun load(context: Context, item: PlayItem): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (item.isContent) retriever.setDataSource(context, Uri.parse(item.target)) else retriever.setDataSource(item.target)
            retriever.embeddedPicture?.let { decode(it, MAX_SIZE) }
                ?: if (item.isAudio) null else videoFrame(retriever)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Embedded album art of an audio file, sized down for lists. */
    fun embedded(path: String, size: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.embeddedPicture?.let { decode(it, size) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun videoFrame(retriever: MediaMetadataRetriever): Bitmap? {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val frame = retriever.getFrameAtTime(durationMs * 150, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        return scaled(frame, MAX_SIZE)
    }

    private fun decode(bytes: ByteArray, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return scaled(bitmap, size)
    }

    private fun scaled(bitmap: Bitmap, size: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= size) return bitmap
        val ratio = size.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
    }
}
