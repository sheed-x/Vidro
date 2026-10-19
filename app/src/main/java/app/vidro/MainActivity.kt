package app.vidro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.vidro.player.PlayItem
import app.vidro.player.PlayerHolder
import app.vidro.ui.AppRequests
import app.vidro.ui.VidroRoot
import app.vidro.ui.theme.VidroTheme
import java.io.File

// AppCompatActivity (not plain ComponentActivity) so the in-app language choice applies on Android 7-12 too.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            VidroTheme {
                VidroRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** "Show what's playing" from the notification / player, or an audio file opened by another app. */
    private fun handle(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_NOW_PLAYING, false)) {
            AppRequests.openNowPlaying()
            return
        }
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val item = audioItem(uri) ?: return
        PlayerHolder.start(application as VidroApp, listOf(item), 0, audioOnly = true)
        AppRequests.openNowPlaying()
    }

    private fun audioItem(uri: Uri): PlayItem? = when (uri.scheme) {
        "file" -> uri.path?.let { PlayItem(it, it.substringAfterLast('/').substringBeforeLast('.'), isAudio = true) }
        "content" -> {
            // The real path and tags when MediaStore knows the file, so plays count in the library too.
            val known = runCatching {
                contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DATA, MediaStore.Audio.AudioColumns.TITLE, MediaStore.Audio.AudioColumns.ARTIST),
                    null, null, null,
                )?.use { c -> if (c.moveToFirst()) Triple(c.getString(0), c.getString(1), c.getString(2)) else null }
            }.getOrNull()
            val path = known?.first?.takeIf { File(it).canRead() }
            val title = known?.second?.takeIf { it.isNotBlank() } ?: displayName(uri) ?: uri.lastPathSegment.orEmpty()
            val artist = known?.third?.takeIf { it.isNotBlank() && it != "<unknown>" }
            PlayItem(path ?: uri.toString(), title, artist, isAudio = true)
        }
        else -> null
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')

    companion object {
        private const val EXTRA_NOW_PLAYING = "app.vidro.extra.NOW_PLAYING"

        /** Brings the app forward on the music screen of the current session. */
        fun nowPlayingIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_NOW_PLAYING, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
