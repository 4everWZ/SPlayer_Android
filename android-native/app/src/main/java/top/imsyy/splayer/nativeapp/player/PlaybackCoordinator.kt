package top.imsyy.splayer.nativeapp.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.imsyy.splayer.nativeapp.data.local.AppSettingsStore
import top.imsyy.splayer.nativeapp.data.repository.QueueRepository
import top.imsyy.splayer.nativeapp.di.ApplicationScope
import top.imsyy.splayer.nativeapp.model.PlayMode
import top.imsyy.splayer.nativeapp.model.TrackItem
import top.imsyy.splayer.nativeapp.model.TrackSource

data class PlaybackUiState(
    val queue: List<TrackItem> = emptyList(),
    val currentTrack: TrackItem? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playMode: PlayMode = PlayMode.SEQUENCE,
    val currentSource: String? = null,
    val errorMessage: String? = null,
)

sealed interface PlaybackQueueSource {
    data object None : PlaybackQueueSource
    data class Playlist(val playlistId: Long) : PlaybackQueueSource
}

data class MiniPlayerChromeState(
    val currentTrack: TrackItem? = null,
    val isPlaying: Boolean = false,
    val queueCount: Int = 0,
)

internal fun seedPendingPlaybackState(
    previousState: PlaybackUiState,
    track: TrackItem,
    queueIndex: Int,
): PlaybackUiState {
    return previousState.copy(
        currentTrack = track,
        currentIndex = queueIndex.coerceAtLeast(0),
        isBuffering = true,
        positionMs = 0L,
        durationMs = track.durationMs.takeIf { it > 0L } ?: previousState.durationMs,
        currentSource = null,
        errorMessage = null,
    )
}

internal fun shouldDispatchProgressUpdate(
    previousPositionMs: Long,
    previousDurationMs: Long,
    currentPositionMs: Long,
    currentDurationMs: Long,
): Boolean {
    return previousPositionMs != currentPositionMs || previousDurationMs != currentDurationMs
}

internal fun shouldRunProgressLoop(
    hasActiveSubscribers: Boolean,
    playerScreenActive: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
): Boolean {
    return hasActiveSubscribers && playerScreenActive && isPlaying && !isBuffering
}

internal fun resolveProgressLoopIntervalMs(
    playerScreenActive: Boolean,
    lyricScreenActive: Boolean,
    wordLevelLyricActive: Boolean,
): Long {
    return when {
        playerScreenActive && lyricScreenActive && wordLevelLyricActive -> 160L
        playerScreenActive && lyricScreenActive -> 160L
        playerScreenActive -> 900L
        else -> 2_000L
    }
}

internal fun toMiniPlayerChromeState(state: PlaybackUiState): MiniPlayerChromeState {
    return MiniPlayerChromeState(
        currentTrack = state.currentTrack,
        isPlaying = state.isPlaying,
        queueCount = state.queue.size,
    )
}

internal fun shouldRunStallWatchdog(
    hasTrack: Boolean,
    isBuffering: Boolean,
    playWhenReady: Boolean,
): Boolean {
    return hasTrack && isBuffering && playWhenReady
}

internal fun shouldTriggerStallRecovery(
    isBuffering: Boolean,
    noProgressDurationMs: Long,
    stallTimeoutMs: Long,
): Boolean {
    return isBuffering && noProgressDurationMs >= stallTimeoutMs
}

internal fun resolveAudioFocusHandling(allowConcurrentPlayback: Boolean): Boolean {
    return !allowConcurrentPlayback
}

internal data class SystemMediaTransportAvailability(
    val previousAvailable: Boolean,
    val nextAvailable: Boolean,
)

internal fun resolveSystemMediaTransportAvailability(
    queueSize: Int,
    currentIndex: Int,
): SystemMediaTransportAvailability {
    val hasQueueNavigation = queueSize > 1 && currentIndex in 0 until queueSize
    return SystemMediaTransportAvailability(
        previousAvailable = hasQueueNavigation,
        nextAvailable = hasQueueNavigation,
    )
}

