package top.imsyy.splayer.nativeapp.data.repository

import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import top.imsyy.splayer.nativeapp.data.api.SPlayerApiService
import top.imsyy.splayer.nativeapp.data.local.PlaylistDetailCacheDao
import top.imsyy.splayer.nativeapp.data.local.PlaylistDetailCacheEntity
import top.imsyy.splayer.nativeapp.model.CommentItem
import top.imsyy.splayer.nativeapp.model.CommentPageResult
import top.imsyy.splayer.nativeapp.model.DiscoveryHomeUi
import top.imsyy.splayer.nativeapp.model.LyricLineUi
import top.imsyy.splayer.nativeapp.model.LyricWordUi
import top.imsyy.splayer.nativeapp.model.AlbumDetailUi
import top.imsyy.splayer.nativeapp.model.AlbumItem
import top.imsyy.splayer.nativeapp.model.ListeningRankItem
import top.imsyy.splayer.nativeapp.model.PlaylistDetailUi
import top.imsyy.splayer.nativeapp.model.PlaylistItem
import top.imsyy.splayer.nativeapp.model.PodcastHomeUi
import top.imsyy.splayer.nativeapp.model.RadioCategoryUi
import top.imsyy.splayer.nativeapp.model.RadioDetailUi
import top.imsyy.splayer.nativeapp.model.RadioItem
import top.imsyy.splayer.nativeapp.model.ArtistItem
import top.imsyy.splayer.nativeapp.model.MyMusicHomeUi
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.model.TrackSource
import top.imsyy.splayer.nativeapp.model.UserAccountUi

data class QrCheckState(
    val code: Int,
    val cookieHeader: String = "",
)

internal class BoundedMemoryCache<K, V>(
    private val maxEntries: Int,
) {
    private val entries = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    operator fun get(key: K): V? = entries[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        entries[key] = value
    }

    @Synchronized
    fun size(): Int = entries.size
}

