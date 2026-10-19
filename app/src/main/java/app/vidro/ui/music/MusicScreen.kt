package app.vidro.ui.music

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.vidro.R
import app.vidro.data.Album
import app.vidro.data.Artist
import app.vidro.data.SortField
import app.vidro.data.Song
import app.vidro.data.StorageAccess
import app.vidro.ui.library.EmptyState
import app.vidro.ui.library.LocalBottomBarSpace
import app.vidro.ui.library.SortButton
import app.vidro.ui.library.VidroTopBar
import app.vidro.ui.library.isolate

private val SongSortFields = listOf(SortField.Name, SortField.Artist, SortField.Date, SortField.Duration)

/** Where the music tab can lead: an album, an artist, or the full now-playing screen. */
class MusicNavigation(
    val openAlbum: (Album) -> Unit,
    val openArtist: (Artist) -> Unit,
    val openNowPlaying: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(viewModel: MusicViewModel, navigation: MusicNavigation) {
    val context = LocalContext.current
    val hasAccess by viewModel.hasAccess.collectAsState()
    val library by viewModel.library.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val recent by viewModel.recentlyPlayed.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var section by rememberSaveable { mutableIntStateOf(0) }
    var actionsFor by remember { mutableStateOf<Song?>(null) }
    val mark = rememberNowPlayingMark()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onResume() }

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val play: (List<Song>, Int) -> Unit = { list, index ->
        viewModel.play(list, index)
        navigation.openNowPlaying()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            VidroTopBar(
                title = stringResource(R.string.tab_music),
                scrollBehavior = scrollBehavior,
                actions = {
                    if (hasAccess && section == 0 && songs.isNotEmpty()) {
                        SortButton(settings.musicSort, SongSortFields, viewModel::setSort)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !hasAccess -> MusicAccessCard(onAllow = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || !StorageAccess.usesAllFilesSettings) {
                        permission.launch(StorageAccess.audioPermission())
                    } else {
                        StorageAccess.openAllFilesSettings(context)
                    }
                })
                !library.hasScanned -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                library.songs.isEmpty() -> EmptyState(
                    title = stringResource(R.string.music_empty_title),
                    body = stringResource(R.string.music_empty_body),
                    icon = Icons.Rounded.LibraryMusic,
                )
                else -> PullToRefreshBox(
                    isRefreshing = library.isScanning,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalBottomBarSpace.current + 16.dp),
                    ) {
                        item(key = "tabs") {
                            SegmentedTabs(
                                labels = listOf(
                                    stringResource(R.string.music_songs),
                                    stringResource(R.string.music_albums),
                                    stringResource(R.string.music_artists),
                                ),
                                selected = section,
                                onSelect = { section = it },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        item(key = "summary") {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    listOf(
                                        pluralStringResource(R.plurals.song_count, library.songs.size, library.songs.size),
                                        totalDuration(library.totalDurationMs),
                                    ).joinToString("  ·  ") { isolate(it) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                                )
                                PlayShuffleButtons(
                                    onPlay = { play(songs, 0) },
                                    onShuffle = {
                                        viewModel.play(songs, shuffle = true)
                                        navigation.openNowPlaying()
                                    },
                                )
                            }
                        }
                        when (section) {
                            0 -> {
                                if (recent.isNotEmpty()) {
                                    item(key = "recent") { RecentShelf(recent, onPlay = { play(recent, it) }) }
                                }
                                items(songs, key = { "song:" + it.path }) { song ->
                                    SongRow(
                                        song = song,
                                        mark = mark,
                                        onClick = { play(songs, songs.indexOf(song)) },
                                        onLongClick = { actionsFor = song },
                                    )
                                }
                            }
                            1 -> items(library.albums.chunked(2), key = { "albums:" + it.first().id }) { row ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                                    row.forEach { album -> AlbumCard(album, onClick = { navigation.openAlbum(album) }, modifier = Modifier.weight(1f)) }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            else -> items(library.artists, key = { "artist:" + it.name }) { artist ->
                                ArtistRow(artist, onClick = { navigation.openArtist(artist) })
                            }
                        }
                    }
                }
            }
        }
    }

    actionsFor?.let { song ->
        SongActionsSheet(
            song = song,
            onDismiss = { actionsFor = null },
            onPlayNext = { viewModel.playNext(song) },
            onAddToQueue = { viewModel.addToQueue(song) },
            onOpenAlbum = viewModel.library.value.albums.firstOrNull { it.id == song.albumId && song.album != null }?.let { album -> { navigation.openAlbum(album) } },
            onOpenArtist = song.artist?.let { name -> viewModel.artist(name)?.let { artist -> { navigation.openArtist(artist) } } },
        )
    }
}

@Composable
private fun RecentShelf(songs: List<Song>, onPlay: (Int) -> Unit) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            stringResource(R.string.recently_played),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(songs.size, key = { songs[it].path }) { i ->
                val song = songs[i]
                Column(
                    Modifier
                        .width(132.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onPlay(i) },
                ) {
                    SongArtwork(song, Modifier.size(132.dp).shadow(6.dp, RoundedCornerShape(20.dp)), RoundedCornerShape(20.dp), pixels = 360)
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
                    )
                    Text(
                        artistName(song),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MusicAccessCard(onAllow: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        EmptyState(
            title = stringResource(R.string.music_access_title),
            body = stringResource(R.string.music_access_body),
            icon = Icons.Rounded.LibraryMusic,
            modifier = Modifier.weight(1f, fill = false),
        )
        Button(onClick = onAllow, shape = RoundedCornerShape(18.dp), modifier = Modifier.height(52.dp)) {
            Text(stringResource(R.string.music_access_action))
        }
    }
}
