package top.imsyy.splayer.nativeapp.ui

import top.imsyy.splayer.nativeapp.model.CommentItem
import top.imsyy.splayer.nativeapp.model.CommentPageResult
import top.imsyy.splayer.nativeapp.model.TrackItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewModelSupportTest {
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
}
