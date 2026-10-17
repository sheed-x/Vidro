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
import app.vidro.data.HomeShelf
import app.vidro.data.Video

/** The whole Home shelf (continue watching / recently added) as a full list, opened from its arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(viewModel: LibraryViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val recent by viewModel.recentlyAdded.collectAsState()
    var infoVideo by remember { mutableStateOf<Video?>(null) }
    val playVideo = rememberPlayVideo(viewModel)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val recentlyAdded = settings.homeShelf == HomeShelf.RecentlyAdded

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            VidroTopBar(
                title = stringResource(if (recentlyAdded) R.string.recently_added else R.string.continue_watching),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            VideoCollection(
                videos = if (recentlyAdded) recent else continueWatching,
                mode = settings.allVideosView,
                onClick = playVideo,
                onLongClick = { infoVideo = it },
                emptyText = stringResource(R.string.shelf_empty),
            )
        }
    }

    infoVideo?.let { VideoInfoSheet(it, onDismiss = { infoVideo = null }) }
}