internal fun resolveQueueMoveTargetIndex(
    queueSize: Int,
    currentIndex: Int,
    playMode: PlayMode,
    delta: Int,
    shuffleCandidateIndex: Int? = null,
): Int? {
    if (queueSize <= 0) return null
    val safeCurrentIndex = currentIndex.coerceIn(0, queueSize - 1)
    return when (playMode) {
        PlayMode.SHUFFLE -> {
            if (queueSize == 1) {
                safeCurrentIndex
            } else {
                shuffleCandidateIndex
                    ?.takeIf { it in 0 until queueSize && it != safeCurrentIndex }
                    ?: (0 until queueSize).firstOrNull { it != safeCurrentIndex }
                    ?: safeCurrentIndex
            }
        }
        else -> {
            val raw = safeCurrentIndex + delta
            val shouldWrap = playMode == PlayMode.LIST_LOOP || playMode == PlayMode.SINGLE_LOOP
            when {
                raw > queueSize - 1 -> if (shouldWrap) 0 else queueSize - 1
                raw < 0 -> if (shouldWrap) queueSize - 1 else 0
                else -> raw
            }
        }
    }
}

internal fun resolvePlaybackEndTargetIndex(
    queueSize: Int,
    currentIndex: Int,
    playMode: PlayMode,
    shuffleCandidateIndex: Int? = null,
): Int? {
    if (queueSize <= 0) return null
    val safeCurrentIndex = currentIndex.coerceIn(0, queueSize - 1)
    return when (playMode) {
        PlayMode.SINGLE_LOOP -> safeCurrentIndex
        PlayMode.SEQUENCE -> (safeCurrentIndex + 1).takeIf { it < queueSize }
        PlayMode.LIST_LOOP,
        PlayMode.SHUFFLE,
        PlayMode.HEART,
        -> (safeCurrentIndex + 1) % queueSize
    }
}

internal data class PlaybackQueuePlan(
    val tracks: List<TrackItem>,
    val startIndex: Int,
)

internal fun resolvePlaybackQueuePlan(
    tracks: List<TrackItem>,
    startIndex: Int,
    playMode: PlayMode,
    shuffledTracks: List<TrackItem>? = null,
    keepRequestedTrackFirst: Boolean = true,
): PlaybackQueuePlan {
    if (tracks.isEmpty()) {
        return PlaybackQueuePlan(emptyList(), 0)
    }
    val safeStartIndex = startIndex.coerceIn(0, tracks.lastIndex)
    if (playMode != PlayMode.SHUFFLE) {
        return PlaybackQueuePlan(tracks, safeStartIndex)
    }
    val requestedShuffle = shuffledTracks ?: tracks.shuffled()
    val originalIds = tracks.map { it.id }.toSet()
    val shuffledUnique = requestedShuffle
        .filter { it.id in originalIds }
        .distinctBy { it.id }
    val shuffledIds = shuffledUnique.map { it.id }.toSet()
    val ordered = shuffledUnique + tracks.filterNot { it.id in shuffledIds }
    if (!keepRequestedTrackFirst) {
        return PlaybackQueuePlan(ordered, 0)
    }
    val requestedTrack = tracks[safeStartIndex]
    return PlaybackQueuePlan(
        tracks = listOf(requestedTrack) + ordered.filterNot { it.id == requestedTrack.id },
        startIndex = 0,
    )
}

internal data class PlayModeQueueTransition(
    val queue: List<TrackItem>,
    val currentIndex: Int,
    val originalQueue: List<TrackItem>?,
)

internal data class PlaylistQueueExtension(
    val queue: List<TrackItem>,
    val currentIndex: Int,
    val originalQueue: List<TrackItem>?,
)

internal fun resolveNextPlayMode(current: PlayMode): PlayMode {
    return when (current) {
        PlayMode.SEQUENCE -> PlayMode.LIST_LOOP
        PlayMode.LIST_LOOP -> PlayMode.SINGLE_LOOP
        PlayMode.SINGLE_LOOP -> PlayMode.SHUFFLE
        PlayMode.SHUFFLE -> PlayMode.HEART
        PlayMode.HEART -> PlayMode.SEQUENCE
    }
}

