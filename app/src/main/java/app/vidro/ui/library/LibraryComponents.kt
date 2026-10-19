package app.vidro.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vidro.R
import app.vidro.data.PlaybackEntry
import app.vidro.data.SortField
import app.vidro.data.SortOrder
import app.vidro.data.Video
import app.vidro.data.VideoFolder
import app.vidro.data.ViewMode
import app.vidro.player.PlayerActivity
import app.vidro.ui.components.formatDate
import app.vidro.ui.components.formatDuration
import app.vidro.ui.components.formatSize
import app.vidro.ui.components.groupItem
import app.vidro.ui.components.resolutionLabel
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.video.videoFramePercent

/** Display preferences from Settings, available to every list without threading them through. */
data class DisplayPrefs(val showThumbnails: Boolean = true, val showNewBadge: Boolean = true, val showWatchTime: Boolean = true)

val LocalDisplayPrefs = compositionLocalOf { DisplayPrefs() }

/** Where each video was left off, keyed by path; drives the "new" mark and the progress bar. */
val LocalPlaybackHistory = compositionLocalOf { emptyMap<String, PlaybackEntry>() }

/** Returns a function that plays a video, queueing the rest of its folder after it. */
@Composable
fun rememberPlayVideo(viewModel: LibraryViewModel): (Video) -> Unit {
    val context = LocalContext.current
    return remember(viewModel, context) {
        { video ->
            val (queue, index) = viewModel.queueFor(video)
            context.startActivity(PlayerActivity.intent(context, queue, index))
        }
    }
}

/** Watch or listen to a video; offered in its info sheet. Provided by the app root. */
class VideoActions(val play: (Video) -> Unit, val listen: (Video) -> Unit)

val LocalVideoActions = compositionLocalOf<VideoActions?> { null }

/** Space the floating tab bar covers at the bottom; lists pad by it so their end stays reachable. */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

