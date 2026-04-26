package top.imsyy.splayer.nativeapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.imsyy.splayer.nativeapp.BuildConfig
import top.imsyy.splayer.nativeapp.AppSettingsProto
import top.imsyy.splayer.nativeapp.data.local.AppSettingsStore
import top.imsyy.splayer.nativeapp.data.repository.LyricsPreferences
import top.imsyy.splayer.nativeapp.data.repository.QueueRepository
import top.imsyy.splayer.nativeapp.data.repository.SPlayerRemoteRepository
import top.imsyy.splayer.nativeapp.model.AlbumDetailUi
import top.imsyy.splayer.nativeapp.model.DiscoveryBrowseUi
import top.imsyy.splayer.nativeapp.model.CommentItem
import top.imsyy.splayer.nativeapp.model.CommentPageResult
import top.imsyy.splayer.nativeapp.model.DiscoveryHomeUi
import top.imsyy.splayer.nativeapp.model.LyricLineUi
import top.imsyy.splayer.nativeapp.model.MyMusicHomeUi
import top.imsyy.splayer.nativeapp.model.MyMusicPanelTab
import top.imsyy.splayer.nativeapp.model.PodcastHomeUi
import top.imsyy.splayer.nativeapp.model.PlaylistDetailUi
import top.imsyy.splayer.nativeapp.model.PlaylistItem
import top.imsyy.splayer.nativeapp.model.MyMusicPanelUi
import top.imsyy.splayer.nativeapp.model.RecommendChannel
import top.imsyy.splayer.nativeapp.model.RecommendFeedUi
import top.imsyy.splayer.nativeapp.model.RadioDetailUi
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.model.ThemeMode
import top.imsyy.splayer.nativeapp.model.UserAccountUi
import top.imsyy.splayer.nativeapp.player.PlaybackCoordinator
import top.imsyy.splayer.nativeapp.ui.navigation.Routes

data class AppChromeUiState(
    val showQueueCount: Boolean = true,
    val currentUser: UserAccountUi? = null,
    val themeMode: ThemeMode = ThemeMode.DARK,
)

@HiltViewModel
class AppChromeViewModel @Inject constructor(
    appSettingsStore: AppSettingsStore,
) : ViewModel() {
    val uiState: StateFlow<AppChromeUiState> = appSettingsStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = appSettingsStore.settings.value,
        )
        .let { settingsFlow ->
            MutableStateFlow(
                AppChromeUiState(showQueueCount = settingsFlow.value.showQueueCount),
            ).also { output ->
                viewModelScope.launch {
                    settingsFlow.collect { settings ->
                        output.value = AppChromeUiState(
                            showQueueCount = settings.showQueueCount,
                            currentUser = settings.toStoredUserOrNull(),
                            themeMode = ThemeMode.fromRaw(settings.themeMode),
                        )
                    }
                }
            }
        }
        .asStateFlow()
}

data class HomeUiState(
    val currentUser: UserAccountUi? = null,
    val feed: RecommendFeedUi = RecommendFeedUi(),
    val selectedChannel: RecommendChannel = RecommendChannel.Recommend,
    val searchHint: String = "搜索歌曲、歌手、专辑",
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val remoteRepository: SPlayerRemoteRepository,
    queueRepository: QueueRepository,
    private val appSettingsStore: AppSettingsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var latestDiscoveryHome = DiscoveryHomeUi()

    private val recentFlow = queueRepository.observeRecent().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList(),
    )

    init {
        refresh()
        viewModelScope.launch {
            recentFlow.collect { recent ->
                _uiState.value = _uiState.value.copy(
                    feed = buildRecommendFeedUi(latestDiscoveryHome, recent),
                )
            }
        }
        viewModelScope.launch {
            appSettingsStore.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(currentUser = settings.toStoredUserOrNull())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, errorMessage = null)
            runCatching {
                supervisorScope {
                    val userDeferred = async { remoteRepository.fetchLoginState() }
                    val discoveryDeferred = async { remoteRepository.fetchDiscoveryHome() }
                    val searchHintDeferred = async { remoteRepository.fetchSearchDefault() }
                    Triple(
                        userDeferred.await(),
                        discoveryDeferred.await(),
                        searchHintDeferred.await(),
                    )
                }
            }.onSuccess { (user, discovery, searchHintResult) ->
                latestDiscoveryHome = discovery
                if (user != null) {
                    appSettingsStore.setAccount(user.userId, user.nickname, user.avatarUrl)
                }
                _uiState.value = _uiState.value.copy(
                    currentUser = user,
                    feed = buildRecommendFeedUi(discovery, recentFlow.value),
                    searchHint = searchHintResult.ifBlank { "搜索歌曲、歌手、专辑" },
                    loading = false,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    errorMessage = sanitizeLoadErrorMessage(error.message, "加载首页失败"),
                )
            }
        }
    }

    fun setChannel(channel: RecommendChannel) {
        _uiState.value = _uiState.value.copy(selectedChannel = channel)
    }
}

