package app.vidro.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import android.text.format.DateFormat
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenLockLandscape
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.vidro.R
import app.vidro.ui.components.SeekBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CONTROLS_TIMEOUT_MS = 3500L

/** Transient feedback shown while a gesture is in progress or after a quick action. */
private sealed interface Feedback {
    data class Brightness(val level: Float) : Feedback
    data class Volume(val level: Float, val max: Float) : Feedback
    data class Seek(val target: Double, val delta: Double) : Feedback
    data class Zoom(val scale: Float) : Feedback
    data class Message(val text: String) : Feedback
}

/** Double-tap seeking on one side; [side] is -1 (back) or 1 (forward), counted in seconds. */
private data class TapSeek(val side: Int, val seconds: Int, val stamp: Long)

private enum class DragMode { Seek, Brightness, Volume, Ignore }

private enum class LockMode { None, Locked, Kids }

@Composable
fun PlayerScreen(controller: PlayerController, inPip: Boolean, onBack: () -> Unit, onEnterPip: (() -> Unit)?, onListen: () -> Unit) {
    // Panels speak the app's language; the video surface, timeline and gestures stay physically
    // left-to-right in every language, as in all video players.
    val appDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // One surface for both modes: recreating it when entering picture-in-picture left mpv
        // drawing to a dead surface (black window). Only the overlays come and go.
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(controller.engine)
            if (!inPip) PlayerContent(controller, appDirection, onBack, onEnterPip, onListen)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerContent(
    controller: PlayerController,
    appDirection: LayoutDirection,
    onBack: () -> Unit,
    onEnterPip: (() -> Unit)?,
    onListen: () -> Unit,
) {
    val engine = controller.engine
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val state by engine.state.collectAsState()
    val settings by controller.settings.collectAsState()
    val index by controller.index.collectAsState()
    val resumedFrom by controller.resumedFrom.collectAsState()
    val resumePrompt by controller.resumePrompt.collectAsState()
    val error by controller.error.collectAsState()
    val decoder by controller.decoder.collectAsState()
    val aspect by controller.aspect.collectAsState()
    val rotation by controller.rotation.collectAsState()
    val bookmarks by controller.bookmarks.collectAsState()
    val sleepAt by controller.sleepAt.collectAsState()
    val item = controller.items[index]

    var controlsVisible by remember { mutableStateOf(true) }
    var lock by remember { mutableStateOf(LockMode.None) }
    var interaction by remember { mutableLongStateOf(0L) }
    var scrubTarget by remember { mutableStateOf<Double?>(null) }
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var tapSeek by remember { mutableStateOf<TapSeek?>(null) }
    var speeding by remember { mutableStateOf(false) }
    var speedBeforeHold by remember { mutableStateOf(1.0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var showRemaining by remember { mutableStateOf(false) }
    var panelStack by remember { mutableStateOf<List<Panel>>(emptyList()) }

    val currentState by rememberUpdatedState(state)
    val currentSettings by rememberUpdatedState(settings)
    val touch = { interaction = System.currentTimeMillis() }
    val messages = PlayerMessages.current()

    val panelOpen = panelStack.isNotEmpty()
    val actions = remember(onEnterPip) {
        PanelActions(
            open = { panel -> panelStack = panelStack + panel },
            close = { panelStack = emptyList() },
            back = { panelStack = panelStack.dropLast(1) },
            enterPip = onEnterPip?.let { pip -> { panelStack = emptyList(); pip() } },
            listen = { panelStack = emptyList(); onListen() },
            kidsLock = {
                panelStack = emptyList()
                lock = LockMode.Kids
                controlsVisible = false
            },
            // A system toast, so it shows above an open panel too.
            toast = { text -> context.showToast(text) },
        )
    }

    BackHandler(enabled = panelOpen) { panelStack = panelStack.dropLast(1) }
    BackHandler(enabled = !panelOpen && lock != LockMode.None) { controlsVisible = true; touch() }

    // Auto-hide controls while playing.
    LaunchedEffect(controlsVisible, state.paused, interaction, scrubTarget, lock, panelOpen) {
        if (controlsVisible && !state.paused && scrubTarget == null && !panelOpen) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(tapSeek) {
        if (tapSeek != null) {
            delay(700)
            tapSeek = null
        }
    }
    LaunchedEffect(feedback) {
        if (feedback is Feedback.Message || feedback is Feedback.Zoom) {
            delay(1200)
            feedback = null
        }
    }
    LaunchedEffect(resumedFrom) {
        if (resumedFrom != null) {
            delay(6000)
            controller.dismissResume()
        }
    }
    // A new video starts unzoomed.
    LaunchedEffect(index) {
        zoom = 1f; panX = 0f; panY = 0f
    }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val edgePx = with(LocalDensity.current) { 48.dp.toPx() }
            val gesturesOn = lock == LockMode.None

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(gesturesOn, settings.gestureDoubleTap, settings.gestureLongPress) {
                        val seekStep = { currentSettings.doubleTapSeconds }
                        detectTapGestures(
                            onTap = { offset ->
                                val pending = tapSeek
                                val side = sideOf(offset.x, widthPx)
                                if (gesturesOn && pending != null && side == pending.side) {
                                    // Taps right after a double-tap keep seeking, like YouTube.
                                    engine.seekBy((side * seekStep()).toDouble())
                                    tapSeek = pending.copy(seconds = pending.seconds + seekStep(), stamp = System.currentTimeMillis())
                                } else {
                                    controlsVisible = !controlsVisible
                                    touch()
                                }
                            },
                            onDoubleTap = if (gesturesOn && settings.gestureDoubleTap) { offset ->
                                when (val side = sideOf(offset.x, widthPx)) {
                                    0 -> engine.togglePause()
                                    else -> {
                                        engine.seekBy((side * seekStep()).toDouble())
                                        tapSeek = TapSeek(side, seekStep(), System.currentTimeMillis())
                                    }
                                }
                            } else null,
                            onLongPress = if (gesturesOn && settings.gestureLongPress) { _ ->
                                if (!currentState.paused) {
                                    speedBeforeHold = currentState.speed
                                    speeding = true
                                    engine.setSpeed(currentSettings.longPressSpeed.toDouble())
                                }
                            } else null,
                            onPress = {
                                tryAwaitRelease()
                                if (speeding) {
                                    speeding = false
                                    engine.setSpeed(speedBeforeHold)
                                }
                            },
                        )
                    }
                    .pointerInput(gesturesOn, settings.gestureSeek, settings.gestureBrightness, settings.gestureVolume, settings.volumeBoost) {
                        if (!gesturesOn) return@pointerInput
                        var mode: DragMode? = null
                        var total = Offset.Zero
                        var startPos = 0.0
                        var startBrightness = 0.5f
                        var startVolume = 0f
                        var lastTarget = 0.0
                        val maxSystem = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val maxLevel = if (settings.volumeBoost) 2f else 1f
                        detectDragGestures(
                            onDragStart = { start ->
                                total = Offset.Zero
                                // Leave the screen edges to the system's own back / home gestures.
                                mode = if (start.y < edgePx || start.y > heightPx - edgePx) DragMode.Ignore else null
                                startPos = currentState.positionSec
                                startBrightness = activity?.currentBrightness() ?: 0.5f
                                // Volume level 0..1 is the system volume; 1..2 is mpv's boost above 100%.
                                val system = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxSystem
                                startVolume = if (currentState.volume > 100) 1f + ((currentState.volume - 100) / 100).toFloat() else system
                                touch()
                            },
                            onDragEnd = {
                                if (mode == DragMode.Seek) engine.endScrub(lastTarget)
                                mode = null
                                scrubTarget = null
                                feedback = null
                            },
                            onDragCancel = {
                                if (mode == DragMode.Seek) engine.endScrub(lastTarget)
                                mode = null
                                scrubTarget = null
                                feedback = null
                            },
                            onDrag = { change, delta ->
                                change.consume()
                                total += delta
                                if (mode == null) {
                                    mode = when {
                                        abs(total.x) > abs(total.y) -> if (settings.gestureSeek) DragMode.Seek else DragMode.Ignore
                                        change.position.x < widthPx / 2 -> if (settings.gestureBrightness) DragMode.Brightness else DragMode.Ignore
                                        else -> if (settings.gestureVolume) DragMode.Volume else DragMode.Ignore
                                    }
                                    if (mode == DragMode.Seek) {
                                        lastTarget = startPos
                                        engine.beginScrub()
                                    }
                                }
                                when (mode) {
                                    DragMode.Seek -> {
                                        val duration = currentState.durationSec
                                        if (duration > 0) {
                                            val perWidth = (duration * 0.25).coerceIn(60.0, 600.0)
                                            val target = (startPos + total.x / widthPx * perWidth).coerceIn(0.0, duration)
                                            lastTarget = target
                                            scrubTarget = target
                                            engine.scrubTo(target)
                                            feedback = Feedback.Seek(target, target - startPos)
                                        }
                                    }
                                    DragMode.Brightness -> {
                                        val level = (startBrightness - total.y / (heightPx * 0.7f)).coerceIn(0.01f, 1f)
                                        activity?.setBrightness(level)
                                        feedback = Feedback.Brightness(level)
                                    }
                                    DragMode.Volume -> {
                                        val level = (startVolume - total.y / (heightPx * 0.7f)).coerceIn(0f, maxLevel)
                                        if (level <= 1f) {
                                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level * maxSystem).roundToInt(), 0)
                                            if (currentState.volume != 100.0) engine.setVolume(100.0)
                                        } else {
                                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystem, 0)
                                            engine.setVolume(100.0 + (level - 1f) * 100.0)
                                        }
                                        feedback = Feedback.Volume(level, maxLevel)
                                    }
                                    else -> Unit
                                }
                            },
                        )
                    }
                    .pointerInput(gesturesOn, settings.gestureZoom) {
                        if (!gesturesOn || !settings.gestureZoom) return@pointerInput
                        // Two-finger pinch zooms and pans the picture; one finger is left to the other gestures.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                                    val pan = event.calculatePan()
                                    val limit = (zoom - 1f) / 2f
                                    panX = (panX + pan.x / (widthPx * zoom)).coerceIn(-limit, limit)
                                    panY = (panY + pan.y / (heightPx * zoom)).coerceIn(-limit, limit)
                                    if (zoom == 1f) { panX = 0f; panY = 0f }
                                    engine.setZoom(zoom, panX, panY)
                                    feedback = Feedback.Zoom(zoom)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            )

            tapSeek?.let { TapSeekOverlay(it) }
        }

        if (!state.loaded && !error || state.buffering) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        FeedbackOverlay(feedback)

        AnimatedVisibility(
            visible = speeding,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(PlayerInsets.only(WindowInsetsSides.Top)).padding(top = 16.dp),
        ) {
            Pill {
                Icon(Icons.Rounded.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(speedLabel(settings.longPressSpeed.toDouble()), style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && lock == LockMode.None && !error && resumePrompt == null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(250)),
        ) {
            Controls(
                title = item.title,
                state = state,
                scrubTarget = scrubTarget,
                showRemaining = showRemaining,
                showClock = settings.showClock,
                sleepAt = sleepAt,
                hasPrevious = controller.hasPrevious || state.positionSec > 5,
                hasNext = controller.hasNext,
                decoder = decoder,
                rotation = rotation,
                zoomed = zoom > 1f,
                markers = state.chapters.map { it.timeSec } to bookmarks.map { it.positionMs / 1000.0 },
                onBack = onBack,
                onPlayPause = { engine.togglePause(); touch() },
                onPrevious = { controller.previous(); touch() },
                onNext = { controller.next(); touch() },
                onScrubStart = { engine.beginScrub() },
                onScrub = { target ->
                    scrubTarget = target
                    engine.scrubTo(target)
                    touch()
                },
                onScrubEnd = { target, fine ->
                    engine.endScrub(target, fine)
                    scrubTarget = null
                    touch()
                },
                onToggleRemaining = { showRemaining = !showRemaining },
                onSubtitles = { actions.open(Panel.Subtitles) },
                onAudio = { actions.open(Panel.Audio) },
                onMore = { actions.open(Panel.More) },
                onSleep = { actions.open(Panel.Sleep) },
                onLock = { lock = LockMode.Locked; feedback = Feedback.Message(messages.locked) },
                onAspect = {
                    // Cycles the built-in modes; the custom ratio is chosen from the More panel.
                    val next = if (aspect == AspectMode.Custom) AspectMode.Fit
                    else AspectMode.entries[(aspect.ordinal + 1) % AspectMode.Custom.ordinal]
                    controller.setAspect(next)
                    feedback = Feedback.Message(messages.aspect(next))
                    touch()
                },
                onRotate = {
                    controller.cycleRotation()
                    feedback = Feedback.Message(messages.rotation(RotationMode.entries[(rotation.ordinal + 1) % RotationMode.entries.size]))
                    touch()
                },
                onPip = onEnterPip,
                onDecoder = {
                    val next = if (decoder == DecoderMode.Hardware) DecoderMode.Software else DecoderMode.Hardware
                    controller.setDecoder(next)
                    feedback = Feedback.Message(messages.decoder(next))
                    touch()
                },
                onResetZoom = {
                    zoom = 1f; panX = 0f; panY = 0f
                    engine.setZoom(1f, 0f, 0f)
                    touch()
                },
            )
        }

        // Locked: a tap reveals only the unlock button. Kids lock needs a long hold instead.
        AnimatedVisibility(
            visible = lock != LockMode.None && controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).windowInsetsPadding(PlayerInsets).padding(start = 24.dp),
        ) {
            if (lock == LockMode.Kids) {
                HoldToUnlock(stringResource(R.string.kids_hold)) { lock = LockMode.None; touch() }
            } else {
                RoundButton(Icons.Rounded.LockOpen, stringResource(R.string.player_unlock), size = 56) {
                    lock = LockMode.None
                    touch()
                }
            }
        }

        val resumeAt = resumedFrom
        AnimatedVisibility(
            visible = resumeAt != null && lock == LockMode.None,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(PlayerInsets)
                .padding(start = 16.dp, bottom = 136.dp),
        ) {
            Pill(padding = 4.dp) {
                Text(
                    stringResource(R.string.player_resumed, formatTime(resumeAt ?: 0.0)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 12.dp),
                )
                TextButton(onClick = { controller.startOver(); touch() }) {
                    Text(stringResource(R.string.player_start_over), color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        resumePrompt?.let { at ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(28.dp))
                    .background(PanelColor.copy(alpha = 0.95f))
                    .padding(24.dp),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides appDirection) {
                    Text(stringResource(R.string.resume_question, formatTime(at)), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { controller.startOver() }) { Text(stringResource(R.string.player_start_over)) }
                        TextButton(onClick = { controller.acceptResume() }) { Text(stringResource(R.string.resume_continue)) }
                    }
                }
            }
        }

        if (error) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            ) {
                Text(stringResource(R.string.player_error), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(item.title, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                    if (controller.hasNext) TextButton(onClick = { controller.next() }) { Text(stringResource(R.string.player_next)) }
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides appDirection) {
            PanelHost(visible = panelOpen, onDismiss = { panelStack = emptyList() }) {
                // Keyed so each panel opens scrolled to its top.
                panelStack.lastOrNull()?.let { panel -> key(panel) { PanelContent(panel, controller, actions, this) } }
            }
        }
    }

    // Keyboard, remote and game controller support.
    LaunchedEffect(Unit) {
        PlayerKeys.handler = { action ->
            when (action) {
                PlayerKeys.Action.PlayPause -> engine.togglePause()
                PlayerKeys.Action.Forward -> engine.seekBy(currentSettings.doubleTapSeconds.toDouble())
                PlayerKeys.Action.Back -> engine.seekBy(-currentSettings.doubleTapSeconds.toDouble())
                PlayerKeys.Action.Next -> controller.next()
                PlayerKeys.Action.Previous -> controller.previous()
                PlayerKeys.Action.ShowControls -> { controlsVisible = true; touch() }
            }
        }
    }
}

/** Bridge from the activity's key events into the composition. */
object PlayerKeys {
    enum class Action { PlayPause, Forward, Back, Next, Previous, ShowControls }
    var handler: ((Action) -> Unit)? = null
}

/** Left third = back, right third = forward, middle = 0. */
private fun sideOf(x: Float, width: Float): Int = when {
    x < width / 3 -> -1
    x > width * 2 / 3 -> 1
    else -> 0
}

@Composable
private fun VideoSurface(engine: MpvEngine) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                PlayerSurface.view = java.lang.ref.WeakReference(this)
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = engine.attachSurface(holder.surface)
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
                        engine.resizeSurface(width, height)
                    override fun surfaceDestroyed(holder: SurfaceHolder) = engine.detachSurface()
                })
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(
    title: String,
    state: PlayerState,
    scrubTarget: Double?,
    showRemaining: Boolean,
    showClock: Boolean,
    sleepAt: Long?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    decoder: DecoderMode,
    rotation: RotationMode,
    zoomed: Boolean,
    markers: Pair<List<Double>, List<Double>>,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Double) -> Unit,
    onScrubEnd: (Double, Boolean) -> Unit,
    onToggleRemaining: () -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onMore: () -> Unit,
    onSleep: () -> Unit,
    onLock: () -> Unit,
    onAspect: () -> Unit,
    onRotate: () -> Unit,
    onPip: (() -> Unit)?,
    onDecoder: () -> Unit,
    onResetZoom: () -> Unit,
) {
    val scrim = Color.Black.copy(alpha = 0.55f)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(scrim, Color.Transparent))),
        )
        Column(Modifier.fillMaxWidth().windowInsetsPadding(PlayerInsets).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                if (zoomed) {
                    IconButton(onClick = onResetZoom) {
                        Icon(Icons.Rounded.ZoomOutMap, contentDescription = stringResource(R.string.player_reset_zoom), tint = Color.White)
                    }
                }
                IconButton(onClick = onSubtitles) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = stringResource(R.string.panel_subtitles), tint = Color.White)
                }
                IconButton(onClick = onAudio) {
                    Icon(Icons.Rounded.Audiotrack, contentDescription = stringResource(R.string.panel_audio), tint = Color.White)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 56.dp),
            ) {
                if (showClock) BatteryClock()
                if (sleepAt != null) SleepChip(sleepAt, onSleep)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            modifier = Modifier.align(Alignment.Center),
        ) {
            RoundButton(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), enabled = hasPrevious, onClick = onPrevious)
            RoundButton(
                if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                stringResource(if (state.paused) R.string.player_play else R.string.player_pause),
                size = 76,
                onClick = onPlayPause,
            )
            RoundButton(Icons.Rounded.SkipNext, stringResource(R.string.player_next), enabled = hasNext, onClick = onNext)
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(190.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, scrim))),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(PlayerInsets)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val duration = state.durationSec.coerceAtLeast(0.0)
            val position = (scrubTarget ?: state.positionSec).coerceIn(0.0, duration)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeText(formatTime(position))
                SeekBar(
                    position = position,
                    duration = duration,
                    onScrubStart = onScrubStart,
                    onScrub = onScrub,
                    onScrubEnd = onScrubEnd,
                    markers = markers.first,
                    highlights = markers.second,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                TimeText(
                    if (showRemaining) "-" + formatTime(duration - position) else formatTime(duration),
                    modifier = Modifier.clickable(onClick = onToggleRemaining),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolButton(Icons.Rounded.Lock, stringResource(R.string.player_lock), onLock)
                ToolButton(
                    when (rotation) {
                        RotationMode.FollowVideo -> Icons.Rounded.ScreenRotation
                        RotationMode.Landscape -> Icons.Rounded.ScreenLockLandscape
                        RotationMode.Portrait -> Icons.Rounded.ScreenLockPortrait
                    },
                    stringResource(R.string.player_rotation),
                    onRotate,
                )
                ToolButton(Icons.Rounded.AspectRatio, stringResource(R.string.player_aspect), onAspect)
                if (onPip != null) ToolButton(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.pip), onPip)
                Spacer(Modifier.weight(1f))
                BarPill(text = speedLabel(state.speed), onClick = onAudio)
                // Shows the decoder actually running (mpv may fall back to software on its own).
                val label = if (state.loaded) (if (state.isHardware) "HW" else "SW") else if (decoder == DecoderMode.Hardware) "HW" else "SW"
                BarPill(text = label, onClick = onDecoder)
                // Labelled so it's easy to find; an icon alone in the top bar went unnoticed.
                BarPill(text = stringResource(R.string.panel_more), icon = Icons.Rounded.Apps, onClick = onMore)
            }
        }
    }
}

