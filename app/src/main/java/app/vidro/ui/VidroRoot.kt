package app.vidro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.vidro.R
import app.vidro.data.StorageAccess
import app.vidro.ui.components.FloatingCircleButton
import app.vidro.ui.components.FloatingTabBar
import app.vidro.ui.components.UpdateDialog
import app.vidro.ui.components.LocalHapticsEnabled
import app.vidro.ui.components.TabItem
import app.vidro.ui.library.DisplayPrefs
import app.vidro.ui.library.FolderScreen
import app.vidro.ui.library.HomeScreen
import app.vidro.ui.library.LibraryViewModel
import app.vidro.ui.library.LocalBottomBarSpace
import app.vidro.ui.library.LocalDisplayPrefs
import app.vidro.ui.library.LocalPlaybackHistory
import app.vidro.ui.library.LocalVideoActions
import app.vidro.ui.library.VideoActions
import app.vidro.ui.music.CollectionScreen
import app.vidro.ui.music.MiniPlayer
import app.vidro.ui.music.MiniPlayerHeight
import app.vidro.ui.music.MusicNavigation
import app.vidro.ui.music.MusicScreen
import app.vidro.ui.music.MusicViewModel
import app.vidro.ui.music.NowPlayingScreen
import app.vidro.player.PlayerActivity
import app.vidro.player.PlayerHolder
import kotlinx.coroutines.flow.flowOf
import app.vidro.ui.library.SearchScreen
import app.vidro.ui.library.ShelfScreen
import app.vidro.ui.library.VideosScreen
import app.vidro.ui.settings.AboutScreen
import app.vidro.ui.settings.SettingsScreen
import app.vidro.ui.welcome.WelcomeScreen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.serialization.Serializable

@Serializable private object WelcomeRoute
@Serializable private object HomeRoute
@Serializable private object SearchRoute
@Serializable private object ShelfRoute
@Serializable private object AboutRoute
@Serializable private data class FolderRoute(val path: String)
@Serializable private object NowPlayingRoute
/** An album (by id) or an artist (by name) in the music library. */
@Serializable private data class MusicCollectionRoute(val albumId: Long = -1, val artist: String? = null)

private val BarHeight = 64.dp
private val BarBottomGap = 12.dp

