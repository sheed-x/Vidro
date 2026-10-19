package app.vidro.ui.music

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.vidro.data.Song
import app.vidro.player.Artwork
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Image request for a song's cover; [size] is the longest edge in pixels. */
data class SongArt(val id: Long, val path: String, val albumId: Long, val size: Int)

/**
 * Loads album art: MediaStore's cached thumbnail on Android 10+, the old album-art table before
 * that, and the picture embedded in the file as a fallback.
 */
class SongArtFetcher(private val data: SongArt, private val options: Options) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bitmap = withContext(Dispatchers.IO) { load() } ?: error("No artwork")
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun load(): Bitmap? {
        val resolver = options.context.contentResolver
        val fromStore = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, data.id)
                resolver.loadThumbnail(uri, Size(data.size, data.size), null)
            } else {
                val uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), data.albumId)
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
        return fromStore ?: Artwork.embedded(data.path, data.size)
    }

    class Factory : Fetcher.Factory<SongArt> {
        override fun create(data: SongArt, options: Options, imageLoader: ImageLoader): Fetcher = SongArtFetcher(data, options)
    }
}

class SongArtKeyer : Keyer<SongArt> {
    override fun key(data: SongArt, options: Options): String = "song-art:${data.id}:${data.size}"
}

/** A song's cover with a quiet note glyph underneath for files without one. */
@Composable
fun SongArtwork(song: Song?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp), pixels: Int = 256) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHighest),
                ),
            ),
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.Center).fillMaxSize(0.42f),
        )
        if (song != null) {
            AsyncImage(
                model = SongArt(song.id, song.path, song.albumId, pixels),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