internal fun resolveNewQueuePlayMode(current: PlayMode): PlayMode {
    return if (current == PlayMode.HEART) PlayMode.LIST_LOOP else current
}

internal fun previewPlayModeState(
    state: PlaybackUiState,
    targetMode: PlayMode,
): PlaybackUiState {
    return state.copy(
        playMode = targetMode,
        errorMessage = null,
    )
}

internal fun resolvePlayModeQueueTransition(
    currentQueue: List<TrackItem>,
    currentIndex: Int,
    currentMode: PlayMode,
    targetMode: PlayMode,
    originalQueue: List<TrackItem>?,
    shuffledTracks: List<TrackItem>? = null,
    heartTracks: List<TrackItem>? = null,
): PlayModeQueueTransition {
    if (currentQueue.isEmpty()) {
        return PlayModeQueueTransition(
            queue = currentQueue,
            currentIndex = 0,
            originalQueue = null,
        )
    }
    val safeCurrentIndex = currentIndex.coerceIn(0, currentQueue.lastIndex)
    val currentTrack = currentQueue[safeCurrentIndex]
    val storedOriginal = originalQueue?.takeIf { it.isNotEmpty() }
    return when (targetMode) {
        PlayMode.SHUFFLE -> {
            val baseQueue = storedOriginal ?: currentQueue
            val baseIndex = baseQueue.indexOfFirst { it.id == currentTrack.id }
                .takeIf { it >= 0 }
                ?: safeCurrentIndex.coerceIn(0, baseQueue.lastIndex)
            val plan = resolvePlaybackQueuePlan(
                tracks = baseQueue,
                startIndex = baseIndex,
                playMode = PlayMode.SHUFFLE,
                shuffledTracks = shuffledTracks,
                keepRequestedTrackFirst = true,
            )
            PlayModeQueueTransition(
                queue = plan.tracks,
                currentIndex = plan.startIndex,
                originalQueue = baseQueue,
            )
        }
        PlayMode.HEART -> {
            val recommendations = heartTracks.orEmpty()
                .filterNot { it.id == currentTrack.id }
                .distinctBy { it.id }
            PlayModeQueueTransition(
                queue = listOf(currentTrack) + recommendations,
                currentIndex = 0,
                originalQueue = storedOriginal ?: currentQueue,
            )
        }
        else -> {
            if ((currentMode == PlayMode.SHUFFLE || currentMode == PlayMode.HEART) && storedOriginal != null) {
                val restoredIndex = storedOriginal.indexOfFirst { it.id == currentTrack.id }
                    .takeIf { it >= 0 }
                    ?: 0
                PlayModeQueueTransition(
                    queue = storedOriginal,
                    currentIndex = restoredIndex,
                    originalQueue = null,
                )
            } else {
                PlayModeQueueTransition(
                    queue = currentQueue,
                    currentIndex = safeCurrentIndex,
                    originalQueue = storedOriginal,
                )
            }
        }
    }
}

internal fun resolvePlaylistQueueExtension(
    activeSource: PlaybackQueueSource,
    requestedPlaylistId: Long,
    currentQueue: List<TrackItem>,
    currentTrack: TrackItem?,
    loadedTracks: List<TrackItem>,
    playMode: PlayMode,
    originalQueue: List<TrackItem>?,
): PlaylistQueueExtension? {
    if (requestedPlaylistId <= 0L || loadedTracks.isEmpty()) return null
    if (activeSource != PlaybackQueueSource.Playlist(requestedPlaylistId)) return null
    val currentTrackId = currentTrack?.id ?: return null
    val mergedOriginal = mergeQueueTracks(
        existing = originalQueue?.takeIf { it.isNotEmpty() } ?: currentQueue,
        incoming = loadedTracks,
    )
    if (mergedOriginal.size <= (originalQueue?.size ?: currentQueue.size)) return null
    if (playMode == PlayMode.SHUFFLE) {
        val extendedQueue = mergeQueueTracks(currentQueue, mergedOriginal)
        val currentIndex = extendedQueue.indexOfFirst { it.id == currentTrackId }.takeIf { it >= 0 } ?: 0
        return PlaylistQueueExtension(
            queue = extendedQueue,
            currentIndex = currentIndex,
            originalQueue = mergedOriginal,
        )
    }
    val currentIndex = mergedOriginal.indexOfFirst { it.id == currentTrackId }.takeIf { it >= 0 } ?: 0
    return PlaylistQueueExtension(
        queue = mergedOriginal,
        currentIndex = currentIndex,
        originalQueue = originalQueue,
    )
}

