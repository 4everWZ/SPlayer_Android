package top.imsyy.splayer.nativeapp.ui

import top.imsyy.splayer.nativeapp.model.DiscoveryBrowseUi
import top.imsyy.splayer.nativeapp.model.DiscoveryHomeUi
import top.imsyy.splayer.nativeapp.model.DiscoveryQuickEntry
import top.imsyy.splayer.nativeapp.model.MyMusicHomeUi
import top.imsyy.splayer.nativeapp.model.MyMusicPanelUi
import top.imsyy.splayer.nativeapp.model.PlaylistItem
import top.imsyy.splayer.nativeapp.model.RecommendFeedUi
import top.imsyy.splayer.nativeapp.model.RecommendHeroCardUi
import top.imsyy.splayer.nativeapp.model.UserAccountUi
import top.imsyy.splayer.nativeapp.ui.navigation.DrawerEntry
import top.imsyy.splayer.nativeapp.ui.navigation.TopLevelDestination

fun topLevelMusicDestinations(): List<TopLevelDestination> {
    return listOf(
        TopLevelDestination.Recommend,
        TopLevelDestination.Discovery,
        TopLevelDestination.My,
    )
}

fun listeningDrawerEntries(): List<DrawerEntry> {
    return listOf(
        DrawerEntry.LikedSongs,
        DrawerEntry.RecentPlays,
        DrawerEntry.CollectedPlaylists,
        DrawerEntry.Settings,
    )
}

fun buildRecommendFeedUi(
    discovery: DiscoveryHomeUi,
    recentTracks: List<top.imsyy.splayer.nativeapp.model.TrackItem>,
): RecommendFeedUi {
    val heroCards = discovery.recommendedPlaylists
        .take(2)
        .mapIndexed { index, playlist ->
            RecommendHeroCardUi(
                playlistId = playlist.id,
                title = playlist.name,
                subtitle = when {
                    index == 0 && recentTracks.isNotEmpty() -> "根据你最近听过的内容继续推荐"
                    index == 0 -> "为你整理的今日推荐歌单"
                    else -> "继续扩展你的音乐偏好"
                },
                coverUrl = playlist.coverUrl,
                badge = when (index) {
                    0 -> "推荐"
                    else -> "精选"
                },
            )
        }

    return RecommendFeedUi(
        heroCards = heroCards,
        guessTracks = discovery.newSongs.take(6),
        recommendedPlaylists = discovery.recommendedPlaylists.drop(heroCards.size).take(6),
        newAlbums = discovery.newAlbums.take(6),
        topPlaylists = discovery.topPlaylists.take(6),
        newSongs = discovery.newSongs.take(12),
    )
}

fun buildDiscoveryBrowseUi(discovery: DiscoveryHomeUi): DiscoveryBrowseUi {
    return DiscoveryBrowseUi(
        quickEntries = defaultDiscoveryQuickEntries(),
        featuredPlaylists = discovery.recommendedPlaylists.take(6),
        topPlaylists = discovery.topPlaylists.take(6),
        newSongs = discovery.newSongs.take(10),
        newAlbums = discovery.newAlbums.take(8),
        topArtists = discovery.topArtists.take(10),
    )
}

fun buildMyMusicPanel(content: MyMusicHomeUi): MyMusicPanelUi {
    return MyMusicPanelUi(
        header = content.currentUser ?: UserAccountUi(
            userId = 0L,
            nickname = "未登录",
            avatarUrl = "",
            signature = "登录后同步你的喜欢歌曲和歌单",
        ),
        likedSongCount = content.likedSongCount,
        likedPlaylist = content.likedPlaylist,
        recentTracks = content.recentTracks,
        recentPlaylists = content.recentPlaylists.ifEmpty {
            buildList {
                content.likedPlaylist?.let(::add)
                addAll(content.createdPlaylists)
                addAll(content.collectedPlaylists)
            }.distinctBy { playlist -> playlist.id }.take(8)
        },
        createdPlaylists = content.createdPlaylists,
        collectedPlaylists = content.collectedPlaylists,
        albums = content.albums,
        listeningRanks = content.listeningRanks,
    )
}

private fun defaultDiscoveryQuickEntries(): List<DiscoveryQuickEntry> {
    return listOf(
        DiscoveryQuickEntry(label = "华语", keyword = "华语"),
        DiscoveryQuickEntry(label = "日语", keyword = "日语"),
        DiscoveryQuickEntry(label = "ACG", keyword = "ACG"),
        DiscoveryQuickEntry(label = "学习", keyword = "学习歌单"),
        DiscoveryQuickEntry(label = "夜晚", keyword = "夜晚歌单"),
        DiscoveryQuickEntry(label = "流行", keyword = "流行歌曲"),
    )
}

internal fun splitCreatedAndCollectedPlaylists(
    currentUserId: Long,
    playlists: List<PlaylistItem>,
): Pair<List<PlaylistItem>, List<PlaylistItem>> {
    return playlists.partition { it.creatorUserId == currentUserId }
}
