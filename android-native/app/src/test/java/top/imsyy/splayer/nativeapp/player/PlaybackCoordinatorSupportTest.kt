package top.imsyy.splayer.nativeapp.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
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
    fun `shouldRunProgressLoop only keeps ticker alive while playing and observed`() {
        assertTrue(
            shouldRunProgressLoop(
                hasActiveSubscribers = true,
                isPlaying = true,
                isBuffering = false,
            ),
        )
        assertFalse(
            shouldRunProgressLoop(
                hasActiveSubscribers = false,
                isPlaying = true,
                isBuffering = false,
            ),
        )
        assertFalse(
            shouldRunProgressLoop(
                hasActiveSubscribers = true,
                isPlaying = false,
                isBuffering = false,
            ),
        )
        assertFalse(
            shouldRunProgressLoop(
                hasActiveSubscribers = true,
                isPlaying = true,
                isBuffering = true,
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
}