@Composable
private fun BarPill(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun BatteryClock() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) level * 100 / scale else null
            }
            delay(15_000)
        }
    }
    val time = DateFormat.getTimeFormat(context).format(now)
    Text(
        listOfNotNull(time, battery?.let { "$it%" }).joinToString("  ·  "),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.85f),
    )
}

@Composable
private fun SleepChip(sleepAt: Long, onClick: () -> Unit) {
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sleepAt) {
        while (true) {
            left = (sleepAt - System.currentTimeMillis()).coerceAtLeast(0)
            delay(1000)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(formatTime(left / 1000.0), style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

/** Kids lock: hold for 1.5 s to unlock, so small hands can't stop the video by tapping. */
@Composable
private fun HoldToUnlock(label: String, onUnlock: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFF2A2C30).copy(alpha = 0.92f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.animateTo(1f, tween(1500, easing = LinearEasing))
                        onUnlock()
                    }
                    tryAwaitRelease()
                    job.cancel()
                    scope.launch { progress.snapTo(0f) }
                })
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress.value }, color = MaterialTheme.colorScheme.primary, trackColor = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(36.dp))
            Icon(Icons.Rounded.ChildCare, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun TimeText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = modifier)
}

@Composable
private fun ToolButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description, tint = Color.White) }
}