data class DiscoveryUiState(
    val content: DiscoveryBrowseUi = DiscoveryBrowseUi(),
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val remoteRepository: SPlayerRemoteRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = DiscoveryUiState(loading = true)
            runCatching {
                buildDiscoveryBrowseUi(remoteRepository.fetchDiscoveryHome())
            }.onSuccess { content ->
                _uiState.value = DiscoveryUiState(
                    content = content,
                    loading = false,
                )
            }.onFailure { error ->
                _uiState.value = DiscoveryUiState(
                    loading = false,
                    errorMessage = sanitizeLoadErrorMessage(error.message, "加载发现页失败"),
                )
            }
        }
    }
}

data class PodcastUiState(
    val content: PodcastHomeUi = PodcastHomeUi(),
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class PodcastViewModel @Inject constructor(
    private val remoteRepository: SPlayerRemoteRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PodcastUiState())
    val uiState: StateFlow<PodcastUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = PodcastUiState(loading = true)
            runCatching {
                remoteRepository.fetchPodcastHome()
            }.onSuccess { content ->
                _uiState.value = PodcastUiState(
                    content = content,
                    loading = false,
                )
            }.onFailure { error ->
                _uiState.value = PodcastUiState(
                    loading = false,
                    errorMessage = sanitizeLoadErrorMessage(error.message, "加载播客页失败"),
                )
            }
        }
    }
}

data class MyUiState(
    val content: MyMusicHomeUi = MyMusicHomeUi(),
    val panel: MyMusicPanelUi = MyMusicPanelUi(),
    val selectedTab: MyMusicPanelTab = MyMusicPanelTab.Recent,
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class MyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val remoteRepository: SPlayerRemoteRepository,
    queueRepository: QueueRepository,
) : ViewModel() {
    private val initialTab = savedStateHandle.get<String>(Routes.MyTabArg)
        ?.let { raw -> MyMusicPanelTab.entries.firstOrNull { it.name == raw } }
        ?: MyMusicPanelTab.Recent
    private val _uiState = MutableStateFlow(MyUiState(selectedTab = initialTab))
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()
    private var likedPlaylistWarmJob: Job? = null

    private val recentFlow = queueRepository.observeRecent().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList(),
    )

    init {
        viewModelScope.launch {
            recentFlow.collect { recent ->
                val content = _uiState.value.content.copy(recentTracks = recent)
                _uiState.value = _uiState.value.copy(
                    content = content,
                    panel = buildMyMusicPanel(content),
                )
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val recent = recentFlow.value
            _uiState.value = _uiState.value.copy(loading = true, errorMessage = null)
            runCatching {
                remoteRepository.fetchMyMusicHome(recent)
            }.onSuccess { content ->
                _uiState.value = MyUiState(
                    content = content,
                    panel = buildMyMusicPanel(content),
                    selectedTab = _uiState.value.selectedTab,
                    loading = false,
                )
                prewarmLikedPlaylist(content.likedPlaylist)
            }.onFailure { error ->
                val fallback = MyMusicHomeUi(recentTracks = recent)
                _uiState.value = MyUiState(
                    content = fallback,
                    panel = buildMyMusicPanel(fallback),
                    selectedTab = _uiState.value.selectedTab,
                    loading = false,
                    errorMessage = sanitizeLoadErrorMessage(error.message, "加载我的页面失败"),
                )
            }
        }
    }

    fun setTab(tab: MyMusicPanelTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    private fun prewarmLikedPlaylist(playlist: PlaylistItem?) {
        likedPlaylistWarmJob?.cancel()
        val target = playlist ?: return
        val cached = remoteRepository.peekCachedPlaylistDetail(target.id)
        if (cached?.tracks?.size?.coerceAtLeast(0) ?: 0 >= 80) {
            return
        }
        likedPlaylistWarmJob = viewModelScope.launch {
            delay(150)
            runCatching {
                remoteRepository.fetchPlaylistPreviewDetail(target.id)
                remoteRepository.fetchPlaylistInitialTracks(target.id)
            }
        }
    }

    override fun onCleared() {
        likedPlaylistWarmJob?.cancel()
        super.onCleared()
    }
}

data class SearchUiState(
    val keyword: String = "",
    val defaultHint: String = "",
    val hotKeywords: List<String> = emptyList(),
    val results: List<TrackItem> = emptyList(),
    val searching: Boolean = false,
    val errorMessage: String? = null,
)

data class PlaylistDetailUiState(
    val playlist: PlaylistDetailUi? = null,
    val loading: Boolean = true,
    val primingFirstPage: Boolean = false,
    val isAppending: Boolean = false,
    val hasMore: Boolean = false,
    val appendErrorMessage: String? = null,
    val errorMessage: String? = null,
)

data class AlbumDetailUiState(
    val album: AlbumDetailUi? = null,
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val remoteRepository: SPlayerRemoteRepository,
    private val playbackCoordinator: PlaybackCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private val initialKeyword = savedStateHandle.get<String>(Routes.SearchKeywordArg).orEmpty()

    init {
        viewModelScope.launch {
            runCatching {
                supervisorScope {
                    val defaultHintDeferred = async { remoteRepository.fetchSearchDefault() }
                    val hotKeywordsDeferred = async { remoteRepository.fetchSearchHot() }
                    defaultHintDeferred.await() to hotKeywordsDeferred.await()
                }
            }.onSuccess { (defaultHint, hotKeywords) ->
                _uiState.value = _uiState.value.copy(
                    defaultHint = defaultHint,
                    hotKeywords = hotKeywords,
                    errorMessage = null,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    defaultHint = "搜索歌曲、歌手、专辑",
                    hotKeywords = emptyList(),
                    errorMessage = sanitizeLoadErrorMessage(error.message, "搜索初始化失败"),
                )
            }
            if (initialKeyword.isNotBlank()) {
                _uiState.value = _uiState.value.copy(keyword = initialKeyword)
                submitSearch(initialKeyword)
            }
        }
    }

    fun updateKeyword(keyword: String) {
        _uiState.value = _uiState.value.copy(keyword = keyword)
    }

    fun submitSearch(keyword: String = _uiState.value.keyword) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searching = true, errorMessage = null, keyword = keyword)
            runCatching {
                remoteRepository.fetchSearchResult(keyword)
            }.onSuccess { results ->
                _uiState.value = _uiState.value.copy(
                    results = results,
                    searching = false,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    errorMessage = sanitizeLoadErrorMessage(error.message, "搜索失败"),
                )
            }
        }
    }

    fun playFromResults(track: TrackItem) {
        val results = _uiState.value.results
        val index = results.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        viewModelScope.launch {
            playbackCoordinator.playTracks(results, index)
        }
    }

    fun playSingleTrack(track: TrackItem) {
        viewModelScope.launch {
            playbackCoordinator.playTracks(listOf(track), 0)
        }
    }
}

