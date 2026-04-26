package top.imsyy.splayer.nativeapp.player

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.MediaItem
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
        playerScreenActive && lyricScreenActive -> 900L
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
        PlayMode.SHUFFLE -> {
            if (queueSize == 1) {
                safeCurrentIndex
            } else {
                shuffleCandidateIndex
                    ?.takeIf { it in 0 until queueSize && it != safeCurrentIndex }
                    ?: (0 until queueSize).firstOrNull { it != safeCurrentIndex }
            }
        }
        PlayMode.SEQUENCE,
        PlayMode.LIST_LOOP,
        PlayMode.HEART,
        -> (safeCurrentIndex + 1) % queueSize
    }
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
    private val stallTimeoutMs = 9_000L

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

    suspend fun playTracks(tracks: List<TrackItem>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        queueRepository.replaceQueue(tracks)
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        playTrack(tracks[safeIndex], safeIndex)
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
        val next = when (_uiState.value.playMode) {
            PlayMode.SEQUENCE -> PlayMode.LIST_LOOP
            PlayMode.LIST_LOOP -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.HEART
            PlayMode.HEART -> PlayMode.SEQUENCE
        }
        appScope.launch { appSettingsStore.setPlayMode(next) }
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
        val nextIndex = when (_uiState.value.playMode) {
            PlayMode.SINGLE_LOOP -> current
            PlayMode.SHUFFLE -> (queue.indices - current).randomOrNull() ?: current
            else -> {
                val raw = current + delta
                when {
                    raw > queue.lastIndex -> if (_uiState.value.playMode == PlayMode.LIST_LOOP) 0 else queue.lastIndex
                    raw < 0 -> if (_uiState.value.playMode == PlayMode.LIST_LOOP) queue.lastIndex else 0
                    else -> raw
                }
            }
        }
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
                shuffleCandidateIndex = if (state.playMode == PlayMode.SHUFFLE) {
                    (queue.indices - state.currentIndex).randomOrNull()
                } else {
                    null
                },
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
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(source.url)
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
            moveQueue(1)
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
            moveQueue(1)
        }
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