/** Wraps mixed-direction text (e.g. "135 م.ب" next to "1080p") so each part keeps its own order in RTL. */
fun isolate(text: String) = "⁨$text⁩"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VidroTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    LargeTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun VideoThumbnail(
    video: Video?,
    modifier: Modifier = Modifier,
    showDuration: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val prefs = LocalDisplayPrefs.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(
            Icons.Rounded.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.align(Alignment.Center).size(28.dp),
        )
        if (video != null) {
            if (prefs.showThumbnails) {
                val request = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(video.file)
                    .videoFramePercent(0.15)
                    .memoryCacheKey("${video.path}|${video.dateModified}")
                    .build()
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val progress = LocalPlaybackHistory.current[video.path]?.progress ?: 0f
            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.35f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            val duration = formatDuration(video.durationMs)
            if (showDuration && duration.isNotEmpty()) {
                Text(
                    text = duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Small accent dot + label; the only color in a video row. */
@Composable
private fun NewBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = stringResource(R.string.new_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun videoDetails(video: Video): String {
    val context = LocalContext.current
    return listOfNotNull(resolutionLabel(video.width, video.height), formatSize(context, video.size))
        .joinToString("  ·  ") { isolate(it) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(video: Video, onClick: () -> Unit, onLongClick: () -> Unit) {
    val showNew = LocalDisplayPrefs.current.showNewBadge && video.path !in LocalPlaybackHistory.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        VideoThumbnail(video, Modifier.width(136.dp).aspectRatio(16f / 9f))
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showNew) {
                    NewBadge()
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = videoDetails(video),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WatchedLine(video)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(video: Video, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val showNew = LocalDisplayPrefs.current.showNewBadge && video.path !in LocalPlaybackHistory.current
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
    ) {
        VideoThumbnail(video, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            if (showNew) {
                NewBadge()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = videoDetails(video),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(Modifier.padding(horizontal = 4.dp)) { WatchedLine(video) }
    }
}

/** "Watched 4 of 10 min" for videos with playback history (can be turned off in Settings). */
@Composable
private fun WatchedLine(video: Video) {
    if (!LocalDisplayPrefs.current.showWatchTime) return
    val entry = LocalPlaybackHistory.current[video.path] ?: return
    val total = (maxOf(entry.durationMs, video.durationMs) + 59_999) / 60_000
    val text = when {
        entry.finished -> stringResource(R.string.watched_fully)
        entry.positionMs <= 0 -> return
        total > 0 -> stringResource(R.string.watched_of, ((entry.positionMs + 59_999) / 60_000).toInt(), total.toInt())
        else -> stringResource(R.string.watched_minutes, ((entry.positionMs + 59_999) / 60_000).toInt())
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(
            Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
fun folderSummary(folder: VideoFolder): String {
    val context = LocalContext.current
    val count = pluralStringResource(R.plurals.video_count, folder.videos.size, folder.videos.size)
    return "${isolate(count)}  ·  ${isolate(formatSize(context, folder.totalSize))}"
}

/** One folder as a row inside a rounded group. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderRow(folder: VideoFolder, index: Int, count: Int, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .groupItem(index, count)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // A large, quiet folder glyph on its own reads cleaner than an icon boxed in a tile.
        Icon(
            Icons.Rounded.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            modifier = Modifier.size(44.dp),
        )
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folderSummary(folder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCard(folder: VideoFolder, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: () -> Unit = {}) {
    Column(
        // Each folder sits in its own framed tile so the fanned cards never spill onto a neighbour.
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
    ) {
        FolderCollage(
            folder,
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Text(
            text = folder.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        )
        Text(
            text = folderSummary(folder),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

/**
 * Folder cover as a hand of three cards (like UNO cards): the latest video upright in the middle,
 * the next two tilted outwards behind it. Folders with fewer videos show fewer cards.
 */
@Composable
private fun FolderCollage(folder: VideoFolder, modifier: Modifier) {
    val covers = folder.covers
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cardWidth = maxWidth * 0.5f
        val cardHeight = maxHeight * 0.72f
        val spread = maxWidth * 0.17f
        val shape = RoundedCornerShape(14.dp)

        @Composable
        fun Card(video: Video?, angle: Float, shift: androidx.compose.ui.unit.Dp, front: Boolean) {
            VideoThumbnail(
                video,
                Modifier
                    .offset(x = shift, y = if (front) (-4).dp else 4.dp)
                    .graphicsLayer {
                        rotationZ = angle
                        // Pivot near the bottom so the cards fan out like a hand.
                        transformOrigin = TransformOrigin(0.5f, 1.1f)
                    }
                    .shadow(if (front) 10.dp else 4.dp, shape)
                    .size(cardWidth, cardHeight),
                showDuration = false,
                shape = shape,
            )
        }

        when (covers.size) {
            0, 1 -> Card(covers.firstOrNull(), 0f, 0.dp, front = true)
            2 -> {
                Card(covers[1], 8f, spread * 0.6f, front = false)
                Card(covers[0], -4f, -spread * 0.3f, front = true)
            }
            else -> {
                Card(covers[1], -8f, -spread, front = false)
                Card(covers[2], 8f, spread, front = false)
                Card(covers[0], 0f, 0.dp, front = true)
            }
        }

        // Video count badge on the front card.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text(
                folder.videos.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** A list or grid of videos, padded so the floating bar never hides the last row. */
@Composable
fun VideoCollection(
    videos: List<Video>,
    mode: ViewMode,
    onClick: (Video) -> Unit,
    onLongClick: (Video) -> Unit,
    emptyText: String?,
    extraBottom: Dp = LocalBottomBarSpace.current,
) {
    if (videos.isEmpty()) {
        if (emptyText != null) EmptyState(title = emptyText, body = null)
        return
    }
    when (mode) {
        ViewMode.List -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = extraBottom + 16.dp)) {
            items(videos, key = { it.path }) { VideoListItem(it, onClick = { onClick(it) }, onLongClick = { onLongClick(it) }) }
        }

        ViewMode.Grid -> LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = extraBottom + 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(videos, key = { it.path }) { VideoCard(it, onClick = { onClick(it) }, onLongClick = { onLongClick(it) }) }
        }
    }
}

@Composable
fun ViewModeButton(mode: ViewMode, onChange: (ViewMode) -> Unit) {
    IconButton(onClick = { onChange(if (mode == ViewMode.List) ViewMode.Grid else ViewMode.List) }) {
        if (mode == ViewMode.List) {
            Icon(Icons.Rounded.GridView, contentDescription = stringResource(R.string.view_grid))
        } else {
            Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = stringResource(R.string.view_list))
        }
    }
}

/** Sort icon that opens its own menu. */
@Composable
fun SortButton(order: SortOrder, fields: List<SortField>, onChange: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.sort))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            fields.forEach { field ->
                DropdownMenuItem(
                    text = { Text(stringResource(field.label())) },
                    onClick = { onChange(order.copy(field = field)) },
                    trailingIcon = { if (order.field == field) Icon(Icons.Rounded.Check, contentDescription = null) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            listOf(true to R.string.sort_ascending, false to R.string.sort_descending).forEach { (asc, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = { onChange(order.copy(ascending = asc)) },
                    trailingIcon = { if (order.ascending == asc) Icon(Icons.Rounded.Check, contentDescription = null) },
                )
            }
        }
    }
}

private fun SortField.label() = when (this) {
    SortField.Name -> R.string.sort_name
    SortField.Date -> R.string.sort_date
    SortField.Size -> R.string.sort_size
    SortField.Duration -> R.string.sort_duration
    SortField.Count -> R.string.sort_count
    SortField.Artist -> R.string.sort_artist
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoInfoSheet(video: Video, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.unknown)
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            VideoThumbnail(video, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large))
            Spacer(Modifier.height(16.dp))
            Text(
                video.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            LocalVideoActions.current?.let { actions ->
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onDismiss(); actions.play(video) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.player_play))
                    }
                    FilledTonalButton(
                        onClick = { onDismiss(); actions.listen(video) },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Rounded.Headphones, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.play_as_audio), maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            InfoRow(R.string.info_location, video.folderPath)
            InfoRow(R.string.info_size, formatSize(context, video.size))
            InfoRow(R.string.info_duration, formatDuration(video.durationMs).ifEmpty { unknown })
            InfoRow(
                R.string.info_resolution,
                if (video.width > 0 && video.height > 0) "${video.width} × ${video.height}" else unknown,
            )
            InfoRow(R.string.info_format, video.extension)
            InfoRow(R.string.info_modified, formatDate(video.dateModified))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InfoRow(label: Int, value: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.VideoLibrary,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().padding(32.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun LimitedAccessBanner(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
    ) {
        Text(
            stringResource(R.string.limited_access_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.limited_access_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAllow, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.limited_access_action))
        }
    }
}
