package app.vidro.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vidro.R
import app.vidro.data.HomeShelf
import app.vidro.data.SortField
import app.vidro.data.StorageAccess
import app.vidro.data.Video
import app.vidro.data.VideoFolder
import app.vidro.data.ViewMode
import app.vidro.ui.components.SectionHeader
import app.vidro.ui.components.formatSize
import androidx.compose.runtime.LaunchedEffect

private val FolderSortFields = listOf(SortField.Name, SortField.Date, SortField.Size, SortField.Count)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: LibraryViewModel, onOpenFolder: (VideoFolder) -> Unit, onOpenShelf: () -> Unit = {}) {
    val context = LocalContext.current
    val library by viewModel.library.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val recent by viewModel.recentlyAdded.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val settings by viewModel.settings.collectAsState()
    // What each counter shows when flipped over.
    val hoursUnit = stringResource(R.string.unit_hours)
    val minutesUnit = stringResource(R.string.unit_minutes)
    val backLabels = listOf(
        stringResource(R.string.stat_total_length),
        stringResource(R.string.stat_this_week),
        stringResource(R.string.stat_finished),
        stringResource(R.string.stat_free),
    )
    val historyNow by viewModel.history.collectAsState()
    val statBacks = remember(library, historyNow, backLabels) {
        val totalMinutes = library.allVideos.sumOf { it.durationMs } / 60_000
        val length = if (totalMinutes >= 60) "${totalMinutes / 60} $hoursUnit" else "$totalMinutes $minutesUnit"
        val weekAgo = System.currentTimeMillis() / 1000 - 7 * 24 * 3600
        val thisWeek = library.allVideos.count { it.dateAdded >= weekAgo }
        val finished = historyNow.values.count { it.finished }
        val free = runCatching {
            android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
        }.getOrDefault(0L)
        listOf(
            StatBack(length, backLabels[0]),
            StatBack(thisWeek.toString(), backLabels[1]),
            StatBack(finished.toString(), backLabels[2]),
            StatBack(formatSize(context, free), backLabels[3]),
        )
    }
    val shelfTitle = stringResource(
        if (settings.homeShelf == HomeShelf.ContinueWatching) R.string.continue_watching else R.string.recently_added,
    )
    val hasFullAccess by viewModel.hasFullAccess.collectAsState()
    var infoVideo by remember { mutableStateOf<Video?>(null) }
    var hideFolder by remember { mutableStateOf<VideoFolder?>(null) }
    val playVideo = rememberPlayVideo(viewModel)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val bottomSpace = LocalBottomBarSpace.current
    val history = LocalPlaybackHistory.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { VidroTopBar(title = stringResource(R.string.app_name), scrollBehavior = scrollBehavior) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!library.hasScanned) {
                Scanning()
                return@Box
            }
            PullToRefreshBox(
                isRefreshing = library.isScanning,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val showBanner = !hasFullAccess && StorageAccess.usesAllFilesSettings
                if (folders.isEmpty() && !showBanner) {
                    EmptyState(
                        title = stringResource(R.string.empty_library_title),
                        body = stringResource(R.string.empty_library_body),
                    )
                    return@PullToRefreshBox
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomSpace + 16.dp)) {
                    if (showBanner) {
                        item(key = "banner") {
                            LimitedAccessBanner(onAllow = { StorageAccess.openAllFilesSettings(context) })
                        }
                    }
                    item(key = "stats") {
                        StatsCard(
                            videos = library.allVideos.size,
                            folders = folders.size,
                            plays = history.values.sumOf { it.playCount },
                            storage = formatSize(context, folders.sumOf { it.totalSize }),
                            backs = statBacks,
                            haptics = settings.haptics,
                            motion = settings.motionEffects,
                        )
                    }
                    val shelf = when (settings.homeShelf) {
                        HomeShelf.ContinueWatching -> continueWatching
                        HomeShelf.RecentlyAdded -> recent
                        HomeShelf.Off -> emptyList()
                    }
                    if (shelf.isNotEmpty()) {
                        item(key = "recent-header") {
                            SectionHeader(shelfTitle) {
                                IconButton(onClick = onOpenShelf) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = stringResource(R.string.see_all),
                                    )
                                }
                            }
                        }
                        item(key = "recent") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(shelf, key = { it.path }) { video ->
                                    VideoCard(
                                        video = video,
                                        onClick = { playVideo(video) },
                                        onLongClick = { infoVideo = video },
                                        modifier = Modifier.width(220.dp),
                                    )
                                }
                            }
                        }
                    }
                    item(key = "folders-header") {
                        SectionHeader(stringResource(R.string.folders)) {
                            SortButton(settings.folderSort, FolderSortFields, viewModel::setFolderSort)
                            ViewModeButton(settings.folderView) { viewModel.setFolderView(it) }
                        }
                    }
                    when (settings.folderView) {
                        ViewMode.List -> itemsIndexed(folders, key = { _, f -> f.path }) { index, folder ->
                            FolderRow(folder, index, folders.size, onClick = { onOpenFolder(folder) }, onLongClick = { hideFolder = folder })
                        }

                        ViewMode.Grid -> items(folders.chunked(2), key = { it.first().path }) { pair ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                            ) {
                                pair.forEach { folder ->
                                    FolderCard(
                                        folder,
                                        onClick = { onOpenFolder(folder) },
                                        modifier = Modifier.weight(1f),
                                        onLongClick = { hideFolder = folder },
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    infoVideo?.let { VideoInfoSheet(it, onDismiss = { infoVideo = null }) }

    hideFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { hideFolder = null },
            title = { Text(stringResource(R.string.hide_folder_question, folder.name)) },
            text = { Text(stringResource(R.string.hide_folder_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setFolderHidden(folder.path, hidden = true)
                    hideFolder = null
                }) { Text(stringResource(R.string.hide)) }
            },
            dismissButton = { TextButton(onClick = { hideFolder = null }) { Text(stringResource(android.R.string.cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    }
}

@Composable
fun Scanning() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                stringResource(R.string.scanning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
