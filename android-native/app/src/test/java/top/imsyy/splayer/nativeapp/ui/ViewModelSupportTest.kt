package top.imsyy.splayer.nativeapp.ui

import top.imsyy.splayer.nativeapp.model.CommentItem
import top.imsyy.splayer.nativeapp.model.CommentPageResult
import top.imsyy.splayer.nativeapp.model.PlaylistDetailUi
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.player.PlaybackQueueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ViewModelSupportTest {
    @Test
    fun `discovery refresh coordinator consumes one automatic force refresh per foreground epoch`() {
        val coordinator = DiscoveryRefreshCoordinator()

        assertEquals(true, coordinator.resolveForceRefresh(forceRefresh = false))
        assertEquals(false, coordinator.resolveForceRefresh(forceRefresh = false))

        coordinator.markAppOpened()

        assertEquals(true, coordinator.resolveForceRefresh(forceRefresh = false))
        assertEquals(false, coordinator.resolveForceRefresh(forceRefresh = false))
    }

    @Test
    fun `discovery refresh coordinator ignores lock screen foreground transitions`() {
        val coordinator = DiscoveryRefreshCoordinator()

        assertEquals(true, coordinator.resolveForceRefresh(forceRefresh = false))
        assertEquals(false, coordinator.resolveForceRefresh(forceRefresh = false))

        coordinator.markAppForegrounded()

        assertEquals(false, coordinator.resolveForceRefresh(forceRefresh = false))
    }

    @Test
    fun `discovery refresh coordinator keeps manual force independent from automatic epoch`() {
        val coordinator = DiscoveryRefreshCoordinator()

        assertEquals(true, coordinator.resolveForceRefresh(forceRefresh = true))
        assertEquals(true, coordinator.resolveForceRefresh(forceRefresh = false))
        assertEquals(false, coordinator.resolveForceRefresh(forceRefresh = false))
    }

    @Test
    fun `refresh policy allows force refresh to replace active ordinary refresh`() {
        assertEquals(false, shouldStartDiscoveryRefresh(refreshInProgress = true, forceRefresh = false))
        assertEquals(true, shouldStartDiscoveryRefresh(refreshInProgress = true, forceRefresh = true))
        assertEquals(true, shouldStartDiscoveryRefresh(refreshInProgress = false, forceRefresh = false))
    }

    @Test
    fun `api root activation triggers refresh only when root changes from blank to configured`() {
        assertEquals(true, shouldRefreshAfterApiRootChange(previousApiRoot = "", nextApiRoot = "https://api.example.com/splayer"))
        assertEquals(false, shouldRefreshAfterApiRootChange(previousApiRoot = "https://api.example.com/splayer", nextApiRoot = "https://api.example.com/splayer"))
        assertEquals(false, shouldRefreshAfterApiRootChange(previousApiRoot = "https://api.example.com/splayer", nextApiRoot = ""))
        assertEquals(false, shouldRefreshAfterApiRootChange(previousApiRoot = "", nextApiRoot = ""))
    }

    @Test
    fun `sanitizeLoadErrorMessage hides raw duplicated json parse details`() {
        val message = sanitizeLoadErrorMessage(
            rawMessage = "Unexpected JSON token at offset 26: Expected EOF after parsing, but had { instead at path: \$ JSON input: {\"msg\":\"参数错误\",\"code\":400}{\"msg\":\"参数错误\",\"code\":400}",
            fallback = "加载歌单失败",
        )

        assertEquals("加载歌单失败，请稍后重试", message)
    }

    @Test
    fun `sanitizeLoadErrorMessage keeps ordinary human readable messages`() {
        val message = sanitizeLoadErrorMessage(
            rawMessage = "歌单参数无效",
            fallback = "加载歌单失败",
        )

        assertEquals("歌单参数无效", message)
    }

    @Test
    fun `sanitizeLoadErrorMessage keeps first install local api from asking for api root`() {
        val message = sanitizeLoadErrorMessage(
            rawMessage = "请先在设置中填写 API 根路径",
            fallback = "加载首页失败",
        )

        assertEquals("加载首页失败，请稍后重试", message)
    }

    @Test
    fun `sanitizeLoadErrorMessage maps remote empty api root to mode specific hint`() {
        val message = sanitizeLoadErrorMessage(
            rawMessage = "远程 API 模式需要 API 根路径",
            fallback = "加载发现页失败",
        )

        assertEquals("远程 API 模式需要先填写 API 根路径，或切回本地 API 模式登录/使用", message)
    }

    @Test
    fun `sanitizeLoadErrorMessage maps unauthenticated errors to netease login hint`() {
        val message = sanitizeLoadErrorMessage(
            rawMessage = "请先在设置中填写 API 根路径后登录",
            fallback = "加载我的页面失败",
        )

        assertEquals("请先登录网易云账号", message)
    }

    @Test
    fun `resolveCommentPreviewCount keeps cached comment count visible before sheet opens`() {
        val hot = CommentPageResult(
            comments = listOf(sampleComment(id = 1L)),
            totalCount = 532,
        )
        val latest = CommentPageResult(
            comments = listOf(sampleComment(id = 2L), sampleComment(id = 3L)),
            totalCount = 1280,
        )

        assertEquals(1280, resolveCommentPreviewCount(existingCount = 0, hotComments = hot, latestComments = latest))
        assertEquals(532, resolveCommentPreviewCount(existingCount = 12, hotComments = hot, latestComments = null))
        assertEquals(12, resolveCommentPreviewCount(existingCount = 12, hotComments = null, latestComments = null))
    }

    @Test
    fun `resolveMergedTrackPage stops pagination when a non empty page adds no new tracks`() {
        val existing = listOf(sampleTrack(id = 1L), sampleTrack(id = 2L))
        val incomingDuplicateOnly = listOf(sampleTrack(id = 2L))

        val result = resolveMergedTrackPage(
            existingTracks = existing,
            incomingTracks = incomingDuplicateOnly,
            trackCount = 10,
        )

        assertEquals(existing, result.tracks)
        assertEquals(false, result.madeProgress)
        assertEquals(false, result.hasMore)
    }

    @Test
    fun `resolveMergedTrackPage keeps pagination only when new tracks were appended`() {
        val result = resolveMergedTrackPage(
            existingTracks = listOf(sampleTrack(id = 1L)),
            incomingTracks = listOf(sampleTrack(id = 2L), sampleTrack(id = 3L)),
            trackCount = 5,
        )

        assertEquals(3, result.tracks.size)
        assertEquals(true, result.madeProgress)
        assertEquals(true, result.hasMore)
    }

    @Test
    fun `resolveMaxPlaylistPageRequests adds one guard page beyond expected remaining pages`() {
        assertEquals(0, resolveMaxPlaylistPageRequests(loadedTrackCount = 10, trackCount = 10))
        assertEquals(2, resolveMaxPlaylistPageRequests(loadedTrackCount = 0, trackCount = 500))
        assertEquals(5, resolveMaxPlaylistPageRequests(loadedTrackCount = 80, trackCount = 1773))
    }

    @Test
    fun `resolvePlaylistInitialTrackForceRefresh follows mutation force refresh`() {
        assertEquals(true, resolvePlaylistInitialTrackForceRefresh(cached = null, forceRefresh = false))
        assertEquals(false, resolvePlaylistInitialTrackForceRefresh(cached = samplePlaylist(), forceRefresh = false))
        assertEquals(true, resolvePlaylistInitialTrackForceRefresh(cached = samplePlaylist(), forceRefresh = true))
    }

    @Test
    fun `resolvePlaylistPlaybackRequest uses currently loaded tracks without waiting for hidden full list`() {
        val loadedTracks = (1L..80L).map(::sampleTrack)
        val clickedTrack = sampleTrack(id = 72L)

        val request = resolvePlaylistPlaybackRequest(
            clickedTrackId = clickedTrack.id,
            loadedTracks = loadedTracks,
            clickedIndex = 70,
        )

        assertEquals(80, request.tracks.size)
        assertEquals(71, request.startIndex)
        assertEquals(72L, request.tracks[request.startIndex].id)
    }

    @Test
    fun `resolvePlaylistPlaybackRequest opens player instead of restarting current track`() {
        val loadedTracks = listOf(sampleTrack(id = 1L), sampleTrack(id = 2L), sampleTrack(id = 3L))

        val request = resolvePlaylistPlaybackRequest(
            clickedTrackId = 2L,
            loadedTracks = loadedTracks,
            clickedIndex = 1,
            currentTrackId = 2L,
        )

        assertFalse(request.shouldStartPlayback)
        assertEquals(1, request.startIndex)
    }

    @Test
    fun `resolvePlaylistCurrentTrackLazyIndex returns null when current track is absent`() {
        val loadedTracks = listOf(sampleTrack(id = 1L), sampleTrack(id = 2L), sampleTrack(id = 3L))

        assertEquals(null, resolvePlaylistCurrentTrackLazyIndex(null, loadedTracks, leadingItemCount = 3))
        assertEquals(null, resolvePlaylistCurrentTrackLazyIndex(0L, loadedTracks, leadingItemCount = 3))
        assertEquals(null, resolvePlaylistCurrentTrackLazyIndex(9L, loadedTracks, leadingItemCount = 3))
    }

    @Test
    fun `resolvePlaylistCurrentTrackLazyIndex offsets loaded track index by leading items`() {
        val loadedTracks = listOf(sampleTrack(id = 10L), sampleTrack(id = 20L), sampleTrack(id = 30L))

        assertEquals(5, resolvePlaylistCurrentTrackLazyIndex(30L, loadedTracks, leadingItemCount = 3))
    }

    @Test
    fun `resolvePlaylistQueueSyncRequest replays cached loaded tracks after playlist playback starts`() {
        val cachedPlaylist = PlaylistDetailUi(
            id = 88L,
            name = "测试歌单",
            coverUrl = "",
            description = "",
            playCount = 0L,
            subscribedCount = 0L,
            trackCount = 3,
            tracks = listOf(sampleTrack(id = 1L), sampleTrack(id = 2L), sampleTrack(id = 3L)),
        )

        val request = resolvePlaylistQueueSyncRequest(
            queueSource = PlaybackQueueSource.Playlist(88L),
            cachedPlaylist = cachedPlaylist,
        )

        requireNotNull(request)
        assertEquals(88L, request.playlistId)
        assertEquals(listOf(1L, 2L, 3L), request.tracks.map { it.id })
    }

    @Test
    fun `shouldShowRefreshLoading hides restored page refresh animation when content exists`() {
        assertEquals(true, shouldShowRefreshLoading(hasContent = false, showLoading = false))
        assertEquals(false, shouldShowRefreshLoading(hasContent = true, showLoading = false))
        assertEquals(true, shouldShowRefreshLoading(hasContent = true, showLoading = true))
    }

    private fun sampleComment(id: Long): CommentItem {
        return CommentItem(
            id = id,
            userName = "测试用户",
            userAvatar = "",
            content = "评论",
            likedCount = 0,
            time = 0L,
        )
    }

    private fun sampleTrack(id: Long): TrackItem {
        return TrackItem(
            id = id,
            name = "测试歌曲$id",
            artists = "测试歌手",
            album = "测试专辑",
            coverUrl = "",
            durationMs = 180_000L,
        )
    }

    private fun samplePlaylist(): PlaylistDetailUi {
        return PlaylistDetailUi(
            id = 88L,
            name = "测试歌单",
            coverUrl = "",
            description = "",
            playCount = 0L,
            subscribedCount = 0L,
            trackCount = 1,
            tracks = listOf(sampleTrack(id = 1L)),
        )
    }
}
