package top.imsyy.splayer.nativeapp.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackItem(
    val id: Long,
    val name: String,
    val artists: String,
    val album: String,
    val coverUrl: String,
    val durationMs: Long,
    val keyword: String = "$name $artists",
)

data class PlaylistItem(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val trackCount: Int = 0,
    val creatorUserId: Long = 0L,
)

data class AlbumItem(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val artistName: String = "",
    val trackCount: Int = 0,
)

data class ListeningRankItem(
    val track: TrackItem,
    val playCount: Int,
)

data class ArtistItem(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val musicSize: Int = 0,
)

data class RadioItem(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val programCount: Int = 0,
    val description: String = "",
)

data class RadioCategoryUi(
    val id: Long,
    val name: String,
    val radios: List<RadioItem>,
)

data class DiscoveryHomeUi(
    val recommendedPlaylists: List<PlaylistItem> = emptyList(),
    val dailySongs: List<TrackItem> = emptyList(),
    val newSongs: List<TrackItem> = emptyList(),
    val topArtists: List<ArtistItem> = emptyList(),
    val newAlbums: List<AlbumItem> = emptyList(),
    val topPlaylists: List<PlaylistItem> = emptyList(),
)

data class PodcastHomeUi(
    val recommendedRadios: List<RadioItem> = emptyList(),
    val hotRadios: List<RadioItem> = emptyList(),
    val categories: List<RadioCategoryUi> = emptyList(),
)

data class RadioDetailUi(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val description: String,
    val programCount: Int,
    val programs: List<TrackItem>,
)

data class MyMusicHomeUi(
    val currentUser: UserAccountUi? = null,
    val likedSongCount: Int = 0,
    val likedPlaylist: PlaylistItem? = null,
    val recentTracks: List<TrackItem> = emptyList(),
    val recentPlaylists: List<PlaylistItem> = emptyList(),
    val createdPlaylists: List<PlaylistItem> = emptyList(),
    val collectedPlaylists: List<PlaylistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val listeningRanks: List<ListeningRankItem> = emptyList(),
)

data class PlaylistDetailUi(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val description: String,
    val playCount: Long,
    val subscribedCount: Long,
    val trackCount: Int,
    val tracks: List<TrackItem>,
)

data class AlbumDetailUi(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val artistName: String,
    val description: String,
    val subscribedCount: Long,
    val shareCount: Long,
    val commentCount: Int,
    val trackCount: Int,
    val tracks: List<TrackItem>,
)

data class CommentItem(
    val id: Long,
    val userName: String,
    val userAvatar: String,
    val content: String,
    val likedCount: Int,
    val time: Long,
)

data class CommentPageResult(
    val comments: List<CommentItem>,
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
)

data class LyricWordUi(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val romanized: String = "",
)

data class LyricLineUi(
    val startTimeMs: Long,
    val endTimeMs: Long = startTimeMs,
    val mainText: String,
    val translation: String = "",
    val romanized: String = "",
    val words: List<LyricWordUi> = emptyList(),
    val hasWordTiming: Boolean = false,
)

data class UserAccountUi(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String,
    val backgroundUrl: String = "",
    val signature: String = "",
    val level: Int = 0,
    val followCount: Int = 0,
    val followerCount: Int = 0,
    val listenCount: Int = 0,
)

enum class RecommendChannel(val label: String) {
    Recommend("推荐"),
    NewSong("新歌"),
    Playlist("歌单"),
    Album("专辑"),
    Toplist("排行"),
}

data class RecommendHeroCardUi(
    val playlistId: Long,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val badge: String = "",
)

data class RecommendFeedUi(
    val channels: List<RecommendChannel> = RecommendChannel.entries.toList(),
    val heroCards: List<RecommendHeroCardUi> = emptyList(),
    val guessTracks: List<TrackItem> = emptyList(),
    val recommendedPlaylists: List<PlaylistItem> = emptyList(),
    val newAlbums: List<AlbumItem> = emptyList(),
    val topPlaylists: List<PlaylistItem> = emptyList(),
    val newSongs: List<TrackItem> = emptyList(),
)

data class DiscoveryQuickEntry(
    val label: String,
    val keyword: String,
)

data class DiscoveryBrowseUi(
    val quickEntries: List<DiscoveryQuickEntry> = emptyList(),
    val featuredPlaylists: List<PlaylistItem> = emptyList(),
    val topPlaylists: List<PlaylistItem> = emptyList(),
    val newSongs: List<TrackItem> = emptyList(),
    val newAlbums: List<AlbumItem> = emptyList(),
    val topArtists: List<ArtistItem> = emptyList(),
)

enum class MyMusicPanelTab(val label: String) {
    Recent("近期"),
    Created("创建"),
    Collected("收藏"),
    Album("专辑"),
}

data class MyMusicPanelUi(
    val header: UserAccountUi = UserAccountUi(
        userId = 0L,
        nickname = "未登录",
        avatarUrl = "",
    ),
    val likedSongCount: Int = 0,
    val likedPlaylist: PlaylistItem? = null,
    val recentTracks: List<TrackItem> = emptyList(),
    val recentPlaylists: List<PlaylistItem> = emptyList(),
    val createdPlaylists: List<PlaylistItem> = emptyList(),
    val collectedPlaylists: List<PlaylistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val listeningRanks: List<ListeningRankItem> = emptyList(),
) {
    fun tabItems(tab: MyMusicPanelTab): List<Any> {
        return when (tab) {
            MyMusicPanelTab.Recent -> buildList {
                addAll(albums)
                addAll(recentPlaylists)
                addAll(listeningRanks)
            }
            MyMusicPanelTab.Created -> createdPlaylists
            MyMusicPanelTab.Collected -> collectedPlaylists
            MyMusicPanelTab.Album -> albums
        }
    }
}

enum class PlayMode(val rawValue: Int) {
    SEQUENCE(0),
    LIST_LOOP(1),
    SINGLE_LOOP(2),
    SHUFFLE(3),
    HEART(4);

    companion object {
        fun fromRaw(rawValue: Int): PlayMode = entries.firstOrNull { it.rawValue == rawValue } ?: SEQUENCE
    }
}

enum class ThemeMode(val rawValue: Int) {
    DARK(0),
    LIGHT(1);

    companion object {
        fun fromRaw(rawValue: Int): ThemeMode = entries.firstOrNull { it.rawValue == rawValue } ?: DARK
    }
}

enum class UnlockServerMode(val rawValue: Int) {
    LOCAL(0),
    EXTERNAL(1);

    companion object {
        fun fromRaw(rawValue: Int): UnlockServerMode = entries.firstOrNull { it.rawValue == rawValue } ?: LOCAL
    }
}

data class TrackSource(
    val url: String,
    val quality: String?,
    val source: String?,
    val unlocked: Boolean,
)