data class RadioDetailUiState(
    val radio: RadioDetailUi? = null,
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val remoteRepository: SPlayerRemoteRepository,
) : ViewModel() {
    private companion object {
        const val PLAYLIST_FIRST_SCREEN_TARGET = 80
    }

    private val playlistId = savedStateHandle.get<String>(Routes.PlaylistIdArg)?.toLongOrNull() ?: 0L
    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (playlistId <= 0L) {
            _uiState.value = PlaylistDetailUiState(
                loading = false,
                errorMessage = "歌单参数无效",
            )
            return
        }
        viewModelScope.launch {
            val cached = remoteRepository.peekCachedPlaylistDetail(playlistId)
            _uiState.value = if (cached == null) {
                PlaylistDetailUiState(loading = true)
            } else {
                PlaylistDetailUiState(
                    playlist = cached,
                    loading = false,
                    hasMore = cached.tracks.size < cached.trackCount,
                )
            }
            runCatching {
                remoteRepository.fetchPlaylistPreviewDetail(
                    playlistId = playlistId,
                    forceRefresh = false,
                )
            }.onSuccess { playlist ->
                _uiState.value = PlaylistDetailUiState(
                    playlist = playlist,
                    loading = false,
                    primingFirstPage = shouldPrimeFirstPage(playlist),
                    hasMore = playlist.tracks.size < playlist.trackCount,
                )
                if (shouldPrimeFirstPage(playlist)) {
                    primeFirstPage(forceRefresh = cached == null)
                }
            }.onFailure { error ->
                if (cached == null) {
                    _uiState.value = PlaylistDetailUiState(
                        loading = false,
                        errorMessage = sanitizeLoadErrorMessage(error.message, "加载歌单失败"),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        primingFirstPage = false,
                    )
                }
            }
        }
    }

    private suspend fun primeFirstPage(forceRefresh: Boolean) {
        val currentState = _uiState.value
        val playlist = currentState.playlist ?: return
        if (!shouldPrimeFirstPage(playlist)) {
            _uiState.value = currentState.copy(
                primingFirstPage = false,
                hasMore = playlist.tracks.size < playlist.trackCount,
            )
            return
        }
        runCatching {
            remoteRepository.fetchPlaylistInitialTracks(
                playlistId = playlist.id,
                forceRefresh = forceRefresh,
            )
        }.onSuccess { page ->
            val latestState = _uiState.value
            val latestPlaylist = latestState.playlist ?: return@onSuccess
            val pageResult = resolveMergedTrackPage(
                existingTracks = latestPlaylist.tracks,
                incomingTracks = page,
                trackCount = latestPlaylist.trackCount,
            )
            _uiState.value = latestState.copy(
                playlist = latestPlaylist.copy(tracks = pageResult.tracks),
                primingFirstPage = false,
                hasMore = pageResult.hasMore,
                appendErrorMessage = null,
            )
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(
                primingFirstPage = false,
                appendErrorMessage = sanitizeLoadErrorMessage(error.message, "加载歌曲列表失败"),
            )
        }
    }

    fun loadMore() {
        val currentState = _uiState.value
        val playlist = currentState.playlist ?: return
        if (currentState.loading || currentState.primingFirstPage || currentState.isAppending || !currentState.hasMore) return
        viewModelScope.launch {
            _uiState.value = currentState.copy(
                isAppending = true,
                appendErrorMessage = null,
            )
            runCatching {
                remoteRepository.fetchPlaylistTracksPage(
                    playlistId = playlist.id,
                    offset = playlist.tracks.size,
                )
            }.onSuccess { page ->
                val latestPlaylist = _uiState.value.playlist ?: playlist
                val pageResult = resolveMergedTrackPage(
                    existingTracks = latestPlaylist.tracks,
                    incomingTracks = page,
                    trackCount = latestPlaylist.trackCount,
                )
                _uiState.value = _uiState.value.copy(
                    playlist = latestPlaylist.copy(tracks = pageResult.tracks),
                    isAppending = false,
                    hasMore = pageResult.hasMore,
                    appendErrorMessage = null,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isAppending = false,
                    appendErrorMessage = sanitizeLoadErrorMessage(error.message, "加载更多歌曲失败"),
                )
            }
        }
    }

    suspend fun ensureAllTracksLoaded(): List<TrackItem> {
        val currentState = _uiState.value
        val playlist = currentState.playlist ?: return emptyList()
        if (!currentState.hasMore) return playlist.tracks

        var mergedTracks = playlist.tracks
        _uiState.value = currentState.copy(
            isAppending = true,
            appendErrorMessage = null,
        )
        var requestedPages = 0
        val maxPages = resolveMaxPlaylistPageRequests(
            loadedTrackCount = mergedTracks.size,
            trackCount = playlist.trackCount,
        )
        var canContinue = true
        while (canContinue && mergedTracks.size < playlist.trackCount && requestedPages < maxPages) {
            requestedPages += 1
            val page = runCatching {
                remoteRepository.fetchPlaylistTracksPage(
                    playlistId = playlist.id,
                    offset = mergedTracks.size,
                )
            }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    isAppending = false,
                    appendErrorMessage = sanitizeLoadErrorMessage(error.message, "加载更多歌曲失败"),
                )
                return mergedTracks
            }
            val pageResult = resolveMergedTrackPage(
                existingTracks = mergedTracks,
                incomingTracks = page,
                trackCount = playlist.trackCount,
            )
            mergedTracks = pageResult.tracks
            _uiState.value = _uiState.value.copy(
                playlist = playlist.copy(tracks = mergedTracks),
                hasMore = pageResult.hasMore,
            )
            canContinue = pageResult.hasMore
        }
        _uiState.value = _uiState.value.copy(
            isAppending = false,
            hasMore = canContinue && mergedTracks.size < playlist.trackCount && requestedPages < maxPages,
            appendErrorMessage = null,
        )
        return mergedTracks
    }

    private fun shouldPrimeFirstPage(playlist: PlaylistDetailUi): Boolean {
        return playlist.trackCount > playlist.tracks.size &&
            playlist.tracks.size < PLAYLIST_FIRST_SCREEN_TARGET
    }
}