@Singleton
class SPlayerRemoteRepository @Inject constructor(
    private val api: SPlayerApiService,
    private val playlistDetailCacheDao: PlaylistDetailCacheDao? = null,
) {
    private companion object {
        const val DIRECT_NETEASE_UNLOCK_BASE_URL = "https://music-api.gdstudio.xyz/api.php"
        const val NATIVE_NETEASE_UNLOCK_SOURCE = "native-netease"
        const val PLAYLIST_PREVIEW_SIZE = 20
        const val PLAYLIST_INITIAL_PAGE_SIZE = 80
        const val PLAYLIST_PAGE_SIZE = 500
        const val DISCOVERY_HOME_CACHE_TTL_MS = 30 * 60 * 1000L
        const val DETAIL_CACHE_LIMIT = 40
        const val PLAYLIST_PAGE_CACHE_LIMIT = 120
        const val LYRIC_CACHE_LIMIT = 128
        const val HOT_COMMENT_CACHE_LIMIT = 96
        const val LATEST_COMMENT_CACHE_LIMIT = 160
    }

    private val officialLevels = listOf("exhigh", "higher", "standard")
    private val playlistDetailCache = BoundedMemoryCache<Long, PlaylistDetailUi>(DETAIL_CACHE_LIMIT)
    private val playlistPageCache = BoundedMemoryCache<String, List<TrackItem>>(PLAYLIST_PAGE_CACHE_LIMIT)
    private val lyricCache = BoundedMemoryCache<Long, List<LyricLineUi>>(LYRIC_CACHE_LIMIT)
    private val hotCommentCache = BoundedMemoryCache<Long, CommentPageResult>(HOT_COMMENT_CACHE_LIMIT)
    private val latestCommentCache = BoundedMemoryCache<String, CommentPageResult>(LATEST_COMMENT_CACHE_LIMIT)
    private val albumDetailCache = BoundedMemoryCache<Long, AlbumDetailUi>(DETAIL_CACHE_LIMIT)
    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<Any?>>()
    private val inFlightLock = Any()
    @Volatile
    private var discoveryHomeCache: DiscoveryHomeUi? = null

    @Volatile
    private var discoveryHomeCachedAtMs: Long = 0L

    @Volatile
    private var podcastHomeCache: PodcastHomeUi? = null

    @Volatile
    private var searchDefaultCache: String? = null

    @Volatile
    private var searchHotCache: List<String>? = null
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun peekCachedPlaylistDetail(playlistId: Long): PlaylistDetailUi? = playlistDetailCache[playlistId]

    suspend fun readCachedPlaylistDetail(playlistId: Long): PlaylistDetailUi? {
        playlistDetailCache[playlistId]?.let { return it }
        val cached = playlistDetailCacheDao?.findById(playlistId)?.toPlaylistDetailUi(json) ?: return null
        playlistDetailCache[playlistId] = cached
        return cached
    }

    fun peekCachedLyrics(trackId: Long): List<LyricLineUi>? = lyricCache[trackId]

    fun peekCachedHotComments(songId: Long): CommentPageResult? = hotCommentCache[songId]

    fun peekCachedLatestComments(songId: Long, pageNo: Int = 1, cursor: Long? = null): CommentPageResult? {
        return latestCommentCache[latestCommentCacheKey(songId, pageNo, cursor)]
    }

    suspend fun fetchPlaylistPreviewDetail(
        playlistId: Long,
        forceRefresh: Boolean = false,
    ): PlaylistDetailUi {
        if (!forceRefresh) {
            playlistDetailCache[playlistId]?.let { return it }
            readCachedPlaylistDetail(playlistId)?.let { return it }
        }
        return awaitInFlight("playlist-preview:$playlistId") {
            val playlist = requestPlaylistPayload(playlistId)
            val cachedTracks = playlistDetailCache[playlistId]?.tracks.orEmpty()
            val previewTracks = playlist.array("tracks").mapNotNull { it.toTrackItem() }.take(PLAYLIST_PREVIEW_SIZE)
            buildPlaylistDetail(
                playlistId = playlistId,
                playlist = playlist,
                tracks = mergeTrackItems(cachedTracks, previewTracks),
            ).also { detail ->
                playlistDetailCache[playlistId] = detail
                persistPlaylistDetail(detail)
            }
        }
    }

    suspend fun fetchPlaylistInitialTracks(
        playlistId: Long,
        forceRefresh: Boolean = false,
    ): List<TrackItem> {
        return fetchPlaylistTracksPage(
            playlistId = playlistId,
            offset = 0,
            limit = PLAYLIST_INITIAL_PAGE_SIZE,
            forceRefresh = forceRefresh,
        )
    }

    suspend fun fetchLoginState(): UserAccountUi? {
        val result = getNetease("login/status")
        val data = result.obj("data")
        val loginUser = data.obj("account").toUserAccount(profile = data.obj("profile"))
        val accountUser = runCatching { getNetease("user/account").obj("profile").toUserAccount() }.getOrNull()
        if (accountUser != null) return accountUser
        if (loginUser != null) return loginUser

        return null
    }

    suspend fun fetchQrKey(): String {
        return getNetease("login/qr/key", mapOf("timestamp" to now())).obj("data").string("unikey")
    }

    suspend fun fetchQrImage(key: String): String {
        return getNetease(
            "login/qr/create",
            mapOf(
                "key" to key,
                "qrimg" to "true",
                "timestamp" to now(),
            ),
        ).obj("data").string("qrimg")
    }

    suspend fun checkQrState(key: String): QrCheckState {
        val response = getNetease(
            "login/qr/check",
            mapOf("key" to key, "timestamp" to now()),
        )
        return QrCheckState(
            code = response.int("code"),
            cookieHeader = response.string("cookie"),
        )
    }

    suspend fun fetchPersonalizedPlaylists(limit: Int = 12): List<PlaylistItem> {
        return getNetease("personalized", mapOf("limit" to limit.toString()))
            .array("result")
            .mapNotNull { item -> item.toPlaylistItem() }
    }

    suspend fun fetchDiscoveryHome(forceRefresh: Boolean = false): DiscoveryHomeUi {
        if (!forceRefresh) {
            discoveryHomeCache
                ?.takeIf { System.currentTimeMillis() - discoveryHomeCachedAtMs < DISCOVERY_HOME_CACHE_TTL_MS }
                ?.let { return it }
        }
        return supervisorScope {
            val timestampParam = if (forceRefresh) mapOf("timestamp" to now()) else emptyMap()
            val homepageBlockResponse = async {
                if (forceRefresh) {
                    runCatching {
                        getNetease(
                            "homepage/block/page",
                            mapOf("refresh" to "true") + timestampParam,
                        )
                    }.getOrNull()
                } else {
                    null
                }
            }
            val playlistResponse = async { getNetease("personalized", mapOf("limit" to "12") + timestampParam) }
            val dailySongResponse = async {
                runCatching { getNetease("recommend/songs", timestampParam) }.getOrNull()
            }
            val songResponse = async { getNetease("top/song", mapOf("type" to "0") + timestampParam) }
            val artistResponse = async { getNetease("top/artists", mapOf("limit" to "12") + timestampParam) }
            val albumResponse = async { getNetease("album/new", timestampParam) }
            val toplistResponse = async { getNetease("toplist/detail", timestampParam) }
            val newSongs = songResponse.await().array("data").mapNotNull { it.toTrackItem() }
            val dailySongs = dailySongResponse.await()
                ?.obj("data")
                ?.array("dailySongs")
                ?.mapNotNull { it.toTrackItem() }
                .orEmpty()
                .ifEmpty { newSongs }
            val blockDiscoveryHome = homepageBlockResponse.await()?.toDiscoveryHomeUi()
            DiscoveryHomeUi(
                recommendedPlaylists = blockDiscoveryHome?.recommendedPlaylists.orEmpty()
                    .ifEmpty { playlistResponse.await().array("result").mapNotNull { it.toPlaylistItem() } },
                dailySongs = blockDiscoveryHome?.dailySongs.orEmpty().ifEmpty { dailySongs },
                newSongs = blockDiscoveryHome?.newSongs.orEmpty().ifEmpty { newSongs },
                topArtists = artistResponse.await().array("artists").mapNotNull { it.toArtistItem() },
                newAlbums = blockDiscoveryHome?.newAlbums.orEmpty()
                    .ifEmpty { albumResponse.await().array("albums").mapNotNull { it.toAlbumItem() } },
                topPlaylists = toplistResponse.await().array("list").mapNotNull { it.toPlaylistItem() }.take(6),
            )
        }.also { discoveryHome ->
            discoveryHomeCache = discoveryHome
            discoveryHomeCachedAtMs = System.currentTimeMillis()
        }
    }

    suspend fun fetchPodcastHome(forceRefresh: Boolean = false): PodcastHomeUi {
        if (!forceRefresh) {
            podcastHomeCache?.let { return it }
        }
        return supervisorScope {
            val recommendResponse = async { getNetease("dj/recommend") }
            val toplistResponse = async {
                getNetease(
                    "dj/toplist",
                    mapOf("type" to "hot", "limit" to "12", "offset" to "0"),
                )
            }
            val categoryResponse = async { getNetease("dj/category/recommend") }
            PodcastHomeUi(
                recommendedRadios = recommendResponse.await().array("djRadios").mapNotNull { it.toRadioItem() },
                hotRadios = toplistResponse.await().array("toplist").mapNotNull { it.toRadioItem() },
                categories = categoryResponse.await().array("data").mapNotNull { item ->
                    val category = item.obj
                    val radios = category.array("radios").mapNotNull { it.toRadioItem() }
                    val id = category.long("categoryId")
                    val name = category.string("categoryName")
                    if (id <= 0L || name.isBlank()) return@mapNotNull null
                    RadioCategoryUi(
                        id = id,
                        name = name,
                        radios = radios,
                    )
                },
            )
        }.also { podcastHome ->
            podcastHomeCache = podcastHome
        }
    }

    suspend fun fetchRadioDetail(radioId: Long): RadioDetailUi {
        val detail = getNetease(
            "dj/detail",
            mapOf("rid" to radioId.toString()),
        ).obj("data")
        val programs = getNetease(
            "dj/program",
            mapOf("rid" to radioId.toString(), "limit" to detail.int("programCount").coerceAtLeast(1).toString()),
        ).array("programs").mapNotNull { it.toRadioProgramTrack() }
        return RadioDetailUi(
            id = detail.long("id"),
            name = detail.string("name"),
            coverUrl = detail.string("picUrl"),
            description = detail.string("desc"),
            programCount = detail.int("programCount"),
            programs = programs,
        )
    }

    suspend fun fetchLikedPlaylistId(): Long? {
        val currentUser = fetchLoginState() ?: return null
        val playlists = getNetease(
            "user/playlist",
            mapOf(
                "uid" to currentUser.userId.toString(),
                "limit" to "50",
                "offset" to "0",
                "timestamp" to now(),
            ),
        ).array("playlist").mapNotNull { it.toPlaylistItem() }
        return playlists.resolveLikedPlaylist(currentUser.userId)?.id
    }

    suspend fun fetchHeartRateTracks(
        trackId: Long,
        playlistId: Long,
    ): List<TrackItem> {
        if (trackId <= 0L || playlistId <= 0L) return emptyList()
        return getNetease(
            "playmode/intelligence/list",
            mapOf(
                "id" to trackId.toString(),
                "pid" to playlistId.toString(),
                "timestamp" to now(),
            ),
        ).array("data")
            .mapNotNull { it.toTrackItem() }
            .distinctBy { it.id }
    }

    suspend fun fetchMyMusicHome(recentTracks: List<TrackItem>): MyMusicHomeUi {
        val currentUser = fetchLoginState()
        if (currentUser == null) {
            return MyMusicHomeUi(recentTracks = recentTracks)
        }
        return supervisorScope {
            val profile = async {
                getNetease(
                    "user/detail",
                    mapOf("uid" to currentUser.userId.toString(), "timestamp" to now()),
                )
            }
            val likeResponse = async {
                getNetease("likelist", mapOf("uid" to currentUser.userId.toString()))
            }
            val playlistResponse = async {
                getNetease(
                    "user/playlist",
                    mapOf("uid" to currentUser.userId.toString(), "limit" to "20", "offset" to "0"),
                )
            }
            val albumResponse = async {
                getNetease(
                    "album/sublist",
                    mapOf("limit" to "20", "offset" to "0", "timestamp" to now()),
                )
            }
            val recordResponse = async {
                runCatching {
                    getNetease(
                        "user/record",
                        mapOf(
                            "uid" to currentUser.userId.toString(),
                            "type" to "1",
                            "timestamp" to now(),
                        ),
                    )
                }.getOrNull()
            }
            val profilePayload = profile.await()
            val profileUser = profilePayload.obj("profile").toUserAccount().let { detailUser ->
                if (detailUser == null) {
                    currentUser
                } else {
                    currentUser.copy(
                        nickname = detailUser.nickname.ifBlank { currentUser.nickname },
                        avatarUrl = detailUser.avatarUrl.ifBlank { currentUser.avatarUrl },
                        backgroundUrl = currentUser.backgroundUrl.ifBlank { detailUser.backgroundUrl },
                        signature = detailUser.signature.ifBlank { currentUser.signature },
                        level = profilePayload.int("level"),
                        followCount = profilePayload.obj("profile").int("follows"),
                        followerCount = profilePayload.obj("profile").int("followeds"),
                        listenCount = profilePayload.int("listenSongs"),
                    )
                }
            }
            val playlists = playlistResponse.await().array("playlist").mapNotNull { it.toPlaylistItem() }
            val createdPlaylists = playlists.filter { it.creatorUserId == profileUser.userId }
            val collectedPlaylists = playlists.filter { it.creatorUserId != profileUser.userId }
            val likedPlaylist = playlists.resolveLikedPlaylist(profileUser.userId)
            MyMusicHomeUi(
                currentUser = profileUser,
                likedSongCount = likeResponse.await().array("ids").size,
                likedPlaylist = likedPlaylist,
                recentTracks = recentTracks,
                recentPlaylists = buildRecentPlaylists(
                    likedPlaylist = likedPlaylist,
                    createdPlaylists = createdPlaylists,
                    collectedPlaylists = collectedPlaylists,
                ),
                createdPlaylists = createdPlaylists.take(12),
                collectedPlaylists = collectedPlaylists.take(12),
                albums = albumResponse.await().array("data").mapNotNull { it.toAlbumItem() }.take(12),
                listeningRanks = recordResponse.await().toListeningRanks(),
            )
        }
    }

    suspend fun fetchPlaylistDetail(
        playlistId: Long,
        forceRefresh: Boolean = false,
    ): PlaylistDetailUi {
        if (!forceRefresh) {
            playlistDetailCache[playlistId]
                ?.takeIf { cached -> cached.trackCount == 0 || cached.tracks.size >= min(cached.trackCount, PLAYLIST_PAGE_SIZE) }
                ?.let { return it }
            readCachedPlaylistDetail(playlistId)
                ?.takeIf { cached -> cached.trackCount == 0 || cached.tracks.size >= min(cached.trackCount, PLAYLIST_PAGE_SIZE) }
                ?.let { return it }
        }
        return supervisorScope {
            val previewDeferred = async {
                fetchPlaylistPreviewDetail(
                    playlistId = playlistId,
                    forceRefresh = forceRefresh,
                )
            }
            val firstPageDeferred = async {
                fetchPlaylistTracksPage(
                    playlistId = playlistId,
                    offset = 0,
                    limit = PLAYLIST_PAGE_SIZE,
                    forceRefresh = forceRefresh,
                )
            }

            val preview = previewDeferred.await()
            if (preview.trackCount <= 0) {
                preview
            } else {
                preview.copy(
                    tracks = mergeTrackItems(preview.tracks, firstPageDeferred.await()),
                )
            }
        }.also { detail ->
            playlistDetailCache[playlistId] = detail
            persistPlaylistDetail(detail)
        }
    }

    suspend fun fetchPlaylistTracksPage(
        playlistId: Long,
        offset: Int,
        limit: Int = PLAYLIST_PAGE_SIZE,
        forceRefresh: Boolean = false,
    ): List<TrackItem> {
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(1)
        val cacheKey = playlistPageCacheKey(playlistId, safeOffset, safeLimit)
        if (!forceRefresh) {
            playlistPageCache[cacheKey]?.let { return it }
        }
        return awaitInFlight("playlist-page:$cacheKey") {
            getNetease(
                "playlist/track/all",
                mapOf(
                    "id" to playlistId.toString(),
                    "limit" to safeLimit.toString(),
                    "offset" to safeOffset.toString(),
                    "timestamp" to now(),
                ),
            ).array("songs").mapNotNull { song ->
                song.toTrackItem()
            }.also { page ->
                playlistPageCache[cacheKey] = page
                playlistDetailCache[playlistId]?.let { cached ->
                    val merged = cached.copy(
                        tracks = mergeTrackItems(cached.tracks, page),
                    )
                    playlistDetailCache[playlistId] = merged
                    persistPlaylistDetail(merged)
                }
            }
        }
    }

    suspend fun fetchAlbumDetail(albumId: Long, forceRefresh: Boolean = false): AlbumDetailUi {
        if (!forceRefresh) {
            albumDetailCache[albumId]?.let { return it }
        }
        return supervisorScope {
            val albumResponse = async {
                getNetease(
                    "album",
                    mapOf("id" to albumId.toString(), "timestamp" to now()),
                )
            }
            val dynamicResponse = async {
                getNetease(
                    "album/detail/dynamic",
                    mapOf("id" to albumId.toString(), "timestamp" to now()),
                )
            }
            val albumPayload = albumResponse.await()
            val dynamicPayload = dynamicResponse.await()
            val album = albumPayload.obj("album")
            val artistName = album.obj("artist").string("name")
                .ifBlank { album.array("artists").firstOrNull()?.let { it.obj.string("name") }.orEmpty() }
            val tracks = albumPayload.array("songs").mapNotNull { it.toTrackItem() }
            AlbumDetailUi(
                id = album.long("id"),
                name = album.string("name"),
                coverUrl = album.string("picUrl"),
                artistName = artistName,
                description = album.string("description").ifBlank { album.string("briefDesc") },
                subscribedCount = dynamicPayload.long("subCount"),
                shareCount = dynamicPayload.long("shareCount"),
                commentCount = dynamicPayload.int("commentCount"),
                trackCount = album.int("size").takeIf { it > 0 } ?: tracks.size,
                tracks = tracks,
            )
        }.also { albumDetail ->
            albumDetailCache[albumId] = albumDetail
        }
    }

    suspend fun fetchSearchDefault(forceRefresh: Boolean = false): String {
        if (!forceRefresh) {
            searchDefaultCache?.let { return it }
        }
        return getNetease("search/default", mapOf("timestamp" to now()))
            .obj("data")
            .string("showKeyword")
            .also { defaultHint ->
                if (defaultHint.isNotBlank()) {
                    searchDefaultCache = defaultHint
                }
            }
    }

    suspend fun fetchSearchHot(forceRefresh: Boolean = false): List<String> {
        if (!forceRefresh) {
            searchHotCache?.let { return it }
        }
        return getNetease("search/hot/detail")
            .array("data")
            .map { it.obj.string("searchWord") }
            .filter { it.isNotBlank() }
            .also { hotKeywords ->
                if (hotKeywords.isNotEmpty()) {
                    searchHotCache = hotKeywords
                }
            }
    }

    suspend fun fetchSearchResult(keyword: String): List<TrackItem> {
        val result = getNetease(
            "cloudsearch",
            mapOf(
                "keywords" to keyword,
                "limit" to "50",
                "offset" to "0",
                "type" to "1",
            ),
        ).obj("result")

        return result.array("songs").mapNotNull { element ->
            val song = element.obj
            val artists = song.array("ar").joinToString(" / ") { artist -> artist.obj.string("name") }
            val album = song.obj("al")
            val id = song.long("id")
            if (id <= 0L) return@mapNotNull null
            TrackItem(
                id = id,
                name = song.string("name"),
                artists = artists,
                album = album.string("name"),
                coverUrl = album.string("picUrl"),
                durationMs = song.long("dt"),
                keyword = listOf(song.string("name"), artists).joinToString(" "),
            )
        }
    }

    suspend fun fetchLyrics(
        track: TrackItem,
        forceRefresh: Boolean = false,
    ): List<LyricLineUi> {
        if (!forceRefresh) {
            lyricCache[track.id]?.let { return it }
        }
        return awaitInFlight("lyrics:${track.id}") {
            val response = getNetease("lyric/new", mapOf("id" to track.id.toString()))
            val yrc = response.obj("yrc").string("lyric")
            val ytlrc = response.obj("ytlrc").string("lyric")
            val yromalrc = response.obj("yromalrc").string("lyric")
            val lrc = response.obj("lrc").string("lyric")
            val tlyric = response.obj("tlyric").string("lyric")
            val romalrc = response.obj("romalrc").string("lyric")
            val officialWordLyrics = parseNeteaseYrc(yrc, ytlrc, yromalrc)
            val plainLyrics = parseLyric(lrc, tlyric, romalrc)
            val ttmlLyrics = parseTtmlLyric(track.id)
            if (ttmlLyrics.isNotEmpty()) {
                return@awaitInFlight sanitizeLyricLines(ttmlLyrics).also { lyricCache[track.id] = it }
            }

            val qqLyrics = parseQQMusicLyric(track)
            if (qqLyrics.isNotEmpty()) {
                return@awaitInFlight sanitizeLyricLines(qqLyrics).also { lyricCache[track.id] = it }
            }

            if (officialWordLyrics.isNotEmpty()) {
                return@awaitInFlight sanitizeLyricLines(officialWordLyrics).also { lyricCache[track.id] = it }
            }

            sanitizeLyricLines(plainLyrics).also { lyricCache[track.id] = it }
        }
    }

    suspend fun fetchHotComments(
        songId: Long,
        forceRefresh: Boolean = false,
    ): CommentPageResult {
        if (!forceRefresh) {
            hotCommentCache[songId]?.let { return it }
        }
        return awaitInFlight("comments-hot:$songId") {
            getNetease(
                "comment/hot",
                mapOf(
                    "id" to songId.toString(),
                    "type" to "0",
                    "limit" to "20",
                    "offset" to "0",
                    "timestamp" to now(),
                ),
            ).let { response ->
                CommentPageResult(
                    comments = response.array("hotComments").map { it.toComment() },
                    totalCount = response.int("total"),
                    hasMore = response.boolean("hasMore"),
                )
            }.also { result ->
                hotCommentCache[songId] = result
            }
        }
    }

    suspend fun fetchLatestComments(
        songId: Long,
        pageNo: Int = 1,
        cursor: Long? = null,
        forceRefresh: Boolean = false,
    ): CommentPageResult {
        val cacheKey = latestCommentCacheKey(songId, pageNo, cursor)
        if (!forceRefresh) {
            latestCommentCache[cacheKey]?.let { return it }
        }
        return awaitInFlight("comments-latest:$cacheKey") {
            val params = linkedMapOf(
                "id" to songId.toString(),
                "type" to "0",
                "pageNo" to pageNo.toString(),
                "pageSize" to "20",
                "sortType" to "3",
                "timestamp" to now(),
            ).apply {
                if (pageNo > 1 && cursor != null && cursor > 0L) {
                    put("cursor", cursor.toString())
                }
            }
            getNetease(
                "comment/new",
                params,
            ).obj("data").let { data ->
                CommentPageResult(
                    comments = data.array("comments").map { it.toComment() },
                    totalCount = data.int("totalCount"),
                    hasMore = data.boolean("hasMore"),
                )
            }.also { result ->
                latestCommentCache[cacheKey] = result
            }
        }
    }

    suspend fun resolveSongSource(
        track: TrackItem,
        tryOfficial: Boolean,
        enabledUnlockServers: List<String>,
    ): TrackSource {
        if (tryOfficial) {
            for (level in officialLevels) {
                val official = getNetease(
                    "song/url/v1",
                    mapOf(
                        "id" to track.id.toString(),
                        "level" to level,
                        "timestamp" to now(),
                    ),
                ).array("data").firstOrNull()?.obj

                val officialUrl = normalizeUrl(official?.string("url").orEmpty())
                if (officialUrl.isNotBlank()) {
                    return TrackSource(
                        url = officialUrl,
                        quality = official?.string("level").orEmpty().ifBlank { level },
                        source = "official",
                        unlocked = false,
                    )
                }
            }
        }

        for (server in enabledUnlockServers) {
            val result = if (server == NATIVE_NETEASE_UNLOCK_SOURCE) {
                getDirectNeteaseUnlock(track.id)
            } else {
                getUnblock(
                    server = server,
                    params = if (server == "netease") {
                        mapOf("id" to track.id.toString(), "noCookie" to "true")
                    } else {
                        mapOf(
                            "keyword" to track.keyword,
                            "songName" to track.name,
                            "artist" to track.artists,
                            "noCookie" to "true",
                        )
                    },
                )
            }
            val url = normalizeUrl(result.string("url"))
            if (url.isNotBlank()) {
                return TrackSource(
                    url = url,
                    quality = result.string("br"),
                    source = server,
                    unlocked = true,
                )
            }
        }

        error("AUDIO_SOURCE_EMPTY")
    }

    private suspend fun getDirectNeteaseUnlock(trackId: Long): JsonObject {
        val body = getRaw(
            DIRECT_NETEASE_UNLOCK_BASE_URL,
            mapOf(
                "types" to "url",
                "id" to trackId.toString(),
                "noCookie" to "true",
            ),
        )
        return parseJsonObjectBody(body)
    }

    private suspend fun getNetease(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val body = getRaw("netease/$path", params)
        return parseJsonObjectBody(body)
    }

    private suspend fun requestPlaylistPayload(playlistId: Long): JsonObject {
        return getNetease(
            "playlist/detail",
            mapOf(
                "id" to playlistId.toString(),
                "s" to "0",
                "noCookie" to "true",
                "timestamp" to now(),
            ),
        ).obj("playlist")
    }

    private suspend fun getUnblock(server: String, params: Map<String, String>): JsonObject {
        val body = getRaw("unblock/$server", params)
        return parseJsonObjectBody(body)
    }

    private suspend fun getQQMusic(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val body = getRaw("qqmusic/$path", params)
        return parseJsonObjectBody(body)
    }

    private fun normalizeUrl(url: String): String {
        if (url.isBlank()) return ""
        return url.replace("http://", "https://")
    }

    private suspend fun getRaw(path: String, params: Map<String, String> = emptyMap()): String {
        return api.get(path, params).string()
    }

    private fun parseJsonObjectBody(rawBody: String): JsonObject {
        return json.parseToJsonElement(extractFirstJsonEnvelope(rawBody)).jsonObject
    }

    private fun buildPlaylistDetail(
        playlistId: Long,
        playlist: JsonObject,
        tracks: List<TrackItem>,
    ): PlaylistDetailUi {
        val trackCount = max(playlist.int("trackCount"), 0)
        return PlaylistDetailUi(
            id = playlist.long("id").takeIf { it > 0L } ?: playlistId,
            name = playlist.string("name"),
            coverUrl = playlist.string("coverImgUrl"),
            description = playlist.string("description"),
            playCount = playlist.long("playCount"),
            subscribedCount = playlist.long("subscribedCount"),
            trackCount = trackCount,
            tracks = tracks.take(trackCount.takeIf { it > 0 } ?: tracks.size),
        )
    }

    private suspend fun persistPlaylistDetail(detail: PlaylistDetailUi) {
        playlistDetailCacheDao?.upsert(detail.toCacheEntity(json))
    }

    private fun playlistPageCacheKey(
        playlistId: Long,
        offset: Int,
        limit: Int,
    ): String = "$playlistId:$offset:$limit"

    private fun latestCommentCacheKey(songId: Long, pageNo: Int, cursor: Long? = null): String {
        return if (cursor == null || cursor <= 0L) {
            "$songId:$pageNo"
        } else {
            "$songId:$pageNo:$cursor"
        }
    }

    private suspend fun <T> awaitInFlight(key: String, block: suspend () -> T): T {
        val waiter = synchronized(inFlightLock) {
            @Suppress("UNCHECKED_CAST")
            inFlightRequests[key] as CompletableDeferred<T>?
        }
        if (waiter != null) {
            return waiter.await()
        }

        val deferred = CompletableDeferred<Any?>()
        val active = synchronized(inFlightLock) {
            inFlightRequests.putIfAbsent(key, deferred) ?: deferred
        }
        if (active !== deferred) {
            @Suppress("UNCHECKED_CAST")
            return (active as CompletableDeferred<T>).await()
        }

        try {
            val result = block()
            deferred.complete(result)
            return result
        } catch (error: Throwable) {
            deferred.completeExceptionally(error)
            throw error
        } finally {
            synchronized(inFlightLock) {
                if (inFlightRequests[key] === deferred) {
                    inFlightRequests.remove(key)
                }
            }
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

    private fun JsonElement.toComment(): CommentItem {
        val root = obj
        val user = root.obj("user")
        return CommentItem(
            id = root.long("commentId"),
            userName = user.string("nickname"),
            userAvatar = user.string("avatarUrl"),
            content = root.string("content"),
            likedCount = root.int("likedCount"),
            time = root.long("time"),
        )
    }

    private fun JsonElement.toTrackItem(): TrackItem? {
        return obj.toTrackItemFromSong()
    }

    private fun parseLyric(
        primaryLyric: String,
        translationLyric: String,
        romanizedLyric: String,
    ): List<LyricLineUi> {
        if (primaryLyric.isBlank()) return emptyList()
        val translations = parsePlainLrcLines(translationLyric)
        val romanized = parsePlainLrcLines(romanizedLyric)
        return parsePlainLrcLines(primaryLyric).map { line ->
            line.copy(
                translation = findAlignedSupplementLyricText(line.startTimeMs, translations).ifBlank { line.translation },
                romanized = findAlignedSupplementLyricText(line.startTimeMs, romanized).ifBlank { line.romanized },
            )
        }
    }

    private fun parseNeteaseYrc(
        primaryLyric: String,
        translationLyric: String = "",
        romanizedLyric: String = "",
    ): List<LyricLineUi> {
        return parseNeteaseYrcLines(
            primaryLyric = primaryLyric,
            translationLyric = translationLyric,
            romanizedLyric = romanizedLyric,
        )
    }

    private fun parsePlainLrc(content: String): List<LyricLineUi> {
        return parsePlainLrcLines(content)
    }

    private suspend fun parseTtmlLyric(songId: Long): List<LyricLineUi> {
        val body = runCatching {
            getRaw(
                "netease/lyric/ttml",
                mapOf("id" to songId.toString(), "timestamp" to now()),
            )
        }.getOrNull().orEmpty()
        if (body.isBlank()) return emptyList()
        return parseTtmlLyricBody(body)
    }

    private suspend fun parseQQMusicLyric(track: TrackItem): List<LyricLineUi> {
        val response = runCatching {
            getQQMusic(
                "match",
                mapOf(
                    "keyword" to track.keyword.ifBlank { "${track.name}-${track.artists}" },
                ),
            )
        }.getOrNull() ?: return emptyList()
        val responseCode = response.int("code")
        if (responseCode != 0 && responseCode != 200) return emptyList()
        if (!isMatchedLyricDurationCompatible(track.durationMs, response.obj("song").long("duration"))) {
            return emptyList()
        }
        val qrcLyrics = parseTimedLyric(
            primaryLyric = extractQrcContent(response.string("qrc")),
            translationLyric = response.string("trans"),
            romanizedLyric = response.string("roma"),
        )
        if (qrcLyrics.isNotEmpty()) return qrcLyrics
        return parseLyric(
            primaryLyric = response.string("lrc"),
            translationLyric = response.string("trans"),
            romanizedLyric = response.string("roma"),
        )
    }

    private fun parseTimedLyric(
        primaryLyric: String,
        translationLyric: String = "",
        romanizedLyric: String = "",
    ): List<LyricLineUi> {
        return parseTimedLyricLines(
            primaryLyric = primaryLyric,
            translationLyric = translationLyric,
            romanizedLyric = romanizedLyric,
        )
    }

    private fun extractQrcContent(rawContent: String): String {
        if (rawContent.isBlank()) return ""
        return Regex("""LyricContent="([^"]+)"""")
            .find(rawContent)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("&quot;", "\"")
            ?.replace("&#10;", "\n")
            ?.replace("\\n", "\n")
            ?: rawContent
    }

    private fun now(): String = System.currentTimeMillis().toString()
}

private data class PlainLrcEvent(
    val startTimeMs: Long,
    val text: String,
    val order: Int,
)

private val plainLrcTimeTagPattern = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")
private val yrcLinePattern = Regex("""^\[(\d+),(\d+)](.*)$""")
private val yrcWordPattern = Regex("""\((\d+),(\d+),(\d+)\)([^()]*)""")
private val timedLyricLinePattern = Regex("""\[(\d+),(\d+)](.*)$""")
private val timedLyricWordPattern = Regex("""(.*?)\((\d+),(\d+)\)""")
private val ttmlLinePattern = Regex("""<p\b([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
private val ttmlSpanPattern = Regex("""<span\b([^>]*)>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
private val ttmlTranslationPattern = Regex("""<span\b[^>]*ttm:role="(?:x-translation|x-bg)"[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
private val ttmlRomanizedPattern = Regex("""<span\b[^>]*ttm:role="x-roman"[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
private val ttmlAnyAttributePattern = Regex("""([A-Za-z:]+)="([^"]*)"""")
private const val LYRIC_ALIGN_TOLERANCE_MS = 300L
private const val LYRIC_MATCH_DURATION_TOLERANCE_MS = 5_000L

internal fun findAlignedSupplementLyricText(
    startTimeMs: Long,
    supplementLines: List<LyricLineUi>,
    toleranceMs: Long = LYRIC_ALIGN_TOLERANCE_MS,
): String {
    if (supplementLines.isEmpty()) return ""
    val nearest = supplementLines.minByOrNull { line -> abs(line.startTimeMs - startTimeMs) } ?: return ""
    return nearest
        .takeIf { line -> abs(line.startTimeMs - startTimeMs) <= toleranceMs }
        ?.mainText
        .orEmpty()
}

internal fun isMatchedLyricDurationCompatible(
    trackDurationMs: Long,
    matchedDurationMs: Long,
    toleranceMs: Long = LYRIC_MATCH_DURATION_TOLERANCE_MS,
): Boolean {
    if (trackDurationMs <= 0L || matchedDurationMs <= 0L) return true
    return abs(trackDurationMs - matchedDurationMs) <= toleranceMs
}

internal fun resolveQrcWordStartTimeMs(
    lineStartTimeMs: Long,
    rawWordStartTimeMs: Long,
): Long {
    return if (rawWordStartTimeMs >= lineStartTimeMs) {
        rawWordStartTimeMs
    } else {
        lineStartTimeMs + rawWordStartTimeMs
    }
}

internal fun parsePlainLrcLines(content: String): List<LyricLineUi> {
    val rawEvents = buildList {
        var order = 0
        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            val matches = plainLrcTimeTagPattern.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val mainText = line.replace(plainLrcTimeTagPattern, "").trim()
            if (mainText.isBlank()) return@forEach
            matches.forEach { match ->
                val minute = match.groupValues[1].toLongOrNull() ?: return@forEach
                val second = match.groupValues[2].toLongOrNull() ?: return@forEach
                val fraction = match.groupValues.getOrNull(3).orEmpty().padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                add(
                    PlainLrcEvent(
                        startTimeMs = minute * 60_000 + second * 1_000 + fraction,
                        text = mainText,
                        order = order,
                    ),
                )
                order += 1
            }
        }
    }.sortedWith(compareBy<PlainLrcEvent> { it.startTimeMs }.thenBy { it.order })

    if (rawEvents.isEmpty()) return emptyList()

    val grouped = rawEvents.groupBy { it.startTimeMs }.entries.toList()
    return grouped.flatMapIndexed { index, group ->
        val startTimeMs = group.key
        val nextStart = grouped.getOrNull(index + 1)?.key ?: (startTimeMs + 10_000L)
        val endTimeMs = nextStart.coerceAtLeast(startTimeMs)
        val textEvents = group.value.sortedBy { it.order }.filter { it.text.isNotBlank() }
        if (textEvents.isEmpty()) {
            emptyList()
        } else {
            val base = LyricLineUi(
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                mainText = textEvents[0].text,
                translation = textEvents.getOrNull(1)?.text.orEmpty(),
                romanized = textEvents.getOrNull(2)?.text.orEmpty(),
                words = listOf(
                    LyricWordUi(
                        text = textEvents[0].text,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                    ),
                ),
                hasWordTiming = false,
            )
            buildList {
                add(base)
                textEvents.drop(3).forEach { event ->
                    add(
                        LyricLineUi(
                            startTimeMs = startTimeMs,
                            endTimeMs = endTimeMs,
                            mainText = event.text,
                            words = listOf(
                                LyricWordUi(
                                    text = event.text,
                                    startTimeMs = startTimeMs,
                                    endTimeMs = endTimeMs,
                                ),
                            ),
                            hasWordTiming = false,
                        ),
                    )
                }
            }
        }
    }
}

internal fun parseNeteaseYrcLines(
    primaryLyric: String,
    translationLyric: String = "",
    romanizedLyric: String = "",
): List<LyricLineUi> {
    if (primaryLyric.isBlank()) return emptyList()
    val translations = parsePlainLrcLines(translationLyric)
    val romanized = parsePlainLrcLines(romanizedLyric)
    return primaryLyric.lineSequence()
        .mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("{")) return@mapNotNull null
            val match = yrcLinePattern.find(line) ?: return@mapNotNull null
            val startTimeMs = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val lineDurationMs = match.groupValues[2].toLongOrNull() ?: 0L
            val content = match.groupValues[3]
            val words = yrcWordPattern.findAll(content)
                .mapNotNull { wordMatch ->
                    val wordStartMs = wordMatch.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                    val durationMs = wordMatch.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                    val text = wordMatch.groupValues[4]
                    if (text.isBlank()) return@mapNotNull null
                    LyricWordUi(
                        text = text,
                        startTimeMs = wordStartMs,
                        endTimeMs = wordStartMs + durationMs,
                    )
                }
                .toList()
            val parsedText = words.joinToString(separator = "") { word -> word.text }
                .trim()
                .ifBlank {
                    content.replace(Regex("""\(\d+,\d+,\d+\)"""), "").trim()
                }
            if (parsedText.isBlank()) return@mapNotNull null
            val lastWordEndTimeMs = words.maxOfOrNull { word -> word.endTimeMs } ?: startTimeMs
            LyricLineUi(
                startTimeMs = startTimeMs,
                endTimeMs = maxOf(startTimeMs + lineDurationMs, lastWordEndTimeMs, startTimeMs),
                mainText = parsedText,
                translation = findAlignedSupplementLyricText(startTimeMs, translations),
                romanized = findAlignedSupplementLyricText(startTimeMs, romanized),
                words = words,
                hasWordTiming = true,
            )
        }
        .sortedBy { it.startTimeMs }
        .toList()
}

internal fun parseTimedLyricLines(
    primaryLyric: String,
    translationLyric: String = "",
    romanizedLyric: String = "",
): List<LyricLineUi> {
    if (primaryLyric.isBlank()) return emptyList()
    val translations = parsePlainLrcLines(translationLyric)
    val romanized = parsePlainLrcLines(romanizedLyric)
    return primaryLyric.lineSequence()
        .mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@mapNotNull null
            val match = timedLyricLinePattern.find(line) ?: return@mapNotNull null
            val startTimeMs = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val lineDurationMs = match.groupValues[2].toLongOrNull() ?: 0L
            val content = match.groupValues[3]
            val words = timedLyricWordPattern.findAll(content)
                .mapNotNull { wordMatch ->
                    val text = wordMatch.groupValues[1]
                    val offsetMs = wordMatch.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                    val durationMs = wordMatch.groupValues[3].toLongOrNull() ?: return@mapNotNull null
                    if (text.isBlank()) return@mapNotNull null
                    val wordStartTimeMs = resolveQrcWordStartTimeMs(startTimeMs, offsetMs)
                    LyricWordUi(
                        text = text,
                        startTimeMs = wordStartTimeMs,
                        endTimeMs = wordStartTimeMs + durationMs,
                    )
                }
                .toList()
            val text = words.joinToString(separator = "") { word -> word.text }
                .trim()
                .ifBlank { content.trim() }
            if (text.isBlank()) return@mapNotNull null
            val lastWordEndTimeMs = words.maxOfOrNull { word -> word.endTimeMs } ?: startTimeMs
            LyricLineUi(
                startTimeMs = startTimeMs,
                endTimeMs = maxOf(startTimeMs + lineDurationMs, lastWordEndTimeMs, startTimeMs),
                mainText = text,
                translation = findAlignedSupplementLyricText(startTimeMs, translations),
                romanized = findAlignedSupplementLyricText(startTimeMs, romanized),
                words = words,
                hasWordTiming = true,
            )
        }
        .sortedBy { it.startTimeMs }
        .toList()
}

internal fun parseTtmlLyricBody(body: String): List<LyricLineUi> {
    if (body.isBlank()) return emptyList()
    return ttmlLinePattern.findAll(body).mapNotNull { match ->
        val attrs = extractXmlAttributes(match.groupValues[1])
        val content = match.groupValues[2]
        val spans = ttmlSpanPattern.findAll(content).toList()
        val words = spans.mapNotNull { span ->
            val spanAttrs = extractXmlAttributes(span.groupValues[1])
            val role = spanAttrs["ttm:role"].orEmpty()
            if (role == "x-translation" || role == "x-roman" || role == "x-bg") return@mapNotNull null
            val startTime = parseTtmlTimeValue(spanAttrs["begin"]) ?: return@mapNotNull null
            val endTime = parseTtmlTimeValue(spanAttrs["end"])?.coerceAtLeast(startTime) ?: startTime
            val text = stripXmlText(span.groupValues[2]).trim()
            if (text.isBlank()) return@mapNotNull null
            LyricWordUi(
                text = text,
                startTimeMs = startTime,
                endTimeMs = endTime,
            )
        }
        val mainText = words.joinToString(separator = "") { it.text }
            .ifBlank {
                stripXmlText(
                    content.replace(ttmlTranslationPattern, "").replace(ttmlRomanizedPattern, ""),
                ).trim()
            }
        if (mainText.isBlank()) return@mapNotNull null
        val startTimeMs = parseTtmlTimeValue(attrs["begin"])
            ?: words.firstOrNull()?.startTimeMs
            ?: return@mapNotNull null
        val explicitEndTimeMs = parseTtmlTimeValue(attrs["end"])
        val lastWordEndTimeMs = words.maxOfOrNull { word -> word.endTimeMs }
        LyricLineUi(
            startTimeMs = startTimeMs,
            endTimeMs = maxOf(explicitEndTimeMs ?: startTimeMs, lastWordEndTimeMs ?: startTimeMs, startTimeMs),
            mainText = mainText,
            translation = ttmlTranslationPattern.find(content)?.groupValues?.getOrNull(1)?.let(::stripXmlText).orEmpty(),
            romanized = ttmlRomanizedPattern.find(content)?.groupValues?.getOrNull(1)?.let(::stripXmlText).orEmpty(),
            words = words,
            hasWordTiming = true,
        )
    }.sortedBy { it.startTimeMs }.toList()
}

private fun extractXmlAttributes(raw: String): Map<String, String> {
    return ttmlAnyAttributePattern.findAll(raw).associate { match ->
        match.groupValues[1] to match.groupValues[2]
    }
}

private fun parseTtmlTimeValue(rawValue: String?): Long? {
    val value = rawValue?.trim().orEmpty()
    if (value.isBlank()) return null
    val match = Regex("""(?:(\d+):)?(\d+):(\d+)\.(\d+)""").matchEntire(value) ?: return null
    val hours = match.groupValues.getOrNull(1).orEmpty().toLongOrNull() ?: 0L
    val minutes = match.groupValues[2].toLongOrNull() ?: 0L
    val seconds = match.groupValues[3].toLongOrNull() ?: 0L
    val millis = match.groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis
}

private fun stripXmlText(text: String): String {
    return text.replace(Regex("""<[^>]+>"""), "").replace(Regex("""\s+"""), " ").trim()
}

internal fun extractFirstJsonEnvelope(rawBody: String): String {
    val body = rawBody.trim()
    if (body.isBlank()) return body
    val opening = body.first()
    if (opening != '{' && opening != '[') return body

    var objectDepth = 0
    var arrayDepth = 0
    var inString = false
    var escapeNext = false

    body.forEachIndexed { index, char ->
        when {
            escapeNext -> escapeNext = false
            char == '\\' && inString -> escapeNext = true
            char == '"' -> inString = !inString
            inString -> Unit
            char == '{' -> objectDepth += 1
            char == '}' -> {
                objectDepth -= 1
                if (opening == '{' && objectDepth == 0 && arrayDepth == 0) {
                    return body.substring(0, index + 1)
                }
            }
            char == '[' -> arrayDepth += 1
            char == ']' -> {
                arrayDepth -= 1
                if (opening == '[' && arrayDepth == 0 && objectDepth == 0) {
                    return body.substring(0, index + 1)
                }
            }
        }
    }
    return body
}

private val lyricMetadataKeywords = listOf(
    "作词",
    "作曲",
    "编曲",
    "和声",
    "混音",
    "母带",
    "监制",
    "出品",
    "发行",
    "录音",
    "歌词提供",
    "翻译提供",
    "曲名",
    "歌名",
    "歌手",
    "演唱",
    "原唱",
    "来源",
    "来自",
    "lyrics",
    "lyrics by",
    "lyricist",
    "composer",
    "arranger",
    "mixing",
    "mastering",
    "lyrics provider",
    "lyric provider",
    "translation provider",
    "cloud drive",
)

private val lyricMetadataSeparators = setOf(':', '：', '-', ' ', '\t', '/', '|', '(', '（', '[')
private val lyricTitleArtistPattern = Regex("""^[\p{L}\p{N} .·'’&]+-[\p{L}\p{N} .·'’&]+[（(].+[）)]$""")

internal fun normalizeLyricLineText(text: String): String {
    return text.replace(Regex("""\s+"""), " ").trim()
}

private fun looksLikeDecorativeLyricNoise(text: String): Boolean {
    val normalized = normalizeLyricLineText(text)
    if (normalized == "//") return true
    if (normalized == "**") return true
    return normalized.startsWith("**")
}

private fun cleanSupplementLyricText(text: String): String {
    val normalized = normalizeLyricLineText(text)
    return if (looksLikeDecorativeLyricNoise(normalized) || looksLikeLyricMetadataLine(normalized)) {
        ""
    } else {
        normalized
    }
}

internal fun looksLikeLyricMetadataLine(text: String): Boolean {
    val normalized = normalizeLyricLineText(text)
    if (normalized.isBlank()) return false
    val lowered = normalized.lowercase()
    if (looksLikeDecorativeLyricNoise(normalized)) return true
    if (normalized.contains("著作权") || normalized.contains("版权所有")) return true
    if (lyricTitleArtistPattern.matches(normalized)) return true
    if (lowered.contains("cloud drive")) return true
    if (lowered.contains("lyrics provider") || lowered.contains("translation provider")) return true
    return lyricMetadataKeywords.any { keyword ->
        val loweredKeyword = keyword.lowercase()
        lowered == loweredKeyword ||
            (
                lowered.startsWith(loweredKeyword) &&
                    lowered
                        .getOrNull(loweredKeyword.length)
                        ?.let { separator -> separator in lyricMetadataSeparators }
                        ?: true
            )
    }
}

internal fun stripLeadingAndTrailingLyricMetadata(
    lines: List<LyricLineUi>,
    scanLimit: Int = 12,
): List<LyricLineUi> {
    if (lines.isEmpty()) return emptyList()
    val cleanedLines = lines.mapNotNull { line ->
        val mainText = normalizeLyricLineText(line.mainText)
        if (mainText.isBlank()) {
            null
        } else {
            line.copy(
                mainText = mainText,
                translation = cleanSupplementLyricText(line.translation),
                romanized = cleanSupplementLyricText(line.romanized),
            )
        }
    }
    if (cleanedLines.isEmpty()) return emptyList()

    var startIndex = 0
    val headBoundary = minOf(scanLimit, cleanedLines.size)
    while (startIndex < headBoundary && looksLikeLyricMetadataLine(cleanedLines[startIndex].mainText)) {
        startIndex += 1
    }

    var endIndex = cleanedLines.size
    val tailBoundary = maxOf(startIndex, cleanedLines.size - scanLimit)
    while (endIndex > tailBoundary && looksLikeLyricMetadataLine(cleanedLines[endIndex - 1].mainText)) {
        endIndex -= 1
    }

    val croppedLines = cleanedLines.subList(startIndex, endIndex)
    val filteredLines = croppedLines.filterNot { line ->
        looksLikeLyricMetadataLine(line.mainText)
    }
    val effectiveLines = filteredLines.ifEmpty { croppedLines }
    return effectiveLines
        .distinctBy { line ->
            Triple(line.startTimeMs, line.mainText, "${line.translation}|${line.romanized}")
        }
}

private fun sanitizeLyricLines(lines: List<LyricLineUi>): List<LyricLineUi> {
    return stripLeadingAndTrailingLyricMetadata(lines)
}

private fun JsonObject.toTrackItemFromSong(): TrackItem? {
    val song = obj("simpleSong").takeIf { it.isNotEmpty() }
        ?: obj("songInfo").takeIf { it.isNotEmpty() }
        ?: this
    val id = song.long("id")
    if (id <= 0L) return null
    val album = song.obj("al").takeIf { it.isNotEmpty() }
        ?: song.obj("album").takeIf { it.isNotEmpty() }
        ?: JsonObject(emptyMap())
    val artists = song.array("ar").joinToString(" / ") { artist -> artist.obj.string("name") }
        .ifBlank { song.array("artists").joinToString(" / ") { artist -> artist.obj.string("name") } }
    val duration = song.long("dt").takeIf { it > 0L } ?: song.long("duration")
    return TrackItem(
        id = id,
        name = song.string("name"),
        artists = artists,
        album = album.string("name"),
        coverUrl = album.string("picUrl"),
        durationMs = duration,
        keyword = listOf(song.string("name"), artists).filter { it.isNotBlank() }.joinToString(" "),
    )
}

private fun JsonObject.toDiscoveryHomeUi(): DiscoveryHomeUi {
    val resources = obj("data")
        .array("blocks")
        .flatMap { block -> block.obj.array("creatives") }
        .flatMap { creative -> creative.obj.array("resources") }
    val playlists = resources.mapNotNull { resource -> resource.toHomepagePlaylistItem() }
    val songs = resources.mapNotNull { resource -> resource.toHomepageTrackItem() }
    val albums = resources.mapNotNull { resource -> resource.toHomepageAlbumItem() }
    return DiscoveryHomeUi(
        recommendedPlaylists = playlists.distinctBy { playlist -> playlist.id }.take(12),
        dailySongs = songs.distinctBy { track -> track.id }.take(12),
        newSongs = songs.distinctBy { track -> track.id }.take(24),
        newAlbums = albums.distinctBy { album -> album.id }.take(12),
    )
}

private fun JsonElement.toHomepagePlaylistItem(): PlaylistItem? {
    val resource = obj
    val type = resource.string("resourceType").lowercase()
    if (type.isNotBlank() && !type.contains("playlist")) return null
    val uiElement = resource.obj("uiElement")
    val id = resource.long("resourceId").takeIf { it > 0L } ?: resource.long("id")
    val name = resource.string("name")
        .ifBlank { uiElement.obj("mainTitle").string("title") }
        .ifBlank { uiElement.string("mainTitle") }
    if (id <= 0L || name.isBlank()) return null
    val ext = resource.obj("resourceExtInfo")
    return PlaylistItem(
        id = id,
        name = name,
        coverUrl = resource.string("picUrl")
            .ifBlank { resource.string("coverImgUrl") }
            .ifBlank { uiElement.obj("image").string("imageUrl") },
        trackCount = resource.int("trackCount").takeIf { it > 0 }
            ?: resource.int("songCount").takeIf { it > 0 }
            ?: ext.int("songCount").takeIf { it > 0 }
            ?: ext.int("trackCount"),
    )
}

private fun JsonElement.toHomepageTrackItem(): TrackItem? {
    val resource = obj
    val type = resource.string("resourceType").lowercase()
    if (type.isNotBlank() && !type.contains("song")) return null
    val ext = resource.obj("resourceExtInfo")
    val song = ext.obj("songData").takeIf { it.isNotEmpty() }
        ?: ext.obj("song").takeIf { it.isNotEmpty() }
        ?: resource.obj("songData").takeIf { it.isNotEmpty() }
        ?: resource.obj("songInfo").takeIf { it.isNotEmpty() }
        ?: resource
    return song.toTrackItemFromSong()
}

private fun JsonElement.toHomepageAlbumItem(): AlbumItem? {
    val resource = obj
    val type = resource.string("resourceType").lowercase()
    if (type.isNotBlank() && !type.contains("album")) return null
    val uiElement = resource.obj("uiElement")
    val ext = resource.obj("resourceExtInfo")
    val id = resource.long("resourceId").takeIf { it > 0L } ?: resource.long("id")
    val name = resource.string("name")
        .ifBlank { uiElement.obj("mainTitle").string("title") }
        .ifBlank { uiElement.string("mainTitle") }
    if (id <= 0L || name.isBlank()) return null
    val artistName = resource.obj("artist").string("name")
        .ifBlank { uiElement.obj("subTitle").string("title") }
        .ifBlank { uiElement.string("subTitle") }
    return AlbumItem(
        id = id,
        name = name,
        coverUrl = resource.string("picUrl").ifBlank { uiElement.obj("image").string("imageUrl") },
        artistName = artistName,
        trackCount = resource.int("size").takeIf { it > 0 } ?: ext.int("songCount"),
    )
}

private fun JsonElement.toPlaylistItem(): PlaylistItem? {
    val item = obj
    val id = item.long("id")
    val name = item.string("name")
    if (id <= 0L || name.isBlank()) return null
    return PlaylistItem(
        id = id,
        name = name,
        coverUrl = item.string("picUrl").ifBlank { item.string("coverImgUrl") },
        trackCount = item.int("trackCount").takeIf { it > 0 } ?: item.int("programCount"),
        creatorUserId = item.obj("creator").long("userId").takeIf { it > 0L } ?: item.long("userId"),
    )
}

private fun List<PlaylistItem>.resolveLikedPlaylist(currentUserId: Long): PlaylistItem? {
    if (isEmpty()) return null
    return firstOrNull { playlist ->
        playlist.creatorUserId == currentUserId &&
            (playlist.name == "我喜欢的音乐" || playlist.name.contains("喜欢的音乐"))
    } ?: firstOrNull { playlist ->
        playlist.creatorUserId == currentUserId
    } ?: firstOrNull()
}

private fun buildRecentPlaylists(
    likedPlaylist: PlaylistItem?,
    createdPlaylists: List<PlaylistItem>,
    collectedPlaylists: List<PlaylistItem>,
): List<PlaylistItem> {
    return buildList {
        likedPlaylist?.let(::add)
        addAll(createdPlaylists)
        addAll(collectedPlaylists)
    }.distinctBy { playlist -> playlist.id }.take(8)
}

private fun JsonObject?.toListeningRanks(): List<ListeningRankItem> {
    val payload = this ?: return emptyList()
    val candidates = payload.array("weekData").ifEmpty { payload.array("allData") }
    return candidates.mapNotNull { entry ->
        val item = entry.obj
        val song = item.obj("song")
        val trackId = song.long("id")
        val trackName = song.string("name")
        if (trackId <= 0L || trackName.isBlank()) return@mapNotNull null
        val album = song.obj("al").takeIf { it.isNotEmpty() }
            ?: song.obj("album").takeIf { it.isNotEmpty() }
            ?: JsonObject(emptyMap())
        val artists = song.array("ar").joinToString(" / ") { artist -> artist.obj.string("name") }
            .ifBlank { song.array("artists").joinToString(" / ") { artist -> artist.obj.string("name") } }
        val track = TrackItem(
            id = trackId,
            name = trackName,
            artists = artists,
            album = album.string("name"),
            coverUrl = album.string("picUrl"),
            durationMs = song.long("dt").takeIf { it > 0L } ?: song.long("duration"),
            keyword = listOf(trackName, artists).filter { it.isNotBlank() }.joinToString(" "),
        )
        ListeningRankItem(
            track = track,
            playCount = item.int("playCount").coerceAtLeast(0),
        )
    }.distinctBy { rank -> rank.track.id }.take(10)
}

private fun JsonElement.toAlbumItem(): AlbumItem? {
    val item = obj
    val id = item.long("id")
    val name = item.string("name")
    if (id <= 0L || name.isBlank()) return null
    val artistName = item.obj("artist").string("name")
        .ifBlank { item.array("artists").firstOrNull()?.let { it.obj.string("name") }.orEmpty() }
    return AlbumItem(
        id = id,
        name = name,
        coverUrl = item.string("picUrl"),
        artistName = artistName,
        trackCount = item.int("size"),
    )
}

private fun JsonElement.toArtistItem(): ArtistItem? {
    val item = obj
    val id = item.long("id")
    val name = item.string("name")
    if (id <= 0L || name.isBlank()) return null
    return ArtistItem(
        id = id,
        name = name,
        coverUrl = item.string("img1v1Url").ifBlank { item.string("picUrl") },
        musicSize = item.int("musicSize"),
    )
}

private fun JsonElement.toRadioItem(): RadioItem? {
    val item = obj
    val id = item.long("id")
    val name = item.string("name")
    if (id <= 0L || name.isBlank()) return null
    return RadioItem(
        id = id,
        name = name,
        coverUrl = item.string("picUrl").ifBlank { item.string("coverUrl") },
        programCount = item.int("programCount"),
        description = item.string("desc").ifBlank { item.string("rcmdtext") },
    )
}

private fun JsonElement.toRadioProgramTrack(): TrackItem? {
    val item = obj
    val mainSong = item.obj("mainSong")
    val id = item.long("id").takeIf { it > 0L } ?: mainSong.long("id")
    if (id <= 0L) return null
    val album = mainSong.obj("album")
    val artists = mainSong.array("artists").joinToString(" / ") { artist -> artist.obj.string("name") }
        .ifBlank { item.obj("dj").string("nickname") }
    return TrackItem(
        id = id,
        name = mainSong.string("name").ifBlank { item.string("name") },
        artists = artists.ifBlank { "播客节目" },
        album = album.string("name").ifBlank { item.obj("dj").string("brand") },
        coverUrl = item.string("coverUrl").ifBlank { album.string("picUrl") },
        durationMs = mainSong.long("duration").takeIf { it > 0L } ?: item.long("duration"),
        keyword = listOf(
            mainSong.string("name").ifBlank { item.string("name") },
            artists,
        ).joinToString(" "),
    )
}

private fun JsonObject.toUserAccount(profile: JsonObject = this): UserAccountUi? {
    val userId = long("id").takeIf { it > 0L } ?: long("userId").takeIf { it > 0L } ?: profile.long("id")
        .takeIf { it > 0L } ?: profile.long("userId").takeIf { it > 0L } ?: return null
    return UserAccountUi(
        userId = userId,
        nickname = profile.string("nickname").ifBlank { string("nickname") },
        avatarUrl = profile.string("avatarUrl").ifBlank { string("avatarUrl") },
        backgroundUrl = profile.string("backgroundUrl")
            .ifBlank { profile.string("backgroundImageUrl") }
            .ifBlank { profile.string("profileBackgroundUrl") }
            .ifBlank { string("backgroundUrl") }
            .ifBlank { string("backgroundImageUrl") }
            .ifBlank { string("profileBackgroundUrl") },
        signature = profile.string("signature").ifBlank { string("signature") },
        level = int("level").takeIf { it > 0 } ?: profile.int("level"),
        followCount = profile.int("follows"),
        followerCount = profile.int("followeds"),
        listenCount = int("listenSongs").takeIf { it > 0 } ?: profile.int("listenSongs"),
    )
}

private fun PlaylistDetailUi.toCacheEntity(json: Json): PlaylistDetailCacheEntity {
    return PlaylistDetailCacheEntity(
        playlistId = id,
        name = name,
        coverUrl = coverUrl,
        description = description,
        playCount = playCount,
        subscribedCount = subscribedCount,
        trackCount = trackCount,
        tracksJson = json.encodeToString(tracks),
        cachedAt = System.currentTimeMillis(),
    )
}

private fun PlaylistDetailCacheEntity.toPlaylistDetailUi(json: Json): PlaylistDetailUi? {
    return runCatching {
        PlaylistDetailUi(
            id = playlistId,
            name = name,
            coverUrl = coverUrl,
            description = description,
            playCount = playCount,
            subscribedCount = subscribedCount,
            trackCount = trackCount,
            tracks = json.decodeFromString<List<TrackItem>>(tracksJson),
        )
    }.getOrNull()
}

private val JsonElement.obj: JsonObject
    get() = this as? JsonObject ?: JsonObject(emptyMap())

private fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())

private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray)?.toList().orEmpty()

private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0

private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
