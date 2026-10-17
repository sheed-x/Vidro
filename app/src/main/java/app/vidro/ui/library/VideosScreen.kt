package app.vidro.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import app.vidro.R
import app.vidro.data.SortField
import app.vidro.data.Video

private val VideoSortFields = listOf(SortField.Name, SortField.Date, SortField.Size, SortField.Duration)

/** Every video on the phone in one list, regardless of folder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(viewModel: LibraryViewModel) {
    val library by viewModel.library.collectAsState()
    val videos by viewModel.allVideos.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var infoVideo by remember { mutableStateOf<Video?>(null) }
    val playVideo = rememberPlayVideo(viewModel)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            VidroTopBar(
                title = stringResource(R.string.tab_videos),
                scrollBehavior = scrollBehavior,
                actions = {
                    SortButton(settings.videoSort, VideoSortFields, viewModel::setVideoSort)
                    ViewModeButton(settings.allVideosView) { viewModel.setAllVideosView(it) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!library.hasScanned) {
                Scanning()
            } else {
                PullToRefreshBox(
                    isRefreshing = library.isScanning,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    VideoCollection(
                        videos = videos,
                        mode = settings.allVideosView,
                        onClick = playVideo,
                        onLongClick = { infoVideo = it },
                        emptyText = stringResource(R.string.empty_library_title),
                    )
                }
            }
        }
    }

    infoVideo?.let { VideoInfoSheet(it, onDismiss = { infoVideo = null }) }
}