@HiltViewModel
class RadioDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val remoteRepository: SPlayerRemoteRepository,
) : ViewModel() {
    private val radioId = savedStateHandle.get<String>(Routes.RadioIdArg)?.toLongOrNull() ?: 0L
    private val _uiState = MutableStateFlow(RadioDetailUiState())
    val uiState: StateFlow<RadioDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (radioId <= 0L) {
            _uiState.value = RadioDetailUiState(
                loading = false,
                errorMessage = "播客参数无效",
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = RadioDetailUiState(loading = true)
            runCatching {
                remoteRepository.fetchRadioDetail(radioId)
            }.onSuccess { radio ->
                _uiState.value = RadioDetailUiState(
                    radio = radio,
                    loading = false,
                )
            }.onFailure { error ->
                _uiState.value = RadioDetailUiState(
                    loading = false,
                    errorMessage = sanitizeLoadErrorMessage(error.message, "加载播客详情失败"),
                )
            }
        }
    }
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val remoteRepository: SPlayerRemoteRepository,
) : ViewModel() {
    private val albumId = savedStateHandle.get<String>(Routes.AlbumIdArg)?.toLongOrNull() ?: 0L
    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (albumId <= 0L) {
            _uiState.value = AlbumDetailUiState(
                loading = false,
                errorMessage = "专辑参数无效",
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = AlbumDetailUiState(loading = true)
            runCatching {
                remoteRepository.fetchAlbumDetail(albumId)
            }.onSuccess { album ->
                _uiState.value = AlbumDetailUiState(
                    album = album,
                    loading = false,
                )
            }.onFailure { error ->
                _uiState.value = AlbumDetailUiState(
                    loading = false,
                    errorMessage = sanitizeLoadErrorMessage(error.message, "加载专辑失败"),
                )
            }
        }
    }
}

data class PlayerScreenState(
    val activeTrackId: Long? = null,
    val commentTrackId: Long? = null,
    val lyrics: List<LyricLineUi> = emptyList(),
    val hotComments: List<CommentItem> = emptyList(),
    val latestComments: List<CommentItem> = emptyList(),
    val lyricLoading: Boolean = false,
    val commentLoading: Boolean = false,
    val loadingMoreComments: Boolean = false,
    val totalCommentCount: Int = 0,
    val hotCommentCount: Int = 0,
    val latestCommentPage: Int = 1,
    val latestCommentHasMore: Boolean = false,
    val showTranslation: Boolean = true,
    val showRomanized: Boolean = false,
    val lyricFontScale: Float = 1f,
)

