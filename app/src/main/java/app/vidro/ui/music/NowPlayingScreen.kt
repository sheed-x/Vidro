package app.vidro.ui.music

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vidro.R
import app.vidro.player.AudioEffects
import app.vidro.player.PlayItem
import app.vidro.player.PlayerController
import app.vidro.player.PlayerHolder
import app.vidro.player.RepeatMode
import app.vidro.player.formatTime
import app.vidro.player.presetName
import app.vidro.player.speedLabel
import app.vidro.ui.components.SeekBar
import app.vidro.ui.components.formatDuration
import app.vidro.ui.theme.LocalIsDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class Sheet { Queue, Sleep, Effects }

/**
 * Full-screen player for songs and for videos played as audio: big cover over its own blurred
 * colours, a precise timeline, transport, and quick tools (speed, sleep timer, equaliser, queue).
 */
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    onWatchVideo: () -> Unit,
    onOpenAlbum: ((PlayItem) -> Unit)?,
    onOpenArtist: ((PlayItem) -> Unit)?,
) {
    val controller by PlayerHolder.current.collectAsState()
    val c = controller
    LaunchedEffect(c) { if (c == null) onBack() }
    if (c == null) return
    BackHandler(onBack = onBack)
    NowPlayingContent(c, onBack, onWatchVideo, onOpenAlbum, onOpenArtist)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingContent(
    controller: PlayerController,
    onBack: () -> Unit,
    onWatchVideo: () -> Unit,
    onOpenAlbum: ((PlayItem) -> Unit)?,
    onOpenArtist: ((PlayItem) -> Unit)?,
) {
    val engine = controller.engine
    val state by engine.state.collectAsState()
    val index by controller.index.collectAsState()
    val queue by controller.queue.collectAsState()
    val artwork by controller.artwork.collectAsState()
    val repeat by controller.repeat.collectAsState()
    val shuffle by controller.shuffle.collectAsState()
    val sleepAt by controller.sleepAt.collectAsState()
    val sleepAtEnd by controller.sleepAtEnd.collectAsState()
    val settings by controller.settings.collectAsState()
    val item = queue.getOrNull(index) ?: return
    var scrubTarget by remember { mutableStateOf<Double?>(null) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var menu by remember { mutableStateOf(false) }
    val dark = LocalIsDark.current
    val surface = MaterialTheme.colorScheme.surface
    val art = remember(artwork) { artwork?.asImageBitmap() }

    Box(Modifier.fillMaxSize().background(surface)) {
        // The cover's own colours, softly blurred, behind everything (plain tint before Android 12).
        if (art != null) {
            Image(
                art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (if (dark) 0.55f else 0.45f) else 0.18f }
                    .blur(90.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to surface.copy(alpha = 0.25f),
                        0.55f to surface.copy(alpha = 0.6f),
                        1f to surface.copy(alpha = 0.92f),
                    ),
                ),
        )

        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val landscape = maxWidth > maxHeight
            val topBar: @Composable () -> Unit = {
                // Top bar: close, where this comes from, more.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.back), modifier = Modifier.size(30.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.now_playing).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (item.isAudio) item.album ?: stringResource(R.string.tab_music) else stringResource(R.string.playing_video_as_audio),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false },
                            shape = MaterialTheme.shapes.medium,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            if (!item.isAudio) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.watch_video)) },
                                    leadingIcon = { Icon(Icons.Rounded.OndemandVideo, contentDescription = null) },
                                    onClick = { menu = false; onWatchVideo() },
                                )
                            }
                            if (item.isAudio && item.album != null && onOpenAlbum != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.go_to_album)) },
                                    leadingIcon = { Icon(Icons.Rounded.Album, contentDescription = null) },
                                    onClick = { menu = false; onOpenAlbum(item) },
                                )
                            }
                            if (item.isAudio && item.artist != null && onOpenArtist != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.go_to_artist)) },
                                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                    onClick = { menu = false; onOpenArtist(item) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.stop_playback)) },
                                leadingIcon = { Icon(Icons.Rounded.StopCircle, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    PlayerHolder.clear(controller)
                                },
                            )
                        }
                    }
                }

            }
            val cover: @Composable (Modifier) -> Unit = { modifier ->
                Cover(
                    art = art,
                    playing = !state.paused,
                    onNext = { if (controller.hasNext) controller.next() },
                    onPrevious = { controller.previous() },
                    modifier = modifier,
                )
            }
            val details: @Composable () -> Unit = {

                // Title and artist; videos get a quick way back to the picture.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000),
                        )
                        Text(
                            item.artist ?: if (item.isAudio) stringResource(R.string.unknown_artist) else formatDuration(item.durationMs).ifEmpty { stringResource(R.string.app_name) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!item.isAudio) {
                        RoundTool(Icons.Rounded.OndemandVideo, stringResource(R.string.watch_video), onClick = onWatchVideo)
                    }
                }

                Spacer(Modifier.height(18.dp))
                val duration = state.durationSec.coerceAtLeast(0.0)
                val position = (scrubTarget ?: state.positionSec).coerceIn(0.0, duration)
                SeekBar(
                    position = position,
                    duration = duration,
                    onScrubStart = { engine.beginScrub() },
                    onScrub = { target -> scrubTarget = target; engine.scrubTo(target) },
                    onScrubEnd = { target, fine -> engine.endScrub(target, fine); scrubTarget = null },
                    activeColor = MaterialTheme.colorScheme.onSurface,
                    inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    bubbleColor = MaterialTheme.colorScheme.inverseSurface,
                    bubbleTextColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Text(formatTime(position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("-" + formatTime(duration - position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ToggleIcon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle), active = shuffle) { controller.toggleShuffle() }
                    IconButton(onClick = { controller.previous() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.player_previous), modifier = Modifier.size(38.dp))
                    }
                    PlayButton(paused = state.paused) { engine.togglePause() }
                    IconButton(onClick = { controller.next() }, enabled = controller.hasNext, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next), modifier = Modifier.size(38.dp))
                    }
                    ToggleIcon(
                        if (repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        stringResource(
                            when (repeat) {
                                RepeatMode.Off -> R.string.repeat_off
                                RepeatMode.One -> R.string.repeat_one
                                RepeatMode.All -> R.string.repeat_all
                            },
                        ),
                        active = repeat != RepeatMode.Off,
                    ) { controller.cycleRepeat() }
                }

                Spacer(Modifier.height(20.dp))
                // Quick tools along the bottom.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    ToolChip(text = speedLabel(state.speed)) {
                        val speeds = listOf(0.75, 1.0, 1.25, 1.5, 2.0)
                        val next = speeds.firstOrNull { it > state.speed + 0.01 } ?: speeds.first()
                        engine.setSpeed(next)
                    }
                    SleepChip(sleepAt, sleepAtEnd) { sheet = Sheet.Sleep }
                    ToolChip(
                        icon = Icons.Rounded.Equalizer,
                        active = settings.effects.enabled && !settings.effects.isNeutral,
                        description = stringResource(R.string.equalizer),
                    ) { sheet = Sheet.Effects }
                    ToolChip(icon = Icons.AutoMirrored.Rounded.QueueMusic, description = stringResource(R.string.panel_queue)) { sheet = Sheet.Queue }
                }
            }
            if (landscape) {
                // Side by side: the cover fills the height, everything else sits next to it.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    cover(Modifier.fillMaxHeight().padding(vertical = 20.dp).aspectRatio(1f))
                    Spacer(Modifier.width(32.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        topBar()
                        Spacer(Modifier.weight(1f))
                        details()
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    topBar()
                    Spacer(Modifier.weight(1f))
                    cover(Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f))
                    Spacer(Modifier.weight(1f))
                    details()
                }
            }
        }
    }

    when (sheet) {
        Sheet.Queue -> QueueSheet(controller, onDismiss = { sheet = null })
        Sheet.Sleep -> SleepSheet(controller, onDismiss = { sheet = null })
        Sheet.Effects -> EffectsSheet(controller, onDismiss = { sheet = null })
        null -> Unit
    }
}

