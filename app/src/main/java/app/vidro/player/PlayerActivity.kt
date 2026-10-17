package app.vidro.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.vidro.R
import app.vidro.VidroApp
import app.vidro.ui.theme.VidroTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class PlayerActivity : AppCompatActivity() {

    private var controller: PlayerController? = null
    private val inPip = MutableStateFlow(false)

    private val pipSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    // Buttons inside the picture-in-picture window.
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val c = controller ?: return
            when (intent.getIntExtra(EXTRA_PIP_CONTROL, 0)) {
                PIP_PLAY_PAUSE -> c.engine.togglePause()
                PIP_NEXT -> c.next()
                PIP_PREVIOUS -> c.previous()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val controller = obtainController(intent, recreated = savedInstanceState != null)
        if (controller == null) {
            finish()
            return
        }
        bind(controller)
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP_CONTROL), ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            VidroTheme(darkTheme = true) {
                val pip by inPip.collectAsState()
                PlayerScreen(
                    controller = controller,
                    inPip = pip,
                    onBack = { finish() },
                    onEnterPip = if (pipSupported) ({ enterPip() }) else null,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Coming back from the notification keeps the current session; a new video replaces it.
        if (intent.getBooleanExtra(EXTRA_REATTACH, false)) return
        val items = queueFrom(intent) ?: return
        // This activity stays; only its session is swapped, so the old one must not close it.
        controller?.let {
            it.onQueueFinished = {}
            it.detachActivity(this)
        }
        createController(items.first, items.second)
        setIntent(intent)
        recreate()
    }

    private fun bind(controller: PlayerController) {
        this.controller = controller
        controller.onQueueFinished = { finish() }
        controller.attachActivity(this)
        // Keep the screen awake only while something is actually playing.
        lifecycleScope.launch {
            controller.engine.state.map { !it.paused && it.loaded }.distinctUntilChanged().collect { playing ->
                if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        if (pipSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            lifecycleScope.launch {
                combine(controller.engine.state, controller.settings) { s, settings ->
                    Triple(s.paused, (s.displayAspect * 100).toInt(), settings.autoPip)
                }.distinctUntilChanged().collect { runCatching { setPictureInPictureParams(pipParams()) } }
            }
        }
    }

    /** Reuses the running session when returning to it; otherwise starts a new one from the intent. */
    private fun obtainController(intent: Intent, recreated: Boolean): PlayerController? {
        val existing = PlayerHolder.controller
        if (existing != null && (recreated || intent.getBooleanExtra(EXTRA_REATTACH, false))) return existing
        val (items, start) = queueFrom(intent) ?: return existing
        return createController(items, start)
    }

    private fun createController(items: List<PlayItem>, start: Int): PlayerController {
        val app = application as VidroApp
        // A few milliseconds: the engine needs subtitle fonts/encoding before it starts.
        val settings = runBlocking { app.playerSettings.settings.first() }
        return PlayerController(app, app.history, app.playerSettings, settings, items, start).also { PlayerHolder.replace(it) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val c = controller ?: return
        // Android 12+ enters automatically via setAutoEnterEnabled; older versions need the call.
        if (pipSupported && Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            c.settings.value.autoPip && !c.engine.state.value.paused
        ) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onStop() {
        // Closing the PiP window or leaving the app: keep going only if background play is on.
        if (!isChangingConfigurations) controller?.onScreenHidden()
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        controller?.let { c ->
            c.detachActivity(this)
            // Back / swipe-away ends playback; a system-initiated destroy keeps the session alive.
            if (isFinishing) PlayerHolder.clear(c)
        }
        controller = null
        PlayerKeys.handler = null
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val action = when (event.keyCode) {
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerKeys.Action.PlayPause
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_BUTTON_R2 -> PlayerKeys.Action.Forward
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_BUTTON_L2 -> PlayerKeys.Action.Back
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_BUTTON_R1 -> PlayerKeys.Action.Next
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_BUTTON_L1 -> PlayerKeys.Action.Previous
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                    audio.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI,
                    )
                    return true
                }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_Y -> PlayerKeys.Action.ShowControls
                else -> null
            }
            if (action != null && PlayerKeys.handler != null) {
                PlayerKeys.handler?.invoke(action)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun enterPip() {
        if (!pipSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pipParams(): PictureInPictureParams {
        val c = controller
        val state = c?.engine?.state?.value
        // Android only accepts ratios between 1:2.39 and 2.39:1.
        val aspect = (state?.displayAspect ?: (16f / 9f)).coerceIn(1f / 2.39f, 2.39f)
        fun action(code: Int, icon: Int, title: Int) = RemoteAction(
            Icon.createWithResource(this, icon), getString(title), getString(title),
            PendingIntent.getBroadcast(
                this, code,
                Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_PIP_CONTROL, code),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        val paused = state?.paused ?: true
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((aspect * 1000).toInt(), 1000))
            .setActions(
                listOf(
                    action(PIP_PREVIOUS, R.drawable.ic_skip_previous, R.string.player_previous),
                    if (paused) action(PIP_PLAY_PAUSE, R.drawable.ic_play, R.string.player_play)
                    else action(PIP_PLAY_PAUSE, R.drawable.ic_pause, R.string.player_pause),
                    action(PIP_NEXT, R.drawable.ic_skip_next, R.string.player_next),
                ),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(c?.settings?.value?.autoPip == true && !paused)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Builds the play queue from our own extras (library) or a VIEW intent (other apps). */
    private fun queueFrom(intent: Intent): Pair<List<PlayItem>, Int>? {
        intent.getStringArrayListExtra(EXTRA_QUEUE)?.takeIf { it.isNotEmpty() }?.let { paths ->
            return paths.map { PlayItem(it, titleOf(it)) } to intent.getIntExtra(EXTRA_INDEX, 0)
        }
        val uri = intent.data ?: return null
        return when (uri.scheme) {
            "file" -> uri.path?.let { listOf(PlayItem(it, titleOf(it))) to 0 }
            "content" -> {
                // Prefer the real path when MediaStore knows it, so resume points match the library.
                val path = pathFromMediaStore(uri)
                if (path != null) listOf(PlayItem(path, titleOf(path))) to 0
                else listOf(PlayItem(uri.toString(), displayName(uri) ?: titleOf(uri.lastPathSegment.orEmpty()))) to 0
            }
            else -> null
        }
    }

    private fun pathFromMediaStore(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.takeIf { File(it).canRead() }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.let(::titleOf)

    companion object {
        private const val EXTRA_QUEUE = "app.vidro.extra.QUEUE"
        private const val EXTRA_INDEX = "app.vidro.extra.INDEX"
        const val EXTRA_REATTACH = "app.vidro.extra.REATTACH"
        private const val ACTION_PIP_CONTROL = "app.vidro.action.PIP_CONTROL"
        private const val EXTRA_PIP_CONTROL = "control"
        private const val PIP_PLAY_PAUSE = 1
        private const val PIP_NEXT = 2
        private const val PIP_PREVIOUS = 3

        fun intent(context: Context, queue: List<String>, index: Int): Intent =
            Intent(context, PlayerActivity::class.java)
                .putStringArrayListExtra(EXTRA_QUEUE, ArrayList(queue))
                .putExtra(EXTRA_INDEX, index)

        private fun titleOf(pathOrName: String) = pathOrName.substringAfterLast('/').substringBeforeLast('.')
    }
}