data class PlayerOverlayState(
    val showQueue: Boolean = false,
    val showComments: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackCoordinator: PlaybackCoordinator,
    private val remoteRepository: SPlayerRemoteRepository,
    private val lyricsPreferences: LyricsPreferences,
) : ViewModel() {
    val playbackState = playbackCoordinator.uiState

    private val _screenState = MutableStateFlow(PlayerScreenState())
    val screenState: StateFlow<PlayerScreenState> = _screenState.asStateFlow()

    private val _overlayState = MutableStateFlow(PlayerOverlayState())
    val overlayState: StateFlow<PlayerOverlayState> = _overlayState.asStateFlow()
    private var commentsPrewarmJob: Job? = null
    private var lyricPrewarmJob: Job? = null

    init {
        viewModelScope.launch {
            lyricsPreferences.showTranslation.collect { enabled ->
                _screenState.value = _screenState.value.copy(showTranslation = enabled)
            }
        }
        viewModelScope.launch {
            lyricsPreferences.showRomanized.collect { enabled ->
                _screenState.value = _screenState.value.copy(showRomanized = enabled)
            }
        }
        viewModelScope.launch {
            lyricsPreferences.lyricFontScale.collect { scale ->
                _screenState.value = _screenState.value.copy(lyricFontScale = scale)
            }
        }
    }

    fun togglePlayback() = playbackCoordinator.togglePlayback()
    fun skipNext() = playbackCoordinator.skipNext()
    fun skipPrevious() = playbackCoordinator.skipPrevious()
    fun cyclePlayMode() = playbackCoordinator.cyclePlayMode()
    fun seekTo(positionMs: Long) = playbackCoordinator.seekTo(positionMs)
    fun playQueueIndex(index: Int) = playbackCoordinator.playQueueIndex(index)
    fun setPlayerScreenCadence(
        playerScreenActive: Boolean,
        lyricScreenActive: Boolean,
        wordLevelLyricActive: Boolean,
    ) = playbackCoordinator.setProgressCadence(
        playerScreenActive = playerScreenActive,
        lyricScreenActive = lyricScreenActive,
        wordLevelLyricActive = wordLevelLyricActive,
    )

    fun setShowTranslation(enabled: Boolean) {
        viewModelScope.launch {
            lyricsPreferences.setShowTranslation(enabled)
        }
    }

    fun setShowRomanized(enabled: Boolean) {
        viewModelScope.launch {
            lyricsPreferences.setShowRomanized(enabled)
        }
    }

    fun playSingleTrack(track: TrackItem) {
        viewModelScope.launch {
            playbackCoordinator.playTracks(listOf(track), 0)
        }
    }

    fun playTracks(tracks: List<TrackItem>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            playbackCoordinator.playTracks(tracks, startIndex.coerceIn(0, tracks.lastIndex))
        }
    }

    fun openQueueSheet() {
        _overlayState.value = _overlayState.value.copy(showQueue = true)
    }

    fun dismissQueueSheet() {
        _overlayState.value = _overlayState.value.copy(showQueue = false)
    }

    fun openCommentsSheet() {
        _overlayState.value = _overlayState.value.copy(showComments = true)
        ensureCommentsLoaded(playbackState.value.currentTrack)
    }

    fun dismissCommentsSheet() {
        _overlayState.value = _overlayState.value.copy(showComments = false)
    }

    fun loadTrackMeta(track: TrackItem?) {
        if (track == null) return
        val current = _screenState.value
        if (current.activeTrackId == track.id && (current.lyricLoading || current.lyrics.isNotEmpty())) {
            prewarmComments(track)
            prewarmNextTrackLyrics(track)
            return
        }
        val cachedLyrics = remoteRepository.peekCachedLyrics(track.id).orEmpty()
        val cachedHotComments = remoteRepository.peekCachedHotComments(track.id)
        val cachedLatestComments = remoteRepository.peekCachedLatestComments(track.id, pageNo = 1)
        val cachedCommentCount = resolveCommentPreviewCount(
            existingCount = 0,
            hotComments = cachedHotComments,
            latestComments = cachedLatestComments,
        )
        viewModelScope.launch {
            _screenState.value = _screenState.value.copy(
                activeTrackId = track.id,
                lyricLoading = cachedLyrics.isEmpty(),
                commentLoading = false,
                loadingMoreComments = false,
                lyrics = cachedLyrics,
                hotComments = emptyList(),
                latestComments = emptyList(),
                totalCommentCount = cachedCommentCount,
                hotCommentCount = cachedHotComments?.let { page ->
                    page.totalCount.coerceAtLeast(page.comments.size)
                } ?: 0,
                latestCommentPage = 1,
                latestCommentHasMore = false,
                commentTrackId = null,
            )
            prewarmComments(track)
            prewarmNextTrackLyrics(track)
            if (cachedLyrics.isNotEmpty()) {
                return@launch
            }
            val lyrics = runCatching { remoteRepository.fetchLyrics(track) }.getOrDefault(emptyList())
            if (_screenState.value.activeTrackId != track.id) return@launch
            _screenState.value = _screenState.value.copy(
                lyrics = lyrics,
                lyricLoading = false,
            )
        }
    }

    private fun prewarmNextTrackLyrics(track: TrackItem) {
        val queue = playbackState.value.queue
        val currentIndex = queue.indexOfFirst { item -> item.id == track.id }
        if (currentIndex < 0) return
        val nextTrack = queue.getOrNull(currentIndex + 1) ?: return
        if (remoteRepository.peekCachedLyrics(nextTrack.id) != null) {
            return
        }
        lyricPrewarmJob?.cancel()
        lyricPrewarmJob = viewModelScope.launch {
            delay(180)
            if (_screenState.value.activeTrackId != track.id || playbackState.value.currentTrack?.id != track.id) {
                return@launch
            }
            runCatching { remoteRepository.fetchLyrics(nextTrack) }
        }
    }

    private fun ensureCommentsLoaded(track: TrackItem?) {
        if (track == null) return
        commentsPrewarmJob?.cancel()
        val state = _screenState.value
        if (state.commentTrackId == track.id && (state.commentLoading || state.hotComments.isNotEmpty() || state.latestComments.isNotEmpty())) {
            return
        }
        val cachedHotComments = remoteRepository.peekCachedHotComments(track.id)
        val cachedLatestComments = remoteRepository.peekCachedLatestComments(track.id, pageNo = 1)
        viewModelScope.launch {
            _screenState.value = _screenState.value.copy(
                commentTrackId = track.id,
                commentLoading = cachedLatestComments == null,
                loadingMoreComments = false,
                hotComments = cachedHotComments?.comments.orEmpty(),
                latestComments = cachedLatestComments?.comments.orEmpty(),
                totalCommentCount = cachedLatestComments?.totalCount
                    ?.coerceAtLeast(cachedHotComments?.totalCount ?: 0)
                    ?: cachedHotComments?.totalCount
                    ?: 0,
                hotCommentCount = cachedHotComments?.totalCount ?: 0,
                latestCommentPage = 1,
                latestCommentHasMore = cachedLatestComments?.hasMore ?: false,
            )
            if (cachedHotComments != null && cachedLatestComments != null) {
                return@launch
            }
            supervisorScope {
                val latestCommentsDeferred = if (cachedLatestComments == null) {
                    async {
                        runCatching { remoteRepository.fetchLatestComments(track.id, pageNo = 1) }
                            .getOrDefault(CommentPageResult(emptyList()))
                    }
                } else {
                    null
                }
                val hotCommentsDeferred = if (cachedHotComments == null) {
                    async {
                        runCatching { remoteRepository.fetchHotComments(track.id) }
                            .getOrDefault(CommentPageResult(emptyList()))
                    }
                } else {
                    null
                }
                val latestComments = cachedLatestComments ?: latestCommentsDeferred?.await() ?: CommentPageResult(emptyList())
                if (_screenState.value.commentTrackId != track.id) return@supervisorScope
                _screenState.value = _screenState.value.copy(
                    commentTrackId = track.id,
                    latestComments = latestComments.comments,
                    commentLoading = false,
                    totalCommentCount = latestComments.totalCount.coerceAtLeast(cachedHotComments?.totalCount ?: 0),
                    latestCommentPage = 1,
                    latestCommentHasMore = latestComments.hasMore,
                )
                val hotComments = cachedHotComments ?: hotCommentsDeferred?.await() ?: CommentPageResult(emptyList())
                if (_screenState.value.commentTrackId != track.id) return@supervisorScope
                _screenState.value = _screenState.value.copy(
                    commentTrackId = track.id,
                    hotComments = hotComments.comments,
                    commentLoading = false,
                    totalCommentCount = latestComments.totalCount.coerceAtLeast(hotComments.totalCount),
                    hotCommentCount = hotComments.totalCount,
                    latestCommentPage = 1,
                    latestCommentHasMore = latestComments.hasMore,
                )
            }
        }
    }

    private fun prewarmComments(track: TrackItem) {
        val cachedHotComments = remoteRepository.peekCachedHotComments(track.id)
        val cachedLatestComments = remoteRepository.peekCachedLatestComments(track.id, pageNo = 1)
        publishCommentPreviewCount(
            trackId = track.id,
            hotComments = cachedHotComments,
            latestComments = cachedLatestComments,
        )
        if (cachedHotComments != null && cachedLatestComments != null) {
            return
        }
        commentsPrewarmJob?.cancel()
        commentsPrewarmJob = viewModelScope.launch {
            delay(150)
            if (_screenState.value.activeTrackId != track.id || _overlayState.value.showComments) {
                return@launch
            }
            supervisorScope {
                val latestDeferred = if (cachedLatestComments == null) {
                    async {
                        runCatching { remoteRepository.fetchLatestComments(track.id, pageNo = 1) }
                    }
                } else {
                    null
                }
                val hotDeferred = if (cachedHotComments == null) {
                    async {
                        runCatching { remoteRepository.fetchHotComments(track.id) }
                    }
                } else {
                    null
                }
                val latestComments = latestDeferred?.await()?.getOrNull() ?: cachedLatestComments
                val hotComments = hotDeferred?.await()?.getOrNull() ?: cachedHotComments
                publishCommentPreviewCount(
                    trackId = track.id,
                    hotComments = hotComments,
                    latestComments = latestComments,
                )
            }
        }
    }

    private fun publishCommentPreviewCount(
        trackId: Long,
        hotComments: CommentPageResult?,
        latestComments: CommentPageResult?,
    ) {
        val state = _screenState.value
        if (state.activeTrackId != trackId) return
        _screenState.value = state.copy(
            totalCommentCount = resolveCommentPreviewCount(
                existingCount = state.totalCommentCount,
                hotComments = hotComments,
                latestComments = latestComments,
            ),
            hotCommentCount = maxOf(
                state.hotCommentCount,
                hotComments?.let { page -> page.totalCount.coerceAtLeast(page.comments.size) } ?: 0,
            ),
        )
    }

    fun loadMoreLatestComments() {
        val currentTrack = playbackState.value.currentTrack ?: return
        val state = _screenState.value
        if (state.commentLoading || state.loadingMoreComments || !state.latestCommentHasMore) return
        viewModelScope.launch {
            _screenState.value = _screenState.value.copy(loadingMoreComments = true)
            val nextPage = state.latestCommentPage + 1
            val nextCursor = state.latestComments.lastOrNull()?.time?.takeIf { it > 0L }
            runCatching {
                remoteRepository.fetchLatestComments(
                    currentTrack.id,
                    pageNo = nextPage,
                    cursor = nextCursor,
                )
            }.onSuccess { page ->
                _screenState.value = _screenState.value.copy(
                    latestComments = mergeCommentItems(_screenState.value.latestComments, page.comments),
                    latestCommentPage = nextPage,
                    latestCommentHasMore = page.hasMore,
                    totalCommentCount = page.totalCount.coerceAtLeast(_screenState.value.totalCommentCount),
                    loadingMoreComments = false,
                )
            }.onFailure {
                _screenState.value = _screenState.value.copy(loadingMoreComments = false)
            }
        }
    }

    override fun onCleared() {
        commentsPrewarmJob?.cancel()
        lyricPrewarmJob?.cancel()
        super.onCleared()
    }
}