@Composable
fun VidroRoot() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val viewModel: LibraryViewModel = viewModel()
    val musicViewModel: MusicViewModel = viewModel()
    val controller by PlayerHolder.current.collectAsState()
    val playerOnScreen by remember(controller) { controller?.screenAttached ?: flowOf(false) }.collectAsState(false)
    val nowPlayingRequested by AppRequests.nowPlaying.collectAsState()
    val hasAccess by viewModel.hasAccess.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val history by viewModel.history.collectAsState()
    val hazeState = rememberHazeState()
    // Home, Videos, Music and Settings are pages you can swipe between; the bar follows the finger.
    val pagerState = rememberPagerState { TAB_COUNT }
    val scope = rememberCoroutineScope()
    // The bar minimises while you scroll down through content and comes back when you scroll up.
    var barCompact by remember { mutableStateOf(false) }
    val barScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) barCompact = true else if (available.y > 6f) barCompact = false
                return Offset.Zero
            }
        }
    }
    // Small widgets (tab bar) read this directly instead of threading the setting through.
    SideEffect { LocalHapticsEnabled.value = settings.haptics }

    // Look for a new release on GitHub once per launch (if allowed in Settings).
    LaunchedEffect(Unit) { viewModel.checkUpdatesOnLaunch() }

    // Access can change in system settings while we're away, so re-check every time we come back.
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val start: Any = remember { if (StorageAccess.hasAnyAccess(context)) HomeRoute else WelcomeRoute }
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val onTabs = destination?.hasRoute<HomeRoute>() == true
    val selectedTab = when {
        onTabs -> pagerState.targetPage
        destination?.hasRoute<FolderRoute>() == true || destination?.hasRoute<ShelfRoute>() == true -> 0
        destination?.hasRoute<MusicCollectionRoute>() == true -> 2
        destination?.hasRoute<AboutRoute>() == true -> 3
        else -> -1
    }
    val onNowPlaying = destination?.hasRoute<NowPlayingRoute>() == true
    val onWelcome = destination?.hasRoute<WelcomeRoute>() == true
    // Whatever is playing off-screen stays one tap away above the bar.
    val miniVisible = controller != null && !playerOnScreen && !onNowPlaying && !onWelcome && destination != null

    val musicNavigation = remember(navController) {
        MusicNavigation(
            openAlbum = { navController.navigate(MusicCollectionRoute(albumId = it.id)) },
            openArtist = { navController.navigate(MusicCollectionRoute(artist = it.name)) },
            openNowPlaying = { navController.navigate(NowPlayingRoute) { launchSingleTop = true } },
        )
    }
    val videoActions = remember(viewModel, musicNavigation) {
        VideoActions(
            play = { video ->
                val (queue, index) = viewModel.queueFor(video)
                context.startActivity(PlayerActivity.intent(context, queue, index))
            },
            listen = { video ->
                viewModel.playAsAudio(video)
                musicNavigation.openNowPlaying()
            },
        )
    }
    val openSession: () -> Unit = {
        val c = PlayerHolder.controller
        if (c != null && !c.audioOnly.value) {
            // A video playing in the background goes back to its own player.
            context.startActivity(PlayerActivity.reattachIntent(context))
        } else {
            musicNavigation.openNowPlaying()
        }
    }

    // The notification, the video player ("Play as audio") or another app asked for the music screen.
    LaunchedEffect(nowPlayingRequested, destination != null) {
        if (nowPlayingRequested && destination != null) {
            AppRequests.consumeNowPlaying()
            if (PlayerHolder.controller != null) musicNavigation.openNowPlaying()
        }
    }
    val tabPosition = if (onTabs) pagerState.currentPage + pagerState.currentPageOffsetFraction else selectedTab.toFloat()

    // A different page or screen always starts with the full bar.
    LaunchedEffect(selectedTab, destination?.id) { barCompact = false }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val miniSpace = if (miniVisible) MiniPlayerHeight + MiniGap else 0.dp
    val barSpace = (if (selectedTab >= 0) BarHeight + BarBottomGap + navBottom + 8.dp else navBottom + if (miniVisible) BarBottomGap else 0.dp) + miniSpace
    // Four tabs and the search button must fit on narrow phones too.
    val narrow = LocalConfiguration.current.screenWidthDp < 400

    CompositionLocalProvider(
        LocalDisplayPrefs provides DisplayPrefs(settings.showThumbnails, settings.showNewBadge, settings.showWatchTime),
        LocalBottomBarSpace provides barSpace,
        LocalPlaybackHistory provides history,
        LocalVideoActions provides videoActions,
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            NavHost(
                navController = navController,
                startDestination = start,
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(160)) },
                modifier = Modifier.fillMaxSize().nestedScroll(barScroll).hazeSource(hazeState),
            ) {
                composable<WelcomeRoute> {
                    LaunchedEffect(hasAccess) {
                        if (hasAccess) {
                            navController.navigate(HomeRoute) { popUpTo<WelcomeRoute> { inclusive = true } }
                        }
                    }
                    WelcomeScreen(onAccessChanged = viewModel::onResume)
                }
                composable<HomeRoute> {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                viewModel = viewModel,
                                onOpenFolder = { navController.navigate(FolderRoute(it.path)) },
                                onOpenShelf = { navController.navigate(ShelfRoute) },
                            )
                            1 -> VideosScreen(viewModel)
                            2 -> MusicScreen(musicViewModel, musicNavigation)
                            else -> SettingsScreen(viewModel, onOpenAbout = { navController.navigate(AboutRoute) })
                        }
                    }
                }
                composable<AboutRoute> { AboutScreen(viewModel, onBack = { navController.popBackStack() }) }
                composable<MusicCollectionRoute> { entry ->
                    val route = entry.toRoute<MusicCollectionRoute>()
                    CollectionScreen(
                        viewModel = musicViewModel,
                        albumId = route.albumId.takeIf { it >= 0 },
                        artistName = route.artist,
                        navigation = musicNavigation,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<NowPlayingRoute>(
                    enterTransition = { slideInVertically(tween(320)) { it } + fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { slideOutVertically(tween(280)) { it } + fadeOut(tween(240)) },
                ) {
                    NowPlayingScreen(
                        onBack = { navController.popBackStack() },
                        onWatchVideo = {
                            PlayerHolder.controller?.setAudioOnly(false)
                            navController.popBackStack()
                            context.startActivity(PlayerActivity.reattachIntent(context))
                        },
                        onOpenAlbum = { item ->
                            val song = musicViewModel.library.value.songs.firstOrNull { it.path == item.target }
                            song?.let { musicViewModel.album(it.albumId) }?.let(musicNavigation.openAlbum)
                        },
                        onOpenArtist = { item ->
                            item.artist?.let { musicViewModel.artist(it) }?.let(musicNavigation.openArtist)
                        },
                    )
                }
                composable<ShelfRoute> { ShelfScreen(viewModel, onBack = { navController.popBackStack() }) }
                composable<SearchRoute> { SearchScreen(viewModel, onBack = { navController.popBackStack() }) }
                composable<FolderRoute> { entry ->
                    FolderScreen(
                        viewModel = viewModel,
                        path = entry.toRoute<FolderRoute>().path,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            UpdateDialog(viewModel.updates)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = BarBottomGap),
            ) {
            AnimatedVisibility(
                visible = miniVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                controller?.let { c ->
                    MiniPlayer(
                        controller = c,
                        hazeState = hazeState,
                        onOpen = openSession,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = MiniGap),
                    )
                }
            }
            AnimatedVisibility(
                visible = selectedTab >= 0,
                enter = slideInVertically { it * 2 } + fadeIn(),
                exit = slideOutVertically { it * 2 } + fadeOut(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FloatingTabBar(
                        tabs = listOf(
                            TabItem(stringResource(R.string.tab_home), Icons.Outlined.Home, Icons.Rounded.Home),
                            TabItem(stringResource(R.string.tab_videos), Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary),
                            TabItem(stringResource(R.string.tab_music), Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
                            TabItem(stringResource(R.string.tab_settings), Icons.Outlined.Settings, Icons.Rounded.Settings),
                        ),
                        tabWidth = if (narrow) 52.dp else 60.dp,
                        selectedTabWidth = if (narrow) 96.dp else 108.dp,
                        selectedIndex = selectedTab,
                        position = tabPosition,
                        compact = barCompact,
                        onExpand = { barCompact = false },
                        onSelect = { index ->
                            if (!onTabs) {
                                // From inside a folder: back to the tabs, then to the chosen page.
                                navController.popBackStack<HomeRoute>(inclusive = false)
                            }
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        hazeState = hazeState,
                    )
                    FloatingCircleButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.search),
                        onClick = { navController.navigate(SearchRoute) { launchSingleTop = true } },
                        hazeState = hazeState,
                    )
                }
            }
            }
        }
    }
}

private const val TAB_COUNT = 4
private val MiniGap = 10.dp