/** Big cover that breathes with playback (smaller when paused) and swipes to the next / previous track. */
@Composable
private fun Cover(
    art: androidx.compose.ui.graphics.ImageBitmap?,
    playing: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(if (playing) 1f else 0.86f, spring(dampingRatio = 0.55f, stiffness = 300f), label = "cover")
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    Box(modifier, contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(28.dp)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.value
                    rotationZ = offset.value / 60f
                    alpha = 1f - (abs(offset.value) / (size.width * 1.4f)).coerceIn(0f, 0.6f)
                }
                .shadow(if (playing) 28.dp else 12.dp, shape)
                .clip(shape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHighest)))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val value = offset.value
                            scope.launch {
                                when {
                                    value < -threshold -> onNext()
                                    value > threshold -> onPrevious()
                                }
                                offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f))
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + delta) }
                        },
                    )
                },
        ) {
            if (art != null) {
                Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.Center).size(96.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayButton(paused: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "play")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(80.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(14.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Icon(
            if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            contentDescription = stringResource(if (paused) R.string.player_play else R.string.player_pause),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(44.dp),
        )
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A small dot under an active toggle, like most music players.
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun RoundTool(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = 12.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ToolChip(
    text: String? = null,
    icon: ImageVector? = null,
    description: String? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        if (icon != null) Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
        if (icon != null && text != null) Spacer(Modifier.width(6.dp))
        if (text != null) Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@Composable
private fun SleepChip(sleepAt: Long?, atEnd: Boolean, onClick: () -> Unit) {
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sleepAt) {
        while (sleepAt != null) {
            left = (sleepAt - System.currentTimeMillis()).coerceAtLeast(0)
            delay(1000)
        }
    }
    ToolChip(
        text = when {
            sleepAt != null -> formatTime(left / 1000.0)
            atEnd -> stringResource(R.string.sleep_end_track)
            else -> null
        },
        icon = Icons.Rounded.Bedtime,
        description = stringResource(R.string.panel_sleep),
        active = sleepAt != null || atEnd,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(controller: PlayerController, onDismiss: () -> Unit) {
    val queue by controller.queue.collectAsState()
    val index by controller.index.collectAsState()
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = index.coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(
            stringResource(R.string.up_next),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding()) {
            itemsIndexed(queue, key = { i, item -> "$i:${item.target}" }) { i, item ->
                val current = i == index
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { controller.playAt(i) }
                        .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                        .padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        if (current) PlayingBars(!controller.engine.state.value.paused, MaterialTheme.colorScheme.primary)
                        else Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.artist?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (!current) {
                        IconButton(onClick = { controller.removeAt(i) }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove_from_queue), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSheet(controller: PlayerController, onDismiss: () -> Unit) {
    val sleepAt by controller.sleepAt.collectAsState()
    val atEnd by controller.sleepAtEnd.collectAsState()
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.panel_sleep),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val minutes = listOf(15, 30, 45, 60, 90)
            minutes.forEach { m ->
                SheetOption(stringResource(R.string.sleep_minutes, m), selected = false) { controller.setSleepTimer(m); onDismiss() }
            }
            SheetOption(stringResource(R.string.sleep_end_track), selected = atEnd) { controller.setSleepAtEndOfVideo(); onDismiss() }
            SheetOption(stringResource(R.string.off), selected = sleepAt == null && !atEnd) { controller.setSleepTimer(null); onDismiss() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EffectsSheet(controller: PlayerController, onDismiss: () -> Unit) {
    val settings by controller.settings.collectAsState()
    val fx = settings.effects
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(R.string.equalizer), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Switch(checked = fx.enabled, onCheckedChange = { on -> controller.updateSettings { it.copy(effects = it.effects.copy(enabled = on)) } })
            }
            Text(
                stringResource(R.string.presets),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioEffects.PRESETS.keys.forEach { key ->
                    FilterChip(
                        selected = fx.enabled && fx.preset == key,
                        onClick = { controller.updateSettings { it.copy(effects = AudioEffects.PRESETS.getValue(key).copy(enabled = true)) } },
                        label = { Text(presetName(key)) },
                        shape = CircleShape,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            modifier = Modifier.weight(1f),
        )
    }
}