private fun mergeQueueTracks(
    existing: List<TrackItem>,
    incoming: List<TrackItem>,
): List<TrackItem> {
    if (existing.isEmpty()) return incoming.distinctBy { it.id }
    val existingIds = existing.map { it.id }.toMutableSet()
    return existing + incoming.filter { track -> existingIds.add(track.id) }
}

@Singleton
class PlaybackCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val trackSourceResolver: TrackSourceResolver,
    private val queueRepository: QueueRepository,
    private val appSettingsStore: AppSettingsStore,
) {
    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context)
            .setMediaCodecSelector(
                MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                        .filterNot { codecInfo -> codecInfo.name.startsWith("OMX.ffmpeg.", ignoreCase = true) }
                },
            )
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
    ).build()

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var sessionServiceAttached = false
    private var progressJob: Job? = null
    private var stallJob: Job? = null
    private var lastStablePositionMs = 0L
    private var lastPositionUpdateElapsed = 0L
    private var lastPublishedPositionMs = 0L
    private var lastPublishedDurationMs = 0L
    private var retryCount = 0
    private var playbackEndJob: Job? = null
    private var playerScreenProgressActive = false
    private var lyricScreenProgressActive = false
    private var wordLevelLyricProgressActive = false
    private var originalQueueForMode: List<TrackItem>? = null
    private var activeQueueSource: PlaybackQueueSource = PlaybackQueueSource.None
    private var handleAudioFocus: Boolean? = null
    private val stallTimeoutMs = 9_000L
    private val musicAudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    init {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.value = _uiState.value.copy(
                        isPlaying = isPlaying,
                        errorMessage = null,
                    )
                    if (isPlaying && player.playbackState != Player.STATE_BUFFERING) {
                        startProgressUpdates()
                    } else {
                        stopProgressUpdates()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val isBuffering = playbackState == Player.STATE_BUFFERING
                    _uiState.value = _uiState.value.copy(
                        isBuffering = isBuffering,
                        durationMs = player.duration.takeIf { it > 0 } ?: _uiState.value.durationMs,
                    )
                    if (playbackState == Player.STATE_ENDED) {
                        stopProgressUpdates()
                        stopStallWatchdog()
                        syncProgressSnapshot(force = true)
                        handlePlaybackEnded()
                        return
                    }
                    if (shouldRunStallWatchdog(
                            hasTrack = _uiState.value.currentTrack != null,
                            isBuffering = isBuffering,
                            playWhenReady = player.playWhenReady,
                        )
                    ) {
                        stopProgressUpdates()
                        startStallWatchdog()
                    } else {
                        stopStallWatchdog()
                        syncProgressSnapshot(force = true)
                        if (player.isPlaying) {
                            startProgressUpdates()
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    appScope.launch { recoverCurrentTrack("播放失败: ${error.errorCodeName}") }
                }
            },
        )

        appScope.launch {
            appSettingsStore.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(playMode = PlayMode.fromRaw(settings.playMode))
                val nextHandleAudioFocus = resolveAudioFocusHandling(settings.allowConcurrentPlayback)
                if (handleAudioFocus != nextHandleAudioFocus) {
                    handleAudioFocus = nextHandleAudioFocus
                    withPlayer {
                        setAudioAttributes(musicAudioAttributes, nextHandleAudioFocus)
                    }
                }
            }
        }

        appScope.launch {
            queueRepository.observeQueue().collect { queue ->
                _uiState.value = _uiState.value.copy(queue = queue)
            }
        }

        appScope.launch(Dispatchers.Main.immediate) {
            _uiState.subscriptionCount.collect { count ->
                val shouldRun = shouldRunProgressLoop(
                    hasActiveSubscribers = count > 0,
                    playerScreenActive = playerScreenProgressActive,
                    isPlaying = player.isPlaying,
                    isBuffering = player.playbackState == Player.STATE_BUFFERING,
                )
                if (shouldRun) {
                    syncProgressSnapshot(force = true)
                    startProgressUpdates()
                } else {
                    stopProgressUpdates()
                }
            }
        }
    }

    fun attachSessionService() {
        sessionServiceAttached = true
    }

    fun detachSessionService() {
        sessionServiceAttached = false
    }

    fun ensureServiceRunning() {
        if (!sessionServiceAttached) {
            context.startForegroundService(Intent(context, SPlayerPlaybackService::class.java))
        }
    }

    fun setProgressCadence(
        playerScreenActive: Boolean,
        lyricScreenActive: Boolean,
        wordLevelLyricActive: Boolean,
    ) {
        if (
            playerScreenProgressActive == playerScreenActive &&
            lyricScreenProgressActive == lyricScreenActive &&
            wordLevelLyricProgressActive == wordLevelLyricActive
        ) {
            return
        }
        playerScreenProgressActive = playerScreenActive
        lyricScreenProgressActive = lyricScreenActive
        wordLevelLyricProgressActive = wordLevelLyricActive
        appScope.launch(Dispatchers.Main.immediate) {
            if (shouldRunProgressLoop(
                    hasActiveSubscribers = _uiState.subscriptionCount.value > 0,
                    playerScreenActive = playerScreenProgressActive,
                    isPlaying = player.isPlaying,
                    isBuffering = player.playbackState == Player.STATE_BUFFERING,
                )
            ) {
                stopProgressUpdates()
                syncProgressSnapshot(force = true)
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }
    }

    suspend fun playTracks(
        tracks: List<TrackItem>,
        startIndex: Int = 0,
        keepRequestedTrackFirstInShuffle: Boolean = true,
        queueSource: PlaybackQueueSource = PlaybackQueueSource.None,
        knownLoadedTracks: List<TrackItem>? = null,
    ) {
        if (tracks.isEmpty()) return
        val activePlayMode = resolveNewQueuePlayMode(_uiState.value.playMode)
        val queuePlan = resolvePlaybackQueuePlan(
            tracks = tracks,
            startIndex = startIndex,
            playMode = activePlayMode,
            keepRequestedTrackFirst = keepRequestedTrackFirstInShuffle,
        )
        if (activePlayMode != _uiState.value.playMode) {
            _uiState.value = _uiState.value.copy(playMode = activePlayMode)
            appSettingsStore.setPlayMode(activePlayMode)
        }
        originalQueueForMode = if (activePlayMode == PlayMode.SHUFFLE && queuePlan.tracks != tracks) {
            tracks
        } else {
            null
        }
        activeQueueSource = queueSource
        var resolvedQueue = queuePlan.tracks
        var resolvedStartIndex = queuePlan.startIndex
        val playlistId = (queueSource as? PlaybackQueueSource.Playlist)?.playlistId
        val knownExtension = if (playlistId != null && !knownLoadedTracks.isNullOrEmpty()) {
            withContext(Dispatchers.Default) {
                resolvePlaylistQueueExtension(
                    activeSource = queueSource,
                    requestedPlaylistId = playlistId,
                    currentQueue = resolvedQueue,
                    currentTrack = resolvedQueue[resolvedStartIndex],
                    loadedTracks = knownLoadedTracks,
                    playMode = activePlayMode,
                    originalQueue = originalQueueForMode,
                )
            }
        } else {
            null
        }
        if (knownExtension != null) {
            originalQueueForMode = knownExtension.originalQueue
            resolvedQueue = knownExtension.queue
            resolvedStartIndex = knownExtension.currentIndex
        }
        queueRepository.replaceQueue(resolvedQueue)
        playTrack(resolvedQueue[resolvedStartIndex], resolvedStartIndex)
    }

    suspend fun updatePlaylistQueueIfActive(
        playlistId: Long,
        loadedTracks: List<TrackItem>,
    ) {
        val state = _uiState.value
        val extension = withContext(Dispatchers.Default) {
            resolvePlaylistQueueExtension(
                activeSource = activeQueueSource,
                requestedPlaylistId = playlistId,
                currentQueue = state.queue,
                currentTrack = state.currentTrack,
                loadedTracks = loadedTracks,
                playMode = state.playMode,
                originalQueue = originalQueueForMode,
            )
        } ?: return
        originalQueueForMode = extension.originalQueue
        queueRepository.replaceQueue(extension.queue)
        _uiState.value = _uiState.value.copy(
            queue = extension.queue,
            currentIndex = extension.currentIndex,
            errorMessage = null,
        )
    }

    suspend fun playTrack(track: TrackItem, queueIndex: Int? = null) {
        ensureServiceRunning()
        retryCount = 0
        val resolvedQueueIndex = queueIndex ?: _uiState.value.queue.indexOfFirst { it.id == track.id }
        _uiState.value = seedPendingPlaybackState(
            previousState = _uiState.value,
            track = track,
            queueIndex = resolvedQueueIndex,
        )
        trackSourceResolver.clearFailures(track)
        val source = trackSourceResolver.resolve(track)
        playResolvedTrack(
            track = track,
            source = source,
            queueIndex = resolvedQueueIndex,
            startPlayback = appSettingsStore.settings.value.autoPlay,
        )
    }

    fun togglePlayback() {
        appScope.launch(Dispatchers.Main.immediate) {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        appScope.launch(Dispatchers.Main.immediate) {
            player.seekTo(positionMs)
            lastStablePositionMs = positionMs
            lastPositionUpdateElapsed = SystemClock.elapsedRealtime()
            syncProgressSnapshot(force = true)
        }
    }

    fun skipNext() {
        moveQueue(1)
    }

    fun skipPrevious() {
        moveQueue(-1)
    }

    fun cyclePlayMode() {
        appScope.launch { setPlayMode(resolveNextPlayMode(_uiState.value.playMode)) }
    }

    fun previewPlayMode(targetMode: PlayMode) {
        _uiState.value = previewPlayModeState(_uiState.value, targetMode)
    }

    fun restorePreviewedPlayMode(targetMode: PlayMode) {
        _uiState.value = _uiState.value.copy(playMode = targetMode)
    }

    suspend fun setPlayMode(
        targetMode: PlayMode,
        heartTracks: List<TrackItem>? = null,
    ): Boolean {
        if (targetMode == PlayMode.HEART && heartTracks.isNullOrEmpty()) {
            reportError("心动模式暂无推荐")
            return false
        }
        val currentMode = _uiState.value.playMode
        _uiState.value = previewPlayModeState(_uiState.value, targetMode)
        val state = _uiState.value
        val transition = withContext(Dispatchers.Default) {
            resolvePlayModeQueueTransition(
                currentQueue = state.queue,
                currentIndex = state.currentIndex,
                currentMode = currentMode,
                targetMode = targetMode,
                originalQueue = originalQueueForMode,
                heartTracks = heartTracks,
            )
        }
        originalQueueForMode = transition.originalQueue
        if (transition.queue != state.queue) {
            queueRepository.replaceQueue(transition.queue)
        }
        val resolvedIndex = if (transition.queue.isEmpty()) {
            -1
        } else {
            transition.currentIndex.coerceIn(0, transition.queue.lastIndex)
        }
        _uiState.value = _uiState.value.copy(
            queue = transition.queue,
            currentIndex = resolvedIndex,
            playMode = targetMode,
            errorMessage = null,
        )
        appSettingsStore.setPlayMode(targetMode)
        return true
    }

    fun reportError(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    fun playQueueIndex(index: Int) {
        val queue = _uiState.value.queue
        if (index !in queue.indices) return
        appScope.launch { playTrack(queue[index], index) }
    }

    private fun moveQueue(delta: Int) {
        val queue = _uiState.value.queue
        if (queue.isEmpty()) return
        val current = _uiState.value.currentIndex.coerceAtLeast(0)
        val shuffleCandidateIndex = (queue.indices - current).randomOrNull()
        val nextIndex = resolveQueueMoveTargetIndex(
            queueSize = queue.size,
            currentIndex = current,
            playMode = _uiState.value.playMode,
            delta = delta,
            shuffleCandidateIndex = shuffleCandidateIndex,
        ) ?: return
        appScope.launch { playTrack(queue[nextIndex], nextIndex) }
    }

    private fun handlePlaybackEnded() {
        if (playbackEndJob?.isActive == true) return
        playbackEndJob = appScope.launch {
            val state = _uiState.value
            val queue = state.queue
            val targetIndex = resolvePlaybackEndTargetIndex(
                queueSize = queue.size,
                currentIndex = state.currentIndex,
                playMode = state.playMode,
            ) ?: return@launch
            val targetTrack = queue.getOrNull(targetIndex) ?: return@launch
            val currentTrack = state.currentTrack
            if (targetTrack.id == currentTrack?.id && targetIndex == state.currentIndex) {
                withPlayer {
                    seekTo(0L)
                    playWhenReady = true
                    play()
                }
                lastStablePositionMs = 0L
                lastPositionUpdateElapsed = SystemClock.elapsedRealtime()
                lastPublishedPositionMs = 0L
                _uiState.value = _uiState.value.copy(
                    currentIndex = targetIndex,
                    positionMs = 0L,
                    isBuffering = false,
                    errorMessage = null,
                )
                startProgressUpdates()
                return@launch
            }
            playTrackAfterEnded(targetTrack, targetIndex)
        }.also { job ->
            job.invokeOnCompletion {
                if (playbackEndJob === job) {
                    playbackEndJob = null
                }
            }
        }
    }

    private suspend fun playTrackAfterEnded(track: TrackItem, queueIndex: Int) {
        ensureServiceRunning()
        retryCount = 0
        _uiState.value = seedPendingPlaybackState(
            previousState = _uiState.value,
            track = track,
            queueIndex = queueIndex,
        )
        trackSourceResolver.clearFailures(track)
        val source = trackSourceResolver.resolve(track)
        playResolvedTrack(
            track = track,
            source = source,
            queueIndex = queueIndex,
            startPlayback = true,
        )
    }

    private suspend fun playResolvedTrack(
        track: TrackItem,
        source: TrackSource,
        queueIndex: Int,
        startPlayback: Boolean,
    ) {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.name)
            .setArtist(track.artists)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setDurationMs(track.durationMs)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(source.url)
            .setMediaMetadata(metadata)
            .setTag(track)
            .build()

        withPlayer {
            setMediaItem(mediaItem)
            prepare()
            if (startPlayback) {
                playWhenReady = true
                play()
            }
        }

        queueRepository.addRecent(track)
        lastStablePositionMs = 0L
        lastPositionUpdateElapsed = SystemClock.elapsedRealtime()
        lastPublishedPositionMs = 0L
        lastPublishedDurationMs = track.durationMs
        _uiState.value = _uiState.value.copy(
            currentTrack = track,
            currentIndex = queueIndex.coerceAtLeast(0),
            currentSource = source.source,
            durationMs = track.durationMs,
            positionMs = 0L,
            errorMessage = null,
        )
    }

    private fun startProgressUpdates() {
        if (
            progressJob?.isActive == true ||
            !shouldRunProgressLoop(
                hasActiveSubscribers = _uiState.subscriptionCount.value > 0,
                playerScreenActive = playerScreenProgressActive,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
            )
        ) {
            return
        }
        progressJob = appScope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                delay(
                    resolveProgressLoopIntervalMs(
                        playerScreenActive = playerScreenProgressActive,
                        lyricScreenActive = lyricScreenProgressActive,
                        wordLevelLyricActive = wordLevelLyricProgressActive,
                    ),
                )
                if (
                    !shouldRunProgressLoop(
                        hasActiveSubscribers = _uiState.subscriptionCount.value > 0,
                        playerScreenActive = playerScreenProgressActive,
                        isPlaying = player.isPlaying,
                        isBuffering = player.playbackState == Player.STATE_BUFFERING,
                    )
                ) {
                    break
                }
                syncProgressSnapshot()
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (progressJob === job) {
                    progressJob = null
                }
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun startStallWatchdog() {
        if (stallJob?.isActive == true) return
        if (!shouldRunStallWatchdog(
                hasTrack = _uiState.value.currentTrack != null,
                isBuffering = _uiState.value.isBuffering,
                playWhenReady = player.playWhenReady,
            )
        ) {
            return
        }
        stallJob = appScope.launch {
            while (isActive) {
                delay(1_500)
                if (!shouldRunStallWatchdog(
                        hasTrack = _uiState.value.currentTrack != null,
                        isBuffering = _uiState.value.isBuffering,
                        playWhenReady = withPlayer { playWhenReady },
                    )
                ) {
                    break
                }
                val noProgressDuration = SystemClock.elapsedRealtime() - lastPositionUpdateElapsed
                if (shouldTriggerStallRecovery(_uiState.value.isBuffering, noProgressDuration, stallTimeoutMs)) {
                    recoverCurrentTrack("检测到卡流，正在恢复")
                    break
                }
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (stallJob === job) {
                    stallJob = null
                }
            }
        }
    }

    private fun stopStallWatchdog() {
        stallJob?.cancel()
        stallJob = null
    }

    private suspend fun recoverCurrentTrack(reason: String) {
        val currentTrack = _uiState.value.currentTrack ?: return
        val source = _uiState.value.currentSource
        trackSourceResolver.markFailed(currentTrack, source)
        val resumePosition = withPlayer {
            max(currentPosition, lastStablePositionMs)
        }
        retryCount += 1
        val retryLimit = trackSourceResolver.getRetryLimit(currentTrack)

        if (retryCount > retryLimit) {
            _uiState.value = _uiState.value.copy(errorMessage = "$reason，已跳过")
            moveToPlaybackEndTarget()
            return
        }

        runCatching {
            val nextSource = trackSourceResolver.resolve(currentTrack)
            val queueIndex = _uiState.value.currentIndex.coerceAtLeast(0)
            playResolvedTrack(
                track = currentTrack,
                source = nextSource,
                queueIndex = queueIndex,
                startPlayback = appSettingsStore.settings.value.autoPlay,
            )
            if (resumePosition > 0L) {
                seekTo(resumePosition)
            }
            _uiState.value = _uiState.value.copy(errorMessage = "$reason，已恢复")
        }.onFailure {
            _uiState.value = _uiState.value.copy(errorMessage = "$reason，恢复失败")
            moveToPlaybackEndTarget()
        }
    }

    private fun moveToPlaybackEndTarget() {
        val state = _uiState.value
        val targetIndex = resolvePlaybackEndTargetIndex(
            queueSize = state.queue.size,
            currentIndex = state.currentIndex,
            playMode = state.playMode,
        ) ?: return
        playQueueIndex(targetIndex)
    }

    private suspend fun <T> withPlayer(block: ExoPlayer.() -> T): T {
        return withContext(Dispatchers.Main.immediate) {
            player.block()
        }
    }

    private fun syncProgressSnapshot(force: Boolean = false) {
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it > 0 } ?: _uiState.value.durationMs
        val now = SystemClock.elapsedRealtime()
        if (currentPosition > lastStablePositionMs) {
            lastStablePositionMs = currentPosition
            lastPositionUpdateElapsed = now
        }
        if (!force && !shouldDispatchProgressUpdate(lastPublishedPositionMs, lastPublishedDurationMs, currentPosition, duration)) {
            return
        }
        lastPublishedPositionMs = currentPosition
        lastPublishedDurationMs = duration
        _uiState.value = _uiState.value.copy(
            positionMs = currentPosition,
            durationMs = duration,
        )
    }
}
