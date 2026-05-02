package top.imsyy.splayer.nativeapp.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.imsyy.splayer.nativeapp.model.PlayMode
import top.imsyy.splayer.nativeapp.model.TrackItem

class PlaybackCoordinatorSupportTest {
    @Test
    fun `seedPendingPlaybackState primes player ui before source resolution completes`() {
        val pendingTrack = TrackItem(
            id = 77L,
            name = "待播歌曲",
            artists = "测试歌手",
            album = "测试专辑",
            coverUrl = "https://example.com/cover.jpg",
            durationMs = 215000L,
        )

        val result = seedPendingPlaybackState(
            previousState = PlaybackUiState(queue = listOf(pendingTrack)),
            track = pendingTrack,
            queueIndex = 0,
        )

        assertEquals(77L, result.currentTrack?.id)
        assertEquals(0, result.currentIndex)
        assertTrue(result.isBuffering)
        assertEquals(215000L, result.durationMs)
        assertEquals(0L, result.positionMs)
    }

    @Test
    fun `shouldRunProgressLoop only keeps ticker alive while player detail is active`() {
        assertTrue(
            shouldRunProgressLoop(
                hasActiveSubscribers = true,
                playerScreenActive = true,
                isPlaying = true,
                isBuffering = false,
            ),
        )
        assertFalse(
            shouldRunProgressLoop(
                hasActiveSubscribers = false,
                playerScreenActive = true,
                isPlaying = true,
                isBuffering = false,
            ),
        )
        assertFalse(
            shouldRunProgressLoop(
                hasActiveSubscribers = true,
                playerScreenActive = false,
                isPlaying = true,
                isBuffering = false,
            ),
        )
        assertFalse(
            shouldRunProgressLoop(
                hasActiveSubscribers = true,
                playerScreenActive = true,
                isPlaying = false,
                isBuffering = false,
            ),
        )
        assertFalse(
            shouldRunProgressLoop(
                hasActiveSubscribers = true,
                playerScreenActive = true,
                isPlaying = true,
                isBuffering = true,
            ),
        )
    }

    @Test
    fun `resolveProgressLoopIntervalMs drops to line level cadence when lyrics have no true word timing`() {
        assertEquals(
            2_000L,
            resolveProgressLoopIntervalMs(
                playerScreenActive = false,
                lyricScreenActive = false,
                wordLevelLyricActive = false,
            ),
        )
        assertEquals(
            900L,
            resolveProgressLoopIntervalMs(
                playerScreenActive = true,
                lyricScreenActive = false,
                wordLevelLyricActive = false,
            ),
        )
        assertEquals(
            900L,
            resolveProgressLoopIntervalMs(
                playerScreenActive = true,
                lyricScreenActive = true,
                wordLevelLyricActive = false,
            ),
        )
        assertEquals(
            160L,
            resolveProgressLoopIntervalMs(
                playerScreenActive = true,
                lyricScreenActive = true,
                wordLevelLyricActive = true,
            ),
        )
    }

