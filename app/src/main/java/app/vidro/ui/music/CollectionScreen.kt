package app.vidro.ui.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vidro.R
import app.vidro.data.Song
import app.vidro.ui.library.LocalBottomBarSpace
import app.vidro.ui.library.isolate

/** One album or one artist: a cover header, Play / Shuffle, then its songs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    viewModel: MusicViewModel,
    albumId: Long?,
    artistName: String?,
    navigation: MusicNavigation,
    onBack: () -> Unit,
) {
    val library by viewModel.library.collectAsState()
    val album = albumId?.let { id -> library.albums.firstOrNull { it.id == id } }
    val artist = artistName?.let { name -> library.artists.firstOrNull { it.name == name } }
    val songs: List<Song> = album?.songs ?: artist?.songs ?: emptyList()
    val title = album?.name ?: artist?.name.orEmpty()
    var actionsFor by remember { mutableStateOf<Song?>(null) }
    val mark = rememberNowPlayingMark()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // The name joins the bar once the big header has scrolled away.
                    if (scrollBehavior.state.overlappedFraction > 0.5f) Text(title, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = LocalBottomBarSpace.current + 16.dp),
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    val shape = if (album != null) RoundedCornerShape(28.dp) else CircleShape
                    Box(Modifier.size(220.dp).shadow(18.dp, shape)) {
                        SongArtwork(songs.firstOrNull(), Modifier.size(220.dp), shape, pixels = 720)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    val details = buildList {
                        if (album != null) add(album.artist ?: stringResource(R.string.unknown_artist))
                        if (album != null && album.year > 0) add(album.year.toString())
                        if (artist != null) add(pluralStringResource(R.plurals.album_count, artist.albumCount, artist.albumCount))
                        add(pluralStringResource(R.plurals.song_count, songs.size, songs.size))
                        add(totalDuration(songs.sumOf { it.durationMs }))
                    }
                    Text(
                        details.joinToString("  ·  ") { isolate(it) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    PlayShuffleButtons(
                        onPlay = { viewModel.play(songs, 0); navigation.openNowPlaying() },
                        onShuffle = { viewModel.play(songs, shuffle = true); navigation.openNowPlaying() },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            itemsIndexed(songs, key = { _, song -> song.path }) { index, song ->
                SongRow(
                    song = song,
                    mark = mark,
                    trackNumber = if (album != null) index + 1 else null,
                    onClick = { viewModel.play(songs, index); navigation.openNowPlaying() },
                    onLongClick = { actionsFor = song },
                )
            }
        }
    }

    actionsFor?.let { song ->
        SongActionsSheet(
            song = song,
            onDismiss = { actionsFor = null },
            onPlayNext = { viewModel.playNext(song) },
            onAddToQueue = { viewModel.addToQueue(song) },
            onOpenAlbum = if (album == null) viewModel.album(song.albumId)?.takeIf { song.album != null }?.let { a -> { navigation.openAlbum(a) } } else null,
            onOpenArtist = if (artist == null) song.artist?.let { viewModel.artist(it) }?.let { a -> { navigation.openArtist(a) } } else null,
        )
    }
}