private fun mergeTrackItems(
    existing: List<TrackItem>,
    incoming: List<TrackItem>,
): List<TrackItem> {
    if (existing.isEmpty()) return incoming
    if (incoming.isEmpty()) return existing
    return (existing + incoming).distinctBy { track -> track.id }
}

internal data class MergedTrackPage(
    val tracks: List<TrackItem>,
    val hasMore: Boolean,
    val madeProgress: Boolean,
)

internal fun resolveMergedTrackPage(
    existingTracks: List<TrackItem>,
    incomingTracks: List<TrackItem>,
    trackCount: Int,
): MergedTrackPage {
    if (incomingTracks.isEmpty()) {
        return MergedTrackPage(
            tracks = existingTracks,
            hasMore = false,
            madeProgress = false,
        )
    }
    val mergedTracks = mergeTrackItems(existingTracks, incomingTracks)
    val madeProgress = mergedTracks.size > existingTracks.size
    return MergedTrackPage(
        tracks = mergedTracks,
        hasMore = madeProgress && mergedTracks.size < trackCount,
        madeProgress = madeProgress,
    )
}

internal fun resolveMaxPlaylistPageRequests(
    loadedTrackCount: Int,
    trackCount: Int,
    pageSize: Int = 200,
): Int {
    val remaining = (trackCount - loadedTrackCount).coerceAtLeast(0)
    if (remaining == 0) return 0
    return ((remaining + pageSize - 1) / pageSize).coerceAtLeast(1) + 1
}