    @Test
    fun `shouldRunStallWatchdog only stays active during real buffering recovery windows`() {
        assertTrue(
            shouldRunStallWatchdog(
                hasTrack = true,
                isBuffering = true,
                playWhenReady = true,
            ),
        )
        assertFalse(
            shouldRunStallWatchdog(
                hasTrack = false,
                isBuffering = true,
                playWhenReady = true,
            ),
        )
        assertFalse(
            shouldRunStallWatchdog(
                hasTrack = true,
                isBuffering = false,
                playWhenReady = true,
            ),
        )
        assertFalse(
            shouldRunStallWatchdog(
                hasTrack = true,
                isBuffering = true,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun `shouldDispatchProgressUpdate skips unchanged playback ticks`() {
        val shouldDispatch = shouldDispatchProgressUpdate(
            previousPositionMs = 12_000L,
            previousDurationMs = 180_000L,
            currentPositionMs = 12_000L,
            currentDurationMs = 180_000L,
        )

        assertFalse(shouldDispatch)
    }

    @Test
    fun `shouldDispatchProgressUpdate emits when position advances`() {
        val shouldDispatch = shouldDispatchProgressUpdate(
            previousPositionMs = 12_000L,
            previousDurationMs = 180_000L,
            currentPositionMs = 13_000L,
            currentDurationMs = 180_000L,
        )

        assertTrue(shouldDispatch)
    }

    @Test
    fun `shouldTriggerStallRecovery only fires after timeout while buffering`() {
        assertFalse(
            shouldTriggerStallRecovery(
                isBuffering = true,
                noProgressDurationMs = 8_999L,
                stallTimeoutMs = 9_000L,
            ),
        )
        assertTrue(
            shouldTriggerStallRecovery(
                isBuffering = true,
                noProgressDurationMs = 9_000L,
                stallTimeoutMs = 9_000L,
            ),
        )
        assertFalse(
            shouldTriggerStallRecovery(
                isBuffering = false,
                noProgressDurationMs = 12_000L,
                stallTimeoutMs = 9_000L,
            ),
        )
    }

    @Test
    fun `resolvePlaybackEndTargetIndex advances sequence until the queue end`() {
        assertEquals(
            1,
            resolvePlaybackEndTargetIndex(
                queueSize = 3,
                currentIndex = 0,
                playMode = PlayMode.SEQUENCE,
            ),
        )
        assertNull(
            resolvePlaybackEndTargetIndex(
                queueSize = 3,
                currentIndex = 2,
                playMode = PlayMode.SEQUENCE,
            ),
        )
    }

    @Test
    fun `resolveSystemMediaTransportAvailability exposes skip controls when app queue has multiple tracks`() {
        val availability = resolveSystemMediaTransportAvailability(
            queueSize = 3,
            currentIndex = 1,
        )

        assertTrue(availability.previousAvailable)
        assertTrue(availability.nextAvailable)
    }

    @Test
    fun `resolveQueueMoveTargetIndex advances system next through app queue`() {
        val targetIndex = resolveQueueMoveTargetIndex(
            queueSize = 3,
            currentIndex = 1,
            playMode = PlayMode.SEQUENCE,
            delta = 1,
        )

        assertEquals(2, targetIndex)
    }

    @Test
    fun `resolvePlaybackQueuePlan shuffles the whole queue instead of picking one random next track`() {
        val tracks = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3), sampleTrack(4))
        val shuffled = listOf(tracks[2], tracks[0], tracks[3], tracks[1])

        val plan = resolvePlaybackQueuePlan(
            tracks = tracks,
            startIndex = 0,
            playMode = PlayMode.SHUFFLE,
            shuffledTracks = shuffled,
            keepRequestedTrackFirst = false,
        )

        assertEquals(listOf(3L, 1L, 4L, 2L), plan.tracks.map { it.id })
        assertEquals(0, plan.startIndex)
    }

    @Test
    fun `resolvePlaybackQueuePlan keeps tapped track first in shuffled queue`() {
        val tracks = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3), sampleTrack(4))
        val shuffled = listOf(tracks[2], tracks[0], tracks[3])

        val plan = resolvePlaybackQueuePlan(
            tracks = tracks,
            startIndex = 1,
            playMode = PlayMode.SHUFFLE,
            shuffledTracks = shuffled,
            keepRequestedTrackFirst = true,
        )

        assertEquals(listOf(2L, 3L, 1L, 4L), plan.tracks.map { it.id })
        assertEquals(0, plan.startIndex)
    }

    @Test
    fun `resolvePlaybackEndTargetIndex honors loop and shuffle modes`() {
        assertEquals(
            2,
            resolvePlaybackEndTargetIndex(
                queueSize = 4,
                currentIndex = 2,
                playMode = PlayMode.SINGLE_LOOP,
            ),
        )
        assertEquals(
            0,
            resolvePlaybackEndTargetIndex(
                queueSize = 4,
                currentIndex = 3,
                playMode = PlayMode.LIST_LOOP,
            ),
        )
        assertEquals(
            3,
            resolvePlaybackEndTargetIndex(
                queueSize = 4,
                currentIndex = 2,
                playMode = PlayMode.SHUFFLE,
                shuffleCandidateIndex = 1,
            ),
        )
        assertEquals(
            3,
            resolvePlaybackEndTargetIndex(
                queueSize = 4,
                currentIndex = 2,
                playMode = PlayMode.SHUFFLE,
                shuffleCandidateIndex = 2,
            ),
        )
    }

    @Test
    fun `toMiniPlayerChromeState ignores progress ticks and keeps chrome payload stable`() {
        val track = TrackItem(
            id = 501L,
            name = "测试歌曲",
            artists = "测试歌手",
            album = "测试专辑",
            coverUrl = "https://example.com/cover.jpg",
            durationMs = 245000L,
        )

        val first = toMiniPlayerChromeState(
            PlaybackUiState(
                queue = listOf(track),
                currentTrack = track,
                currentIndex = 0,
                isPlaying = true,
                positionMs = 12_000L,
                durationMs = 245_000L,
            ),
        )
        val second = toMiniPlayerChromeState(
            PlaybackUiState(
                queue = listOf(track),
                currentTrack = track,
                currentIndex = 0,
                isPlaying = true,
                positionMs = 18_400L,
                durationMs = 245_000L,
            ),
        )

        assertEquals(first, second)
        assertEquals(1, first.queueCount)
        assertEquals("测试歌曲", first.currentTrack?.name)
    }
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
