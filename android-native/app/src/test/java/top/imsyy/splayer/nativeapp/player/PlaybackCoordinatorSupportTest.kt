package top.imsyy.splayer.nativeapp.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.imsyy.splayer.nativeapp.data.repository.PlaybackSnapshot
import top.imsyy.splayer.nativeapp.model.PlayMode
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.model.UnlockServerMode

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
    fun `shouldRunProgressLoop keeps foreground mini player ticker alive at low cadence`() {
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
                isPlaying = true,
                isBuffering = true,
            ),
        )
        assertFalse(
            shouldRunProgressLoop(
                hasActiveSubscribers = true,
                isPlaying = false,
                isBuffering = false,
            ),
        )
    }

    @Test
    fun `resolveProgressLoopIntervalMs keeps low power cadence outside lyric page`() {
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
    }

    @Test
    fun `resolveProgressLoopIntervalMs uses responsive lyric cadence even without word timing`() {
        assertEquals(
            160L,
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
    fun `resolveQueueMoveTargetIndex lets manual next leave single loop like desktop`() {
        assertEquals(
            2,
            resolveQueueMoveTargetIndex(
                queueSize = 3,
                currentIndex = 1,
                playMode = PlayMode.SINGLE_LOOP,
                delta = 1,
            ),
        )
        assertEquals(
            2,
            resolveQueueMoveTargetIndex(
                queueSize = 3,
                currentIndex = 0,
                playMode = PlayMode.SINGLE_LOOP,
                delta = -1,
            ),
        )
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
    fun `resolveNextPlayMode cycles through desktop aligned modes`() {
        assertEquals(PlayMode.LIST_LOOP, resolveNextPlayMode(PlayMode.SEQUENCE))
        assertEquals(PlayMode.SINGLE_LOOP, resolveNextPlayMode(PlayMode.LIST_LOOP))
        assertEquals(PlayMode.SHUFFLE, resolveNextPlayMode(PlayMode.SINGLE_LOOP))
        assertEquals(PlayMode.HEART, resolveNextPlayMode(PlayMode.SHUFFLE))
        assertEquals(PlayMode.SEQUENCE, resolveNextPlayMode(PlayMode.HEART))
    }

    @Test
    fun `resolveNewQueuePlayMode leaves heartbeat when ordinary playlist starts`() {
        assertEquals(PlayMode.LIST_LOOP, resolveNewQueuePlayMode(PlayMode.HEART))
        assertEquals(PlayMode.SHUFFLE, resolveNewQueuePlayMode(PlayMode.SHUFFLE))
        assertEquals(PlayMode.SINGLE_LOOP, resolveNewQueuePlayMode(PlayMode.SINGLE_LOOP))
    }

    @Test
    fun `resolvePlaylistQueueExtension appends loaded tracks for same playlist and keeps current track index`() {
        val currentQueue = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3))
        val loadedTracks = currentQueue + listOf(sampleTrack(4), sampleTrack(5))

        val result = resolvePlaylistQueueExtension(
            activeSource = PlaybackQueueSource.Playlist(99L),
            requestedPlaylistId = 99L,
            currentQueue = currentQueue,
            currentTrack = sampleTrack(2),
            loadedTracks = loadedTracks,
            playMode = PlayMode.SEQUENCE,
            originalQueue = null,
        )

        requireNotNull(result)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), result.queue.map { it.id })
        assertEquals(1, result.currentIndex)
        assertNull(result.originalQueue)
    }

    @Test
    fun `resolvePlaylistQueueExtension ignores pages from inactive playlist`() {
        val result = resolvePlaylistQueueExtension(
            activeSource = PlaybackQueueSource.Playlist(88L),
            requestedPlaylistId = 99L,
            currentQueue = listOf(sampleTrack(1), sampleTrack(2)),
            currentTrack = sampleTrack(1),
            loadedTracks = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3)),
            playMode = PlayMode.SEQUENCE,
            originalQueue = null,
        )

        assertNull(result)
    }

    @Test
    fun `resolvePlaylistQueueExtension keeps shuffle visible order and expands original queue`() {
        val originalQueue = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3))
        val shuffledQueue = listOf(sampleTrack(2), sampleTrack(3), sampleTrack(1))
        val loadedTracks = originalQueue + listOf(sampleTrack(4), sampleTrack(5))

        val result = resolvePlaylistQueueExtension(
            activeSource = PlaybackQueueSource.Playlist(99L),
            requestedPlaylistId = 99L,
            currentQueue = shuffledQueue,
            currentTrack = sampleTrack(2),
            loadedTracks = loadedTracks,
            playMode = PlayMode.SHUFFLE,
            originalQueue = originalQueue,
        )

        requireNotNull(result)
        assertEquals(listOf(2L, 3L, 1L, 4L, 5L), result.queue.map { it.id })
        assertEquals(0, result.currentIndex)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), result.originalQueue?.map { it.id })
    }

    @Test
    fun `previewPlayModeState changes visible mode without touching queue`() {
        val tracks = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3))
        val state = PlaybackUiState(
            queue = tracks,
            currentTrack = tracks[1],
            currentIndex = 1,
            playMode = PlayMode.SINGLE_LOOP,
            errorMessage = "旧错误",
        )

        val preview = previewPlayModeState(state, PlayMode.HEART)

        assertEquals(PlayMode.HEART, preview.playMode)
        assertEquals(tracks, preview.queue)
        assertEquals(tracks[1], preview.currentTrack)
        assertEquals(1, preview.currentIndex)
        assertNull(preview.errorMessage)
    }

    @Test
    fun `resolvePlayModeQueueTransition shuffles current queue immediately and keeps current track first`() {
        val tracks = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3), sampleTrack(4))
        val shuffled = listOf(tracks[3], tracks[2], tracks[0], tracks[1])

        val transition = resolvePlayModeQueueTransition(
            currentQueue = tracks,
            currentIndex = 1,
            currentMode = PlayMode.SINGLE_LOOP,
            targetMode = PlayMode.SHUFFLE,
            originalQueue = null,
            shuffledTracks = shuffled,
        )

        assertEquals(listOf(2L, 4L, 3L, 1L), transition.queue.map { it.id })
        assertEquals(0, transition.currentIndex)
        assertEquals(listOf(1L, 2L, 3L, 4L), transition.originalQueue?.map { it.id })
    }

    @Test
    fun `resolvePlayModeQueueTransition restores original queue when leaving shuffled mode`() {
        val original = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3), sampleTrack(4))
        val shuffled = listOf(original[1], original[3], original[2], original[0])

        val transition = resolvePlayModeQueueTransition(
            currentQueue = shuffled,
            currentIndex = 0,
            currentMode = PlayMode.SHUFFLE,
            targetMode = PlayMode.LIST_LOOP,
            originalQueue = original,
        )

        assertEquals(listOf(1L, 2L, 3L, 4L), transition.queue.map { it.id })
        assertEquals(1, transition.currentIndex)
        assertNull(transition.originalQueue)
    }

    @Test
    fun `resolvePlayModeQueueTransition builds heartbeat queue from current track and recommendations`() {
        val tracks = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3))
        val heartTracks = listOf(sampleTrack(5), sampleTrack(2), sampleTrack(6))

        val transition = resolvePlayModeQueueTransition(
            currentQueue = tracks,
            currentIndex = 1,
            currentMode = PlayMode.SHUFFLE,
            targetMode = PlayMode.HEART,
            originalQueue = null,
            heartTracks = heartTracks,
        )

        assertEquals(listOf(2L, 5L, 6L), transition.queue.map { it.id })
        assertEquals(0, transition.currentIndex)
        assertEquals(listOf(1L, 2L, 3L), transition.originalQueue?.map { it.id })
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
    fun `resolveRestoredPlaybackState restores paused current track from queue snapshot`() {
        val queue = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3))

        val restored = resolveRestoredPlaybackState(
            previousState = PlaybackUiState(isPlaying = true, isBuffering = true),
            queue = queue,
            snapshot = PlaybackSnapshot(
                currentTrackId = 2L,
                currentIndex = 1,
                positionMs = 64_000L,
                durationMs = 180_000L,
                savedAtMs = 123L,
            ),
            playMode = PlayMode.SHUFFLE,
        )

        assertEquals(2L, restored.currentTrack?.id)
        assertEquals(1, restored.currentIndex)
        assertEquals(64_000L, restored.positionMs)
        assertEquals(180_000L, restored.durationMs)
        assertEquals(PlayMode.SHUFFLE, restored.playMode)
        assertFalse(restored.isPlaying)
        assertFalse(restored.isBuffering)
        assertNull(restored.currentSource)
    }

    @Test
    fun `resolveRestoredPlaybackState ignores snapshot when current track is absent from queue`() {
        val previous = PlaybackUiState(currentTrack = sampleTrack(9), currentIndex = 0)

        val restored = resolveRestoredPlaybackState(
            previousState = previous,
            queue = listOf(sampleTrack(1), sampleTrack(2)),
            snapshot = PlaybackSnapshot(
                currentTrackId = 9L,
                currentIndex = 0,
                positionMs = 30_000L,
                durationMs = 180_000L,
                savedAtMs = 123L,
            ),
            playMode = PlayMode.LIST_LOOP,
        )

        assertNull(restored.currentTrack)
        assertEquals(-1, restored.currentIndex)
        assertEquals(listOf(1L, 2L), restored.queue.map { it.id })
        assertEquals(PlayMode.LIST_LOOP, restored.playMode)
    }

    @Test
    fun `resolveRestoredPlaybackState clears stale ui when queue is empty`() {
        val previous = PlaybackUiState(
            queue = listOf(sampleTrack(9)),
            currentTrack = sampleTrack(9),
            currentIndex = 0,
            positionMs = 50_000L,
            durationMs = 180_000L,
        )

        val restored = resolveRestoredPlaybackState(
            previousState = previous,
            queue = emptyList(),
            snapshot = null,
            playMode = PlayMode.SEQUENCE,
        )

        assertEquals(emptyList<TrackItem>(), restored.queue)
        assertNull(restored.currentTrack)
        assertEquals(-1, restored.currentIndex)
        assertEquals(0L, restored.positionMs)
        assertEquals(0L, restored.durationMs)
    }

    @Test
    fun `sanitizeRestoredPositionMs resets progress near song ending`() {
        assertEquals(0L, sanitizeRestoredPositionMs(positionMs = 176_000L, durationMs = 180_000L))
        assertEquals(0L, sanitizeRestoredPositionMs(positionMs = 180_000L, durationMs = 180_000L))
        assertEquals(120_000L, sanitizeRestoredPositionMs(positionMs = 120_000L, durationMs = 180_000L))
    }

    @Test
    fun `toMiniPlayerChromeState exposes coarse progress for mini player bar`() {
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

        assertEquals(12_000L, first.positionMs)
        assertEquals(18_000L, second.positionMs)
        assertEquals(245_000L, first.durationMs)
        assertEquals(1, first.queueCount)
        assertEquals("测试歌曲", first.currentTrack?.name)
    }

    @Test
    fun `resolveMiniPlayerProgressFraction handles empty duration safely`() {
        assertEquals(0f, resolveMiniPlayerProgressFraction(positionMs = 30_000L, durationMs = 0L), 0.001f)
        assertEquals(0.5f, resolveMiniPlayerProgressFraction(positionMs = 90_000L, durationMs = 180_000L), 0.001f)
        assertEquals(1f, resolveMiniPlayerProgressFraction(positionMs = 220_000L, durationMs = 180_000L), 0.001f)
    }

    @Test
    fun `resolvePlaybackToggleAction upgrades restored ui state before normal player play`() {
        val restoredState = PlaybackUiState(
            currentTrack = sampleTrack(7),
            currentSource = null,
            isPlaying = false,
        )

        assertEquals(
            PlaybackToggleAction.ResumeRestored,
            resolvePlaybackToggleAction(
                state = restoredState,
                playerMediaItemCount = 0,
                playerIsPlaying = false,
            ),
        )
        assertEquals(
            PlaybackToggleAction.PlayPrepared,
            resolvePlaybackToggleAction(
                state = restoredState,
                playerMediaItemCount = 1,
                playerIsPlaying = false,
            ),
        )
        assertEquals(
            PlaybackToggleAction.Pause,
            resolvePlaybackToggleAction(
                state = restoredState.copy(isPlaying = true),
                playerMediaItemCount = 1,
                playerIsPlaying = true,
            ),
        )
    }

    @Test
    fun `shouldApplyRestoredPlaybackSnapshot depends on captured player state only`() {
        val restoredState = PlaybackUiState(
            currentTrack = null,
            currentSource = null,
            isPlaying = false,
        )

        assertTrue(
            shouldApplyRestoredPlaybackSnapshot(
                restoreSnapshotApplied = false,
                playerMediaItemCount = 0,
                state = restoredState,
            ),
        )
        assertFalse(
            shouldApplyRestoredPlaybackSnapshot(
                restoreSnapshotApplied = false,
                playerMediaItemCount = 1,
                state = restoredState,
            ),
        )
        assertFalse(
            shouldApplyRestoredPlaybackSnapshot(
                restoreSnapshotApplied = true,
                playerMediaItemCount = 0,
                state = restoredState,
            ),
        )
    }

    @Test
    fun `resolveEnabledUnlockServers keeps local and remote provider order consistent`() {
        val servers = resolveEnabledUnlockServers(
            mode = UnlockServerMode.LOCAL,
            failedSources = emptySet(),
        )
        val remoteServers = resolveEnabledUnlockServers(
            mode = UnlockServerMode.EXTERNAL,
            failedSources = emptySet(),
        )

        assertEquals(listOf("bodian", "gequbao", "netease", "kuwo"), servers)
        assertEquals(remoteServers, servers)
    }

    @Test
    fun `resolveEnabledUnlockServers uses remote unblock candidates only in external mode`() {
        val servers = resolveEnabledUnlockServers(
            mode = UnlockServerMode.EXTERNAL,
            failedSources = setOf("kuwo"),
        )

        assertEquals(listOf("bodian", "gequbao", "netease"), servers)
    }

    @Test
    fun `resolveAudioFocusHandling requests focus unless concurrent playback is allowed`() {
        assertTrue(resolveAudioFocusHandling(allowConcurrentPlayback = false))
        assertFalse(resolveAudioFocusHandling(allowConcurrentPlayback = true))
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
