package app.vidro.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(viewModel: LibraryViewModel, path: String, onBack: () -> Unit) {
    val videos by remember(path) { viewModel.folderVideos(path) }.collectAsState(initial = emptyList())
    val settings by viewModel.settings.collectAsState()
    var infoVideo by remember { mutableStateOf<Video?>(null) }
    val playVideo = rememberPlayVideo(viewModel)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            VidroTopBar(
                title = viewModel.folderName(path),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    SortButton(settings.videoSort, VideoSortFields, viewModel::setVideoSort)
                    ViewModeButton(settings.videoView) { viewModel.setVideoView(it) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            VideoCollection(
                videos = videos,
                mode = settings.videoView,
                onClick = playVideo,
                onLongClick = { infoVideo = it },
                emptyText = null,
            )
        }
    }

    infoVideo?.let { VideoInfoSheet(it, onDismiss = { infoVideo = null }) }
}
