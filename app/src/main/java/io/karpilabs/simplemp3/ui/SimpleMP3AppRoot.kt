package io.karpilabs.simplemp3.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.ui.components.AddToPlaylistSheet
import io.karpilabs.simplemp3.ui.components.MiniPlayer
import io.karpilabs.simplemp3.ui.components.NowPlayingSheet
import io.karpilabs.simplemp3.ui.components.QueueSheet
import io.karpilabs.simplemp3.ui.navigation.Routes
import io.karpilabs.simplemp3.ui.screens.CollectionDetailScreen
import io.karpilabs.simplemp3.ui.screens.CreatePlaylistDialog
import io.karpilabs.simplemp3.ui.screens.FolderDetailScreen
import io.karpilabs.simplemp3.ui.screens.HomeScreen
import io.karpilabs.simplemp3.ui.screens.JellyfinScreen
import io.karpilabs.simplemp3.ui.screens.LibraryFoldersScreen
import io.karpilabs.simplemp3.ui.screens.LibraryScreen
import io.karpilabs.simplemp3.ui.screens.PlaylistDetailScreen
import io.karpilabs.simplemp3.ui.screens.PlaylistsScreen
import io.karpilabs.simplemp3.ui.screens.QuickConnectScreen
import io.karpilabs.simplemp3.ui.screens.SearchScreen
import io.karpilabs.simplemp3.ui.screens.SettingsScreen
import io.karpilabs.simplemp3.ui.screens.ToolsScreen
import io.karpilabs.simplemp3.ui.screens.YoutubeScreen
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.NightBlack
import io.karpilabs.simplemp3.ui.theme.TextMuted
import io.karpilabs.simplemp3.ui.viewmodel.JellyfinViewModel
import io.karpilabs.simplemp3.ui.viewmodel.MusicViewModel
import io.karpilabs.simplemp3.ui.viewmodel.QuickConnectViewModel
import io.karpilabs.simplemp3.ui.viewmodel.YoutubeViewModel

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SimpleMP3AppRoot(
    viewModel: MusicViewModel = hiltViewModel()
) {
    val navController = rememberNavController()

    // Only collect what Home + chrome need — keeps first frame light.
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val continueListening by viewModel.continueListening.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val trackCount by viewModel.trackCount.collectAsStateWithLifecycle()
    val jellyfinCount by viewModel.jellyfinCount.collectAsStateWithLifecycle()
    val jellyfinEnabled by viewModel.jellyfinEnabled.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val driveMode by viewModel.driveMode.collectAsStateWithLifecycle()
    val autoDriveModeOnCar by viewModel.autoDriveModeOnCar.collectAsStateWithLifecycle()
    val autoResumeOnDrive by viewModel.autoResumeOnDrive.collectAsStateWithLifecycle()
    val pauseOnCarDisconnect by viewModel.pauseOnCarDisconnect.collectAsStateWithLifecycle()
    val largeFileOptimize by viewModel.largeFileOptimize.collectAsStateWithLifecycle()
    val largeFileColdPack by viewModel.largeFileColdPack.collectAsStateWithLifecycle()
    val wifiOnlyDownloads by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val resume by viewModel.resumeSnapshot.collectAsStateWithLifecycle()
    val resumeEnabled by viewModel.resumeEnabled.collectAsStateWithLifecycle()

    val visiblePlaylists = remember(playlists, jellyfinEnabled) {
        if (jellyfinEnabled) playlists
        else playlists.filter { it.systemType != PlaylistEntity.SYSTEM_JELLYFIN }
    }

    var showNowPlaying by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var addToPlaylistTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var showCreateFromAdd by remember { mutableStateOf(false) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(audioPermission)

    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) {
            // Soft scan: instant UI from Room; MediaStore only if empty/stale
            viewModel.ensureLibraryReady()
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
        }
    }

    val tabs = listOf(
        TabItem(Routes.HOME, "Home", Icons.Rounded.Home),
        TabItem(Routes.SEARCH, "Search", Icons.Rounded.Search),
        TabItem(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
        TabItem(Routes.PLAYLISTS, "Playlists", Icons.Rounded.QueueMusic),
        TabItem(Routes.TOOLS, "Tools", Icons.Rounded.Build)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = !driveMode && currentRoute in tabs.map { it.route }

    // Leave Jellyfin if the user turns the integration off while on that screen.
    LaunchedEffect(jellyfinEnabled, currentRoute) {
        if (!jellyfinEnabled && currentRoute == Routes.JELLYFIN) {
            navController.popBackStack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = NightBlack,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentTeal,
                                selectedTextColor = AccentTeal,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = AccentTeal.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        playlists = visiblePlaylists,
                        recentlyAdded = recentlyAdded,
                        continueListening = continueListening,
                        trackCount = trackCount,
                        jellyfinCount = jellyfinCount,
                        jellyfinEnabled = jellyfinEnabled,
                        isScanning = isScanning,
                        playerState = playerState,
                        driveMode = driveMode,
                        resume = if (resumeEnabled) resume else null,
                        onScan = {
                            if (permissionState.status.isGranted) viewModel.scanLibrary(force = true)
                            else permissionState.launchPermissionRequest()
                        },
                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                        onPlayAll = { viewModel.playAll(it) },
                        onOpenPlaylist = { navController.navigate(Routes.playlistDetail(it)) },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddToPlaylist = { addToPlaylistTrack = it },
                        onOpenJellyfin = {
                            if (jellyfinEnabled) navController.navigate(Routes.JELLYFIN)
                        },
                        onOpenTools = { navController.navigate(Routes.TOOLS) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onToggleDriveMode = { viewModel.setDriveMode(!driveMode) },
                        onResume = { viewModel.resumeLastSession(autoPlay = true) },
                        onPlayPause = viewModel::togglePlayPause,
                        onSkipNext = viewModel::skipNext,
                        onSkipPrevious = viewModel::skipPrevious
                    )
                }
                composable(Routes.TOOLS) {
                    ToolsScreen(
                        onOpenYoutube = { navController.navigate(Routes.YOUTUBE) },
                        onOpenQuickConnect = { navController.navigate(Routes.QUICK_CONNECT) }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        jellyfinEnabled = jellyfinEnabled,
                        autoDriveModeOnCar = autoDriveModeOnCar,
                        autoResumeOnDrive = autoResumeOnDrive,
                        pauseOnCarDisconnect = pauseOnCarDisconnect,
                        resumeEnabled = resumeEnabled,
                        wifiOnlyDownloads = wifiOnlyDownloads,
                        largeFileOptimize = largeFileOptimize,
                        largeFileColdPack = largeFileColdPack,
                        onBack = { navController.popBackStack() },
                        onJellyfinEnabledChange = viewModel::setJellyfinEnabled,
                        onAutoDriveModeOnCarChange = viewModel::setAutoDriveModeOnCar,
                        onAutoResumeOnDriveChange = viewModel::setAutoResumeOnDrive,
                        onPauseOnCarDisconnectChange = viewModel::setPauseOnCarDisconnect,
                        onResumeEnabledChange = viewModel::setResumeEnabled,
                        onWifiOnlyDownloadsChange = viewModel::setWifiOnlyDownloads,
                        onLargeFileOptimizeChange = viewModel::setLargeFileOptimize,
                        onLargeFileColdPackChange = viewModel::setLargeFileColdPack,
                        onOpenQuickConnect = { navController.navigate(Routes.QUICK_CONNECT) },
                        onOpenLibraryFolders = { navController.navigate(Routes.LIBRARY_FOLDERS) }
                    )
                }
                composable(Routes.LIBRARY_FOLDERS) {
                    val selectedRoots by viewModel.libraryFolderRoots.collectAsStateWithLifecycle()
                    val deviceFolders by viewModel.deviceFolders.collectAsStateWithLifecycle()
                    val deviceFoldersLoading by viewModel.deviceFoldersLoading.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { viewModel.refreshDeviceFolders() }
                    // Picker “select all” uses the same top/second-level paths shown in the UI.
                    val pickerPaths = remember(deviceFolders) {
                        buildLibraryFolderPickerPaths(deviceFolders)
                    }
                    LibraryFoldersScreen(
                        selectedRoots = selectedRoots,
                        deviceFolders = deviceFolders,
                        isLoading = deviceFoldersLoading,
                        isScanning = isScanning,
                        onBack = { navController.popBackStack() },
                        onRefreshFolders = viewModel::refreshDeviceFolders,
                        onLimitEnabledChange = viewModel::setLibraryFolderLimitEnabled,
                        onToggleRoot = viewModel::toggleLibraryFolderRoot,
                        onSelectAllVisible = {
                            viewModel.selectAllVisibleLibraryFolderRoots(pickerPaths)
                        },
                        onClearSelection = viewModel::clearLibraryFolderRoots
                    )
                }
                composable(Routes.JELLYFIN) {
                    if (!jellyfinEnabled) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                        return@composable
                    }

                    val jfVm: JellyfinViewModel = hiltViewModel()
                    val session by jfVm.session.collectAsStateWithLifecycle()
                    val jfUi by jfVm.ui.collectAsStateWithLifecycle()
                    val jfProgress by jfVm.syncProgress.collectAsStateWithLifecycle()
                    val offlineTracks by jfVm.offlineTracks.collectAsStateWithLifecycle()
                    val offlineCount by jfVm.offlineCount.collectAsStateWithLifecycle()

                    LaunchedEffect(session?.userId) {
                        if (session != null) jfVm.refreshRemote()
                    }

                    JellyfinScreen(
                        session = session,
                        ui = jfUi,
                        progress = jfProgress,
                        offlineTracks = offlineTracks,
                        offlineCount = offlineCount,
                        onBack = { navController.popBackStack() },
                        onLogin = jfVm::login,
                        onDiscover = jfVm::discoverServers,
                        onLogout = jfVm::logout,
                        onRefresh = jfVm::refreshRemote,
                        onBrowseMode = jfVm::setBrowseMode,
                        onOpenAlbum = jfVm::openAlbum,
                        onCloseAlbum = jfVm::closeAlbum,
                        onToggleSelect = jfVm::toggleSelect,
                        onSelectAll = jfVm::selectAllVisible,
                        onSyncSelected = jfVm::syncSelected,
                        onSyncAlbum = jfVm::syncAlbum,
                        onSyncAll = { jfVm.syncAll(200) },
                        onImportPlaylist = jfVm::importPlaylist,
                        onImportPlaylistNow = jfVm::importPlaylistNow,
                        onWifiOnlyChange = jfVm::setWifiOnly,
                        onRemoveOffline = jfVm::removeOffline,
                        onClearOffline = jfVm::clearOffline,
                        onPlayOfflineTrack = { track ->
                            viewModel.playTrack(track, offlineTracks)
                        }
                    )
                }
                composable(Routes.YOUTUBE) {
                    val ytVm: YoutubeViewModel = hiltViewModel()
                    val ytUi by ytVm.ui.collectAsStateWithLifecycle()
                    val ytProgress by ytVm.progress.collectAsStateWithLifecycle()
                    val ytDownloads by ytVm.downloads.collectAsStateWithLifecycle()

                    YoutubeScreen(
                        ui = ytUi,
                        progress = ytProgress,
                        downloads = ytDownloads,
                        onBack = { navController.popBackStack() },
                        onUrlChange = ytVm::setUrl,
                        onPaste = ytVm::pasteAndSet,
                        onDownload = ytVm::download,
                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                        onRemove = ytVm::remove,
                        onToggleNeverCompress = ytVm::toggleNeverCompress,
                        onClearAll = ytVm::clearAll
                    )
                }
                composable(Routes.QUICK_CONNECT) {
                    val qcVm: QuickConnectViewModel = hiltViewModel()
                    QuickConnectScreen(
                        viewModel = qcVm,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SEARCH) {
                    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
                    SearchScreen(
                        query = searchQuery,
                        results = searchResults,
                        playerState = playerState,
                        onQueryChange = viewModel::setSearchQuery,
                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddToPlaylist = { addToPlaylistTrack = it }
                    )
                }
                composable(Routes.LIBRARY) {
                    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                    val albums by viewModel.albums.collectAsStateWithLifecycle()
                    val artists by viewModel.artists.collectAsStateWithLifecycle()
                    val rootFolders by viewModel.rootFolders.collectAsStateWithLifecycle()
                    val libraryFilter by viewModel.libraryFilter.collectAsStateWithLifecycle()
                    LibraryScreen(
                        tracks = tracks,
                        albums = albums,
                        artists = artists,
                        rootFolders = rootFolders,
                        filter = libraryFilter,
                        playerState = playerState,
                        onFilterChange = viewModel::setLibraryFilter,
                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                        onOpenAlbum = { navController.navigate(Routes.albumDetail(it)) },
                        onOpenArtist = { navController.navigate(Routes.artistDetail(it)) },
                        onOpenFolder = { navController.navigate(Routes.folderDetail(it)) },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddToPlaylist = { addToPlaylistTrack = it }
                    )
                }
                composable(Routes.PLAYLISTS) {
                    PlaylistsScreen(
                        playlists = visiblePlaylists,
                        onOpenPlaylist = { navController.navigate(Routes.playlistDetail(it)) },
                        onCreatePlaylist = { viewModel.createPlaylist(it) }
                    )
                }
                composable(
                    route = Routes.PLAYLIST_DETAIL,
                    arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                ) { entry ->
                    val playlistId = entry.arguments?.getLong("playlistId") ?: return@composable
                    val playlist by remember(playlistId) { viewModel.playlist(playlistId) }
                        .collectAsStateWithLifecycle()
                    val playlistTracks by remember(playlistId) { viewModel.playlistTracks(playlistId) }
                        .collectAsStateWithLifecycle()
                    PlaylistDetailScreen(
                        playlist = playlist,
                        tracks = playlistTracks,
                        playerState = playerState,
                        onBack = { navController.popBackStack() },
                        onPlayAll = { viewModel.playAll(playlistTracks) },
                        onShuffle = { viewModel.playAll(playlistTracks.shuffled()) },
                        onPlayTrack = { viewModel.playTrack(it, playlistTracks) },
                        onRemoveTrack = { viewModel.removeFromPlaylist(playlistId, it) },
                        onDeletePlaylist = {
                            viewModel.deletePlaylist(playlistId)
                            navController.popBackStack()
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onToggleNeverCompress = viewModel::toggleNeverCompress,
                        onReorder = { from, to -> viewModel.reorderPlaylist(playlistId, from, to) },
                        onPlayNext = viewModel::playNext,
                        onAddToQueue = viewModel::addToQueue,
                        onAddToPlaylist = { addToPlaylistTrack = it }
                    )
                }
                composable(
                    route = Routes.ALBUM_DETAIL,
                    arguments = listOf(navArgument("albumName") { type = NavType.StringType })
                ) { entry ->
                    val albumName = android.net.Uri.decode(
                        entry.arguments?.getString("albumName").orEmpty()
                    )
                    val albumTracks by remember(albumName) { viewModel.albumTracks(albumName) }
                        .collectAsStateWithLifecycle()
                    CollectionDetailScreen(
                        title = albumName,
                        subtitle = albumTracks.firstOrNull()?.artist.orEmpty(),
                        tracks = albumTracks,
                        playerState = playerState,
                        onBack = { navController.popBackStack() },
                        onPlayAll = { viewModel.playAll(albumTracks) },
                        onShuffle = { viewModel.playAll(albumTracks.shuffled()) },
                        onPlayTrack = { viewModel.playTrack(it, albumTracks) },
                        onToggleFavorite = viewModel::toggleFavorite
                    )
                }
                composable(
                    route = Routes.ARTIST_DETAIL,
                    arguments = listOf(navArgument("artistName") { type = NavType.StringType })
                ) { entry ->
                    val artistName = android.net.Uri.decode(
                        entry.arguments?.getString("artistName").orEmpty()
                    )
                    val artistTracks by remember(artistName) { viewModel.artistTracks(artistName) }
                        .collectAsStateWithLifecycle()
                    CollectionDetailScreen(
                        title = artistName,
                        subtitle = "",
                        tracks = artistTracks,
                        playerState = playerState,
                        onBack = { navController.popBackStack() },
                        onPlayAll = { viewModel.playAll(artistTracks) },
                        onShuffle = { viewModel.playAll(artistTracks.shuffled()) },
                        onPlayTrack = { viewModel.playTrack(it, artistTracks) },
                        onToggleFavorite = viewModel::toggleFavorite
                    )
                }
                composable(
                    route = Routes.FOLDER_DETAIL,
                    arguments = listOf(navArgument("folderPath") { type = NavType.StringType })
                ) { entry ->
                    val folderPath = android.net.Uri.decode(
                        entry.arguments?.getString("folderPath").orEmpty()
                    )
                    val subfolders by remember(folderPath) { viewModel.childFolders(folderPath) }
                        .collectAsStateWithLifecycle()
                    val folderTracks by remember(folderPath) { viewModel.folderTracks(folderPath) }
                        .collectAsStateWithLifecycle()
                    FolderDetailScreen(
                        folderPath = folderPath,
                        subfolders = subfolders,
                        tracks = folderTracks,
                        playerState = playerState,
                        onBack = { navController.popBackStack() },
                        onOpenFolder = { navController.navigate(Routes.folderDetail(it)) },
                        onPlayAll = { viewModel.playAll(folderTracks) },
                        onShuffle = { viewModel.playAll(folderTracks.shuffled()) },
                        onPlayTrack = { viewModel.playTrack(it, folderTracks) },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddToPlaylist = { addToPlaylistTrack = it }
                    )
                }
            }

            MiniPlayer(
                state = playerState,
                onExpand = { showNowPlaying = true },
                onPlayPause = viewModel::togglePlayPause,
                onSkipNext = viewModel::skipNext,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showBottomBar) 0.dp else 8.dp)
            )
        }
    }

    if (showNowPlaying) {
        NowPlayingSheet(
            state = playerState,
            onDismiss = { showNowPlaying = false },
            onPlayPause = viewModel::togglePlayPause,
            onSkipNext = viewModel::skipNext,
            onSkipPrevious = viewModel::skipPrevious,
            onSeek = viewModel::seekTo,
            onToggleShuffle = viewModel::toggleShuffle,
            onCycleRepeat = viewModel::cycleRepeat,
            onRefreshPosition = viewModel::refreshPosition,
            onOpenQueue = {
                showNowPlaying = false
                showQueue = true
            },
            onSleepTimer = viewModel::setSleepTimer
        )
    }

    if (showQueue) {
        QueueSheet(
            state = playerState,
            onDismiss = { showQueue = false },
            onPlayIndex = { index ->
                viewModel.seekToQueueIndex(index)
                showQueue = false
            }
        )
    }

    addToPlaylistTrack?.let { track ->
        AddToPlaylistSheet(
            playlists = visiblePlaylists,
            trackTitle = track.title,
            onDismiss = { addToPlaylistTrack = null },
            onSelect = { playlistId ->
                viewModel.addToPlaylist(playlistId, track.id)
                addToPlaylistTrack = null
            },
            onCreateNew = {
                showCreateFromAdd = true
            }
        )
    }

    if (showCreateFromAdd) {
        CreatePlaylistDialog(
            onDismiss = { showCreateFromAdd = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name) { id ->
                    addToPlaylistTrack?.let { viewModel.addToPlaylist(id, it.id) }
                    addToPlaylistTrack = null
                }
                showCreateFromAdd = false
            }
        )
    }
}

/** Mirrors LibraryFoldersScreen picker entries so “Select all” matches the UI. */
private fun buildLibraryFolderPickerPaths(allFolders: List<String>): List<String> {
    val normalized = allFolders
        .map { it.trim().trim('/').replace('\\', '/') }
        .filter { it.isNotEmpty() }
    val entries = linkedSetOf<String>()
    for (path in normalized) {
        entries += path.substringBefore('/')
    }
    for (path in normalized) {
        val parts = path.split('/')
        if (parts.size >= 2) {
            entries += parts.take(2).joinToString("/")
        }
    }
    return entries.toList()
}
