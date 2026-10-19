package app.vidro

import android.app.Application
import app.vidro.data.LibraryRepository
import app.vidro.data.MusicRepository
import app.vidro.data.PlaybackHistory
import app.vidro.data.SettingsRepository
import app.vidro.data.UpdateRepository
import app.vidro.player.PlayerSettingsRepository
import app.vidro.ui.music.SongArtFetcher
import app.vidro.ui.music.SongArtKeyer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder

class VidroApp : Application(), SingletonImageLoader.Factory {

    val library by lazy { LibraryRepository(this) }
    val music by lazy { MusicRepository(this) }
    val settings by lazy { SettingsRepository(this) }
    val history by lazy { PlaybackHistory(this) }
    val playerSettings by lazy { PlayerSettingsRepository(this) }
    val updates by lazy { UpdateRepository(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
                add(SongArtFetcher.Factory())
                add(SongArtKeyer())
            }
            .crossfade(true)
            .build()
}