private fun mergeCommentItems(
    existing: List<CommentItem>,
    incoming: List<CommentItem>,
): List<CommentItem> {
    if (existing.isEmpty()) return incoming
    if (incoming.isEmpty()) return existing
    return (existing + incoming).distinctBy { comment -> comment.id }
}

internal fun resolveCommentPreviewCount(
    existingCount: Int,
    hotComments: CommentPageResult?,
    latestComments: CommentPageResult?,
): Int {
    fun CommentPageResult.visibleCount(): Int {
        return totalCount.coerceAtLeast(comments.size).coerceAtLeast(0)
    }

    return maxOf(
        existingCount.coerceAtLeast(0),
        hotComments?.visibleCount() ?: 0,
        latestComments?.visibleCount() ?: 0,
    )
}

data class SettingsUiState(
    val currentUser: UserAccountUi? = null,
    val apiRoot: String = "",
    val appMode: String = BuildConfig.APP_MODE,
    val qrImageUrl: String? = null,
    val qrStatusText: String = "未开始",
    val qrLoading: Boolean = false,
    val qrVisible: Boolean = false,
    val showTranslation: Boolean = true,
    val showRomanized: Boolean = false,
    val autoPlay: Boolean = true,
    val showQueueCount: Boolean = true,
    val lyricFontScale: Int = 100,
    val themeMode: ThemeMode = ThemeMode.DARK,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val remoteRepository: SPlayerRemoteRepository,
    private val appSettingsStore: AppSettingsStore,
    private val lyricsPreferences: LyricsPreferences,
    private val queueRepository: QueueRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(apiRoot = appSettingsStore.apiRoot))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var qrLoginJob: Job? = null
    private var qrPollingJob: Job? = null

    init {
        refreshAccount()
        viewModelScope.launch {
            appSettingsStore.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    apiRoot = settings.apiRoot,
                    currentUser = settings.toStoredUserOrNull(),
                    showTranslation = settings.showTranslation,
                    showRomanized = settings.showRoma,
                    autoPlay = settings.autoPlay,
                    showQueueCount = settings.showQueueCount,
                    lyricFontScale = settings.lyricFontScale.coerceIn(85, 135),
                    themeMode = ThemeMode.fromRaw(settings.themeMode),
                )
            }
        }
    }

    fun refreshAccount() {
        viewModelScope.launch {
            val user = fetchLoginStateWithRetry()
            if (user != null) {
                appSettingsStore.setAccount(user.userId, user.nickname, user.avatarUrl)
            }
            _uiState.value = _uiState.value.copy(
                currentUser = user,
                qrVisible = if (user != null) false else _uiState.value.qrVisible,
                qrLoading = false,
            )
        }
    }

    fun startQrLogin() {
        qrLoginJob?.cancel()
        qrPollingJob?.cancel()
        val loginJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                qrVisible = true,
                qrLoading = true,
                qrImageUrl = null,
                qrStatusText = "生成二维码中",
            )
            runCatching {
                val key = remoteRepository.fetchQrKey()
                val qrImage = remoteRepository.fetchQrImage(key)
                if (!isActive) return@launch
                _uiState.value = _uiState.value.copy(
                    qrImageUrl = qrImage,
                    qrLoading = false,
                    qrStatusText = "等待扫码确认",
                    qrVisible = true,
                )
                qrPollingJob = launch {
                    while (isActive && _uiState.value.qrVisible && _uiState.value.currentUser == null) {
                        delay(2_000)
                        val result = remoteRepository.checkQrState(key)
                        if (result.cookieHeader.isNotBlank()) {
                            appSettingsStore.updateCookiesFromHeader(result.cookieHeader)
                        }
                        val status = when (result.code) {
                            800 -> "二维码已过期"
                            801 -> "等待扫码"
                            802 -> "已扫码，请在手机确认"
                            803 -> "登录成功"
                            else -> "登录状态未知"
                        }
                        _uiState.value = _uiState.value.copy(qrStatusText = status)
                        if (result.code == 803 || result.code == 800) {
                            if (result.code == 803) {
                                _uiState.value = _uiState.value.copy(qrVisible = false)
                                refreshAccount()
                            }
                            break
                        }
                    }
                }.also { job ->
                    job.invokeOnCompletion {
                        if (qrPollingJob === job) {
                            qrPollingJob = null
                        }
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) return@launch
                _uiState.value = _uiState.value.copy(
                    qrVisible = true,
                    qrLoading = false,
                    qrImageUrl = null,
                    qrStatusText = error.message ?: "二维码登录失败",
                )
            }
        }
        qrLoginJob = loginJob
        loginJob.invokeOnCompletion {
            if (qrLoginJob === loginJob) {
                qrLoginJob = null
            }
        }
    }

    fun stopQrLogin() {
        qrLoginJob?.cancel()
        qrLoginJob = null
        qrPollingJob?.cancel()
        qrPollingJob = null
        _uiState.value = _uiState.value.copy(
            qrVisible = false,
            qrLoading = false,
        )
    }

    override fun onCleared() {
        stopQrLogin()
        super.onCleared()
    }

    fun setShowTranslation(enabled: Boolean) {
        viewModelScope.launch {
            lyricsPreferences.setShowTranslation(enabled)
        }
    }

    fun setShowRomanized(enabled: Boolean) {
        viewModelScope.launch {
            lyricsPreferences.setShowRomanized(enabled)
        }
    }

    fun setAutoPlay(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsStore.setAutoPlay(enabled)
        }
    }

    fun setShowQueueCount(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsStore.setShowQueueCount(enabled)
        }
    }

    fun setLyricFontScale(scale: Int) {
        viewModelScope.launch {
            lyricsPreferences.setLyricFontScale(scale)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appSettingsStore.setThemeMode(mode)
        }
    }

    fun clearRecentTracks() {
        viewModelScope.launch {
            queueRepository.clearRecent()
        }
    }

    fun clearFailedSourceMemory() {
        viewModelScope.launch {
            queueRepository.clearAllFailedSources()
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            queueRepository.clearQueue()
        }
    }

    private suspend fun fetchLoginStateWithRetry(maxAttempts: Int = 5): UserAccountUi? {
        repeat(maxAttempts) { index ->
            val user = runCatching { remoteRepository.fetchLoginState() }.getOrNull()
            if (user != null) return user
            if (index < maxAttempts - 1) {
                delay(800)
            }
        }
        return null
    }
}

internal fun AppSettingsProto.toStoredUserOrNull(): UserAccountUi? {
    val userId = accountId.toLongOrNull() ?: return null
    return UserAccountUi(
        userId = userId,
        nickname = accountNickname.ifBlank { "已登录用户" },
        avatarUrl = accountAvatarUrl,
    )
}

internal fun sanitizeLoadErrorMessage(
    rawMessage: String?,
    fallback: String,
): String {
    val message = rawMessage?.trim().orEmpty()
    if (message.isBlank()) {
        return "$fallback，请稍后重试"
    }
    val lowered = message.lowercase()
    val looksLikeParserNoise = lowered.contains("unexpected json token") ||
        lowered.contains("json input:") ||
        lowered.contains("expected eof after parsing") ||
        lowered.contains("offset") && lowered.contains("path:")
    val looksLikeDuplicatedPayload = message.count { it == '{' } > 1 && message.contains("\"code\"")
    if (looksLikeParserNoise || looksLikeDuplicatedPayload) {
        return "$fallback，请稍后重试"
    }
    return message
}