@Composable
private fun TapSeekOverlay(seek: TapSeek) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val forward = seek.side > 0
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(if (forward) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft)
                .fillMaxHeight()
                .width(maxWidth * 0.36f)
                .clip(
                    if (forward) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                    else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50),
                )
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
                Text(stringResource(R.string.player_seconds, seek.seconds), style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

@Composable
private fun FeedbackOverlay(feedback: Feedback?) {
    Box(Modifier.fillMaxSize()) {
        when (feedback) {
            is Feedback.Brightness -> LevelBar(Icons.Rounded.BrightnessMedium, feedback.level, 1f, AbsoluteAlignment.CenterLeft)
            is Feedback.Volume -> LevelBar(Icons.AutoMirrored.Rounded.VolumeUp, feedback.level, feedback.max, AbsoluteAlignment.CenterRight)
            is Feedback.Seek -> CenterCard {
                Text(formatTime(feedback.target), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                val sign = if (feedback.delta >= 0) "+" else "-"
                Text(sign + formatTime(abs(feedback.delta)), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
            }
            is Feedback.Zoom -> CenterCard {
                Text("${(feedback.scale * 100).roundToInt()}%", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            }
            is Feedback.Message -> CenterCard {
                Text(feedback.text, style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            null -> Unit
        }
    }
}

@Composable
private fun BoxScope.CenterCard(content: @Composable () -> Unit) {
    // Sits above the centre transport buttons so the two never overlap.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .align(Alignment.TopCenter)
            // Top inset only: the side cutout inset in landscape pushed the card off-centre.
            .windowInsetsPadding(PlayerInsets.only(WindowInsetsSides.Top))
            .padding(top = 80.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) { content() }
}

/** Vertical level for brightness / volume; above 100% (volume boost) the fill turns accent. */
@Composable
private fun BoxScope.LevelBar(icon: ImageVector, level: Float, max: Float, alignment: Alignment) {
    val boosted = level > 1f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .align(alignment)
            .windowInsetsPadding(PlayerInsets)
            .padding(horizontal = 40.dp)
            .width(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(8.dp).height(150.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight((level / max).coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(if (boosted) MaterialTheme.colorScheme.primary else Color.White),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("${(level * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium, color = if (boosted) MaterialTheme.colorScheme.primary else Color.White)
    }
}

/** Localised one-word labels for transient messages, resolved once per composition. */
private class PlayerMessages(
    val locked: String,
    private val aspects: Map<AspectMode, String>,
    private val rotations: Map<RotationMode, String>,
    private val decoders: Map<DecoderMode, String>,
) {
    fun aspect(mode: AspectMode) = aspects.getValue(mode)
    fun rotation(mode: RotationMode) = rotations.getValue(mode)
    fun decoder(mode: DecoderMode) = decoders.getValue(mode)

    companion object {
        @Composable
        fun current() = PlayerMessages(
            locked = stringResource(R.string.player_locked),
            aspects = mapOf(
                AspectMode.Fit to stringResource(R.string.aspect_fit),
                AspectMode.Crop to stringResource(R.string.aspect_crop),
                AspectMode.Stretch to stringResource(R.string.aspect_stretch),
                AspectMode.Ratio16x9 to "16:9",
                AspectMode.Ratio4x3 to "4:3",
                AspectMode.Original to stringResource(R.string.aspect_original),
                AspectMode.Custom to stringResource(R.string.panel_aspect),
            ),
            rotations = mapOf(
                RotationMode.FollowVideo to stringResource(R.string.rotation_auto),
                RotationMode.Landscape to stringResource(R.string.rotation_landscape),
                RotationMode.Portrait to stringResource(R.string.rotation_portrait),
            ),
            decoders = mapOf(
                DecoderMode.Hardware to stringResource(R.string.decoder_hw),
                DecoderMode.Software to stringResource(R.string.decoder_sw),
            ),
        )
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Window brightness override, or the system brightness when the player hasn't changed it yet. */
private fun Activity.currentBrightness(): Float {
    val own = window.attributes.screenBrightness
    if (own >= 0) return own
    return runCatching {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f)
}

private fun Activity.setBrightness(level: Float) {
    window.attributes = window.attributes.apply { screenBrightness = level }
}
