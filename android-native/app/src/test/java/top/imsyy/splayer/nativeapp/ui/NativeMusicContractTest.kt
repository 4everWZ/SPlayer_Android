package top.imsyy.splayer.nativeapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.imsyy.splayer.nativeapp.model.AlbumItem
import top.imsyy.splayer.nativeapp.model.DiscoveryHomeUi
import top.imsyy.splayer.nativeapp.model.MyMusicHomeUi
import top.imsyy.splayer.nativeapp.model.MyMusicPanelTab
import top.imsyy.splayer.nativeapp.model.PlaylistItem
import top.imsyy.splayer.nativeapp.model.RecommendChannel
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.model.UserAccountUi
import top.imsyy.splayer.nativeapp.ui.navigation.DrawerEntry
import top.imsyy.splayer.nativeapp.ui.navigation.TopLevelDestination

class NativeMusicContractTest {
    @Test
    fun `top level navigation keeps recommend discovery and my only`() {
        val tabs = topLevelMusicDestinations()

        assertEquals(3, tabs.size)
        assertEquals(
            listOf(
                TopLevelDestination.Recommend,
                TopLevelDestination.Discovery,
                TopLevelDestination.My,
            ),
            tabs,
        )
    }

    @Test
    fun `drawer entries keep listening related destinations only`() {
        assertEquals(
            listOf(
                DrawerEntry.LikedSongs,
                DrawerEntry.RecentPlays,
                DrawerEntry.CollectedPlaylists,
                DrawerEntry.Settings,
            ),
            listeningDrawerEntries(),
        )
    }

    @Test
    fun `buildRecommendFeedUi exposes fixed channels and music sections`() {
        val feed = buildRecommendFeedUi(
            discovery = sampleDiscoveryHome(),
            recentTracks = listOf(sampleTrack(id = 91, name = "最近听过的歌")),
        )

        assertEquals(RecommendChannel.entries.toList(), feed.channels)
        assertEquals(2, feed.heroCards.size)
        assertEquals(3, feed.guessTracks.size)
        assertEquals(2, feed.recommendedPlaylists.size)
        assertEquals(2, feed.topPlaylists.size)
        assertEquals(2, feed.newAlbums.size)
        assertTrue(feed.heroCards.all { it.playlistId > 0L })
    }

    @Test
    fun `buildMyMusicPanel keeps liked created collected and album assets`() {
        val user = UserAccountUi(
            userId = 7L,
            nickname = "测试用户",
            avatarUrl = "https://example.com/avatar.jpg",
        )
        val panel = buildMyMusicPanel(
            MyMusicHomeUi(
                currentUser = user,
                likedSongCount = 77,
                likedPlaylist = samplePlaylist(id = 700, name = "我喜欢的音乐", creatorUserId = 7L),
                recentTracks = listOf(sampleTrack(id = 801, name = "最近播放")),
                createdPlaylists = listOf(samplePlaylist(id = 701, name = "创建歌单", creatorUserId = 7L)),
                collectedPlaylists = listOf(samplePlaylist(id = 702, name = "收藏歌单", creatorUserId = 8L)),
                albums = listOf(
                    AlbumItem(
                        id = 901,
                        name = "收藏专辑",
                        coverUrl = "https://example.com/album.jpg",
                        artistName = "歌手甲",
                        trackCount = 10,
                    ),
                ),
            ),
        )

        assertEquals("测试用户", panel.header.nickname)
        assertEquals(77, panel.likedSongCount)
        assertEquals("我喜欢的音乐", panel.likedPlaylist?.name)
        assertEquals(1, panel.tabItems(MyMusicPanelTab.Recent).size)
        assertEquals(1, panel.tabItems(MyMusicPanelTab.Created).size)
        assertEquals(1, panel.tabItems(MyMusicPanelTab.Collected).size)
        assertEquals(1, panel.tabItems(MyMusicPanelTab.Album).size)
    }

    private fun sampleDiscoveryHome(): DiscoveryHomeUi {
        return DiscoveryHomeUi(
            recommendedPlaylists = listOf(
                samplePlaylist(id = 1, name = "每日推荐"),
                samplePlaylist(id = 2, name = "心动模式"),
                samplePlaylist(id = 3, name = "私人订制"),
                samplePlaylist(id = 4, name = "夜晚循环"),
            ),
            newSongs = listOf(
                sampleTrack(id = 11, name = "新歌甲"),
                sampleTrack(id = 12, name = "新歌乙"),
                sampleTrack(id = 13, name = "新歌丙"),
            ),
            newAlbums = listOf(
                AlbumItem(
                    id = 21,
                    name = "新专辑甲",
                    coverUrl = "https://example.com/al1.jpg",
                    artistName = "歌手甲",
                    trackCount = 12,
                ),
                AlbumItem(
                    id = 22,
                    name = "新专辑乙",
                    coverUrl = "https://example.com/al2.jpg",
                    artistName = "歌手乙",
                    trackCount = 10,
                ),
            ),
            topPlaylists = listOf(
                samplePlaylist(id = 31, name = "飙升榜"),
                samplePlaylist(id = 32, name = "热歌榜"),
            ),
        )
    }

    private fun samplePlaylist(id: Long, name: String, creatorUserId: Long = 0L): PlaylistItem {
        return PlaylistItem(
            id = id,
            name = name,
            coverUrl = "https://example.com/$id.jpg",
            trackCount = 20,
            creatorUserId = creatorUserId,
        )
    }

    private fun sampleTrack(id: Long, name: String): TrackItem {
        return TrackItem(
            id = id,
            name = name,
            artists = "歌手甲",
            album = "专辑甲",
            coverUrl = "https://example.com/$id.jpg",
            durationMs = 180000,
        )
    }
}
