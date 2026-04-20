package top.imsyy.splayer.nativeapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import top.imsyy.splayer.nativeapp.model.MyMusicPanelTab
import top.imsyy.splayer.nativeapp.model.ThemeMode
import top.imsyy.splayer.nativeapp.ui.components.MiniPlayerBar
import top.imsyy.splayer.nativeapp.ui.navigation.DrawerEntry
import top.imsyy.splayer.nativeapp.ui.navigation.Routes
import top.imsyy.splayer.nativeapp.ui.navigation.TopLevelDestination
import top.imsyy.splayer.nativeapp.ui.screen.AlbumDetailScreen
import top.imsyy.splayer.nativeapp.ui.screen.DiscoveryScreen
import top.imsyy.splayer.nativeapp.ui.screen.HomeScreen
import top.imsyy.splayer.nativeapp.ui.screen.MyScreen
import top.imsyy.splayer.nativeapp.ui.screen.PlayerScreen
import top.imsyy.splayer.nativeapp.ui.screen.PlaylistDetailScreen
import top.imsyy.splayer.nativeapp.ui.screen.SearchScreen
import top.imsyy.splayer.nativeapp.ui.screen.SettingsScreen
import top.imsyy.splayer.nativeapp.ui.theme.SPlayerNativeTheme

@Composable
fun SPlayerNativeApp() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val chromeViewModel: AppChromeViewModel = hiltViewModel()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val chromeState by chromeViewModel.uiState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val tabs = topLevelMusicDestinations()

    fun navigateToTopLevel(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            popUpTo(Routes.Home) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToDrawerEntry(entry: DrawerEntry) {
        when (entry) {
            DrawerEntry.LikedSongs -> navController.navigate(Routes.my(MyMusicPanelTab.Created.name))
            DrawerEntry.RecentPlays -> navController.navigate(Routes.my(MyMusicPanelTab.Recent.name))
            DrawerEntry.CollectedPlaylists -> navController.navigate(Routes.my(MyMusicPanelTab.Collected.name))
            DrawerEntry.Settings -> navController.navigate(Routes.Settings)
        }
    }

    SPlayerNativeTheme(darkTheme = chromeState.themeMode == ThemeMode.DARK) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = navBackStackEntry?.destination?.route?.startsWith(Routes.Home) == true,
            drawerContent = {
                ModalDrawerSheet {
                    NavigationDrawerItem(
                        label = { Text(chromeState.currentUser?.nickname ?: "未登录") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                navController.navigate(Routes.my())
                            }
                        },
                    )
                    listeningDrawerEntries().forEach { entry ->
                        NavigationDrawerItem(
                            label = { Text(entry.label) },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    navigateToDrawerEntry(entry)
                                }
                            },
                        )
                    }
                }
            },
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    if (navBackStackEntry?.destination?.route != Routes.Player) {
                        Column(modifier = Modifier.navigationBarsPadding()) {
                            playbackState.currentTrack?.let { track ->
                                MiniPlayerBar(
                                    track = track,
                                    isPlaying = playbackState.isPlaying,
                                    queueCount = if (chromeState.showQueueCount) playbackState.queue.size else 0,
                                    onOpenPlayer = { navController.navigate(Routes.Player) },
                                    onTogglePlay = playerViewModel::togglePlayback,
                                    onOpenQueue = {
                                        playerViewModel.openQueueSheet()
                                        navController.navigate(Routes.Player)
                                    },
                                )
                            }
                            NavigationBar {
                                tabs.forEach { destination ->
                                    NavigationBarItem(
                                        selected = navBackStackEntry?.destination?.hierarchy?.any {
                                            it.route?.startsWith(destination.route) == true
                                        } == true,
                                        onClick = { navigateToTopLevel(destination) },
                                        icon = {
                                            Icon(
                                                when (destination) {
                                                    TopLevelDestination.Recommend -> Icons.Rounded.Home
                                                    TopLevelDestination.Discovery -> Icons.Rounded.Explore
                                                    TopLevelDestination.My -> Icons.Rounded.Person
                                                },
                                                contentDescription = destination.label,
                                            )
                                        },
                                        label = { Text(destination.label) },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.Home,
                    modifier = Modifier.padding(padding),
                ) {
                    composable(Routes.Home) {
                        HomeScreen(
                            onOpenMenu = {
                                scope.launch {
                                    drawerState.open()
                                }
                            },
                            onOpenSearch = { navController.navigate(Routes.search()) },
                            onPlayTrack = { track ->
                                playerViewModel.playSingleTrack(track)
                                navController.navigate(Routes.Player)
                            },
                            onOpenPlaylist = { playlistId ->
                                navController.navigate(Routes.playlistDetail(playlistId))
                            },
                            onOpenAlbum = { albumId ->
                                navController.navigate(Routes.albumDetail(albumId))
                            },
                            onOpenMy = { navController.navigate(Routes.my()) },
                        )
                    }
                    composable(Routes.Discovery) {
                        DiscoveryScreen(
                            onOpenSearch = { navController.navigate(Routes.search()) },
                            onOpenSearchKeyword = { keyword ->
                                navController.navigate(Routes.search(keyword))
                            },
                            onPlayTrack = { track ->
                                playerViewModel.playSingleTrack(track)
                                navController.navigate(Routes.Player)
                            },
                            onOpenPlaylist = { playlistId ->
                                navController.navigate(Routes.playlistDetail(playlistId))
                            },
                            onOpenAlbum = { albumId ->
                                navController.navigate(Routes.albumDetail(albumId))
                            },
                        )
                    }
                    composable(
                        route = Routes.MyRoute,
                        arguments = listOf(navArgument(Routes.MyTabArg) { defaultValue = "" }),
                    ) {
                        MyScreen(
                            onOpenPlaylist = { playlistId ->
                                navController.navigate(Routes.playlistDetail(playlistId))
                            },
                            onOpenAlbum = { albumId ->
                                navController.navigate(Routes.albumDetail(albumId))
                            },
                            onPlayTrack = { track ->
                                playerViewModel.playSingleTrack(track)
                                navController.navigate(Routes.Player)
                            },
                        )
                    }
                    composable(
                        route = Routes.SearchRoute,
                        arguments = listOf(navArgument(Routes.SearchKeywordArg) { defaultValue = "" }),
                    ) {
                        SearchScreen(
                            onPlayTrack = {
                                navController.navigate(Routes.Player)
                            },
                        )
                    }
                    composable(Routes.Player) {
                        PlayerScreen(
                            onClose = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = "${Routes.Playlist}/{${Routes.PlaylistIdArg}}",
                        arguments = listOf(navArgument(Routes.PlaylistIdArg) { defaultValue = "0" }),
                    ) {
                        PlaylistDetailScreen(
                            onBack = { navController.popBackStack() },
                            onPlayAll = { tracks ->
                                playerViewModel.playTracks(tracks, 0)
                                navController.navigate(Routes.Player)
                            },
                            onPlayTrack = { tracks, index ->
                                playerViewModel.playTracks(tracks, index)
                                navController.navigate(Routes.Player)
                            },
                        )
                    }
                    composable(
                        route = "${Routes.Album}/{${Routes.AlbumIdArg}}",
                        arguments = listOf(navArgument(Routes.AlbumIdArg) { defaultValue = "0" }),
                    ) {
                        AlbumDetailScreen(
                            onBack = { navController.popBackStack() },
                            onPlayAll = { tracks ->
                                playerViewModel.playTracks(tracks, 0)
                                navController.navigate(Routes.Player)
                            },
                            onPlayTrack = { tracks, index ->
                                playerViewModel.playTracks(tracks, index)
                                navController.navigate(Routes.Player)
                            },
                        )
                    }
                    composable(Routes.Settings) {
                        SettingsScreen()
                    }
                }
            }
        }
    }
}
