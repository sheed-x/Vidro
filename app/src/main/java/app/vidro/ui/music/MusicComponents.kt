package app.vidro.ui.music

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vidro.R
import app.vidro.data.Album
import app.vidro.data.Artist
import app.vidro.data.Song
import app.vidro.player.PlayerHolder
import app.vidro.ui.components.formatDuration
import app.vidro.ui.components.formatSize
import app.vidro.ui.library.isolate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** What's playing right now, for highlighting it in lists: its path and whether it's moving. */
data class NowPlayingMark(val target: String?, val playing: Boolean)

@Composable
fun rememberNowPlayingMark(): NowPlayingMark {
    val controller by PlayerHolder.current.collectAsState()
    val c = controller ?: return NowPlayingMark(null, false)
    val index by c.index.collectAsState()
    val queue by c.queue.collectAsState()
    // Only the paused flag: lists shouldn't recompose on every position tick.
    val paused by remember(c) { c.engine.state.map { it.paused }.distinctUntilChanged() }.collectAsState(true)
    return NowPlayingMark(queue.getOrNull(index)?.target, !paused)
}

@Composable
fun artistName(song: Song): String = song.artist ?: stringResource(R.string.unknown_artist)

/** "1 h 20 min" or "42 min". */
@Composable
fun totalDuration(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return if (minutes >= 60) stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    else stringResource(R.string.duration_minutes, minutes.coerceAtLeast(1))
}

/** Three little bars that dance while the marked song plays and rest while it's paused. */
@Composable
fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phases = listOf(0, 180, 360).map { delay ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520, delayMillis = delay, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar",
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = modifier.size(16.dp)) {
        phases.forEachIndexed { i, phase ->
            val rest = listOf(0.45f, 0.8f, 0.6f)[i]
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight(if (playing) phase.value else rest)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    mark: NowPlayingMark,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Album screens show the track number instead of the cover. */
    trackNumber: Int? = null,
) {
    val current = mark.target == song.path
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (trackNumber != null) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (current) PlayingBars(mark.playing, accent)
                else Text("$trackNumber", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box {
                SongArtwork(song, Modifier.size(52.dp), RoundedCornerShape(14.dp), pixels = 160)
                if (current) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.45f)),
                    ) { PlayingBars(mark.playing, Color.White) }
                }
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (current) FontWeight.SemiBold else null,
                color = if (current) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(artistName(song), song.album.takeIf { trackNumber == null }).joinToString("  ·  ") { isolate(it) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        SongArtwork(
            album.songs.firstOrNull(),
            Modifier.fillMaxWidth().aspectRatio(1f).shadow(6.dp, RoundedCornerShape(20.dp)),
            RoundedCornerShape(20.dp),
            pixels = 420,
        )
        Text(
            album.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
        )
        Text(
            album.artist ?: stringResource(R.string.unknown_artist),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SongArtwork(artist.songs.firstOrNull(), Modifier.size(56.dp), CircleShape, pixels = 160)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(
                    pluralStringResource(R.plurals.song_count, artist.songs.size, artist.songs.size),
                    pluralStringResource(R.plurals.album_count, artist.albumCount, artist.albumCount),
                ).joinToString("  ·  ") { isolate(it) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * iOS-style segmented control in the app's quiet palette: a soft track with a raised pill that
 * glides to the chosen segment.
 */
@Composable
fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        val segment = maxWidth / labels.size
        val offset by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.75f, stiffness = 500f), label = "segment")
        Box(
            Modifier
                .offset(x = segment * offset)
                .width(segment)
                .fillMaxHeight()
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            labels.forEachIndexed { i, label ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable { onSelect(i) },
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Play and Shuffle side by side, as on every music app's album page. */
@Composable
fun PlayShuffleButtons(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        Button(onClick = onPlay, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.play_all), style = MaterialTheme.typography.titleSmall)
        }
        FilledTonalButton(
            onClick = onShuffle,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shuffle_all), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Long-press menu of a song: queue actions, where it belongs, and its details. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    song: Song,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpenAlbum: (() -> Unit)?,
    onOpenArtist: (() -> Unit)?,
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                SongArtwork(song, Modifier.size(64.dp), RoundedCornerShape(16.dp), pixels = 200)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(artistName(song), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.play_next)) { onPlayNext(); onDismiss() }
            SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_queue)) { onAddToQueue(); onDismiss() }
            onOpenAlbum?.let { open -> SheetAction(Icons.Rounded.Album, stringResource(R.string.go_to_album)) { onDismiss(); open() } }
            onOpenArtist?.let { open -> SheetAction(Icons.Rounded.Person, stringResource(R.string.go_to_artist)) { onDismiss(); open() } }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text(
                stringResource(R.string.song_details),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Detail(stringResource(R.string.info_album), song.album ?: stringResource(R.string.unknown_album))
            if (song.year > 0) Detail(stringResource(R.string.info_year), song.year.toString())
            Detail(stringResource(R.string.info_duration), formatDuration(song.durationMs).ifEmpty { stringResource(R.string.unknown) })
            Detail(stringResource(R.string.info_size), formatSize(context, song.size))
            Detail(stringResource(R.string.info_format), song.extension)
            Detail(stringResource(R.string.info_location), song.folderPath)
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 20.dp))
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}
